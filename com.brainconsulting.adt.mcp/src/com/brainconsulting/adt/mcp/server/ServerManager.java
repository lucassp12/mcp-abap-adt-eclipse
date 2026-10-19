package com.brainconsulting.adt.mcp.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.brainconsulting.adt.mcp.Activator;
import com.brainconsulting.adt.mcp.claude.ClaudeConfig;
import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.EnvironmentStore;
import com.brainconsulting.adt.mcp.model.Names;
import com.brainconsulting.adt.mcp.model.Settings;

/**
 * Sobe e derruba um processo do servidor MCP por ambiente.
 * <p>
 * Cada processo recebe as credenciais por variáveis de ambiente (SAP_URL, SAP_CLIENT,
 * SAP_USERNAME, SAP_PASSWORD...), nunca por argumento de linha de comando, para a senha
 * não aparecer em listas de processos nem no log.
 */
public class ServerManager {

    public interface Listener {
        void stateChanged();
    }

    private static final class Running {
        final Process process;
        final MessageConsoleStream out;
        final String serverName;
        final Path claudeFile;
        final boolean unregisterOnStop;

        Running(Process process, MessageConsoleStream out, String serverName, Path claudeFile, boolean unregisterOnStop) {
            this.process = process;
            this.out = out;
            this.serverName = serverName;
            this.claudeFile = claudeFile;
            this.unregisterOnStop = unregisterOnStop;
        }
    }

    private final EnvironmentStore store;
    private final Map<String, Running> running = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public ServerManager(EnvironmentStore store) {
        this.store = store;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public boolean isRunning(String environmentId) {
        return running.containsKey(environmentId);
    }

    /** Nome com que o servidor aparece no Claude Code, ex.: "sap-arezzo-dev". */
    public String serverName(Environment env) {
        return store.getSettings().getServerPrefix() + Names.slug(env.getName());
    }

    public String endpointUrl(Environment env) {
        String path = store.getSettings().getEndpointPath();
        if (path == null || path.isBlank()) {
            path = "/mcp";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://localhost:" + env.getPort() + path;
    }

    // ------------------------------------------------------------------ start

    public synchronized void start(Environment env) throws McpException {
        if (isRunning(env.getId())) {
            return;
        }
        Settings s = store.getSettings();

        String password = store.getPassword(env.getId());
        if (password == null || password.isEmpty()) {
            throw new McpException("Não há senha cadastrada para \"" + env.getName()
                    + "\". Edite o ambiente e informe a senha.");
        }
        String exe = s.getCommandPath() == null ? "" : s.getCommandPath().trim();
        if (exe.isEmpty()) {
            throw new McpException("O caminho do servidor MCP não está configurado. Ajuste em Configurações.");
        }
        try {
            Path exePath = Paths.get(exe);
            if (exePath.isAbsolute() && !Files.isRegularFile(exePath)) {
                throw new McpException("Executável não encontrado:\n" + exe
                        + "\n\nConfira com \"where mcp-abap-adt\" no prompt e ajuste em Configurações.");
            }
        } catch (InvalidPathException e) {
            throw new McpException("Caminho do servidor MCP inválido: " + exe);
        }
        if (!isPortFree(env.getPort())) {
            throw new McpException("A porta " + env.getPort() + " já está em uso neste computador.\n"
                    + "Escolha outra porta local para este ambiente.");
        }

        List<String> command = CommandLine.build(s, env);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workDir());
        Map<String, String> vars = pb.environment();
        vars.put("SAP_URL", env.getUrl());
        vars.put("SAP_CLIENT", env.getClient());
        vars.put("SAP_USERNAME", env.getUser());
        vars.put("SAP_PASSWORD", password);
        vars.put("SAP_AUTH_TYPE", "basic");
        if (env.getLanguage() != null && !env.getLanguage().isBlank()) {
            vars.put("SAP_LANGUAGE", env.getLanguage());
        }
        if (env.isIgnoreTls()) {
            vars.put("TLS_REJECT_UNAUTHORIZED", "0");
            vars.put("NODE_TLS_REJECT_UNAUTHORIZED", "0");
        }

        MessageConsole console = console(env);
        MessageConsoleStream out = console.newMessageStream();
        out.println("------------------------------------------------------------");
        out.println("[" + now() + "] Iniciando \"" + env.getName() + "\"");
        out.println("SAP: " + env.getUrl() + "  |  mandante " + env.getClient() + "  |  usuário " + env.getUser());
        out.println("Comando: " + String.join(" ", command));

        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            out.println("ERRO ao iniciar o processo: " + e.getMessage());
            throw new McpException("Não foi possível iniciar o servidor MCP:\n" + e.getMessage(), e);
        }

        boolean register = s.isRegisterInClaude();
        Path claudeFile = Paths.get(s.getClaudeConfigPath());
        Running r = new Running(p, out, serverName(env), claudeFile, register && s.isRemoveOnStop());
        running.put(env.getId(), r);
        pump(p, out, password, env.getName());
        // Registrado depois do put: se o processo já tiver morrido, o handler roda na hora e limpa o estado.
        p.onExit().thenAccept(proc -> onProcessExit(env.getId(), r, proc));

        if (register) {
            String url = endpointUrl(env);
            try {
                ClaudeConfig.register(claudeFile, r.serverName, url);
                out.println("Registrado no Claude Code como \"" + r.serverName + "\" -> " + url);
                out.println("Abra uma nova sessão do Claude Code (ou use /mcp) para ele enxergar este servidor.");
            } catch (IOException e) {
                out.println("AVISO: não foi possível atualizar " + claudeFile + ": " + e.getMessage());
            }
        }
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console);
        fire();
    }

    // ------------------------------------------------------------------- stop

    public synchronized void stop(String environmentId) {
        Running r = running.remove(environmentId);
        if (r == null) {
            return;
        }
        killTree(r.process);
        safePrint(r.out, "[" + now() + "] Servidor parado.");
        cleanup(r);
        fire();
    }

    public void stopAllQuietly() {
        for (String id : new ArrayList<>(running.keySet())) {
            try {
                stop(id);
            } catch (Throwable t) {
                // encerramento: segue para o próximo
            }
        }
    }

    private void onProcessExit(String id, Running r, Process proc) {
        if (!running.remove(id, r)) {
            return; // já foi parado pelo usuário
        }
        int code;
        try {
            code = proc.exitValue();
        } catch (Exception e) {
            code = -1;
        }
        safePrint(r.out, "[" + now() + "] O processo terminou sozinho (código " + code
                + "). Veja as mensagens acima para entender o motivo.");
        cleanup(r);
        fire();
    }

    private void cleanup(Running r) {
        if (!r.unregisterOnStop) {
            return;
        }
        try {
            ClaudeConfig.unregister(r.claudeFile, r.serverName);
            safePrint(r.out, "Removido do Claude Code: " + r.serverName);
        } catch (IOException e) {
            safePrint(r.out, "AVISO: não foi possível atualizar " + r.claudeFile + ": " + e.getMessage());
        }
    }

    /**
     * No Windows o processo iniciado é o cmd.exe, e o node.exe é filho dele. Matar só o cmd
     * deixaria o node órfão segurando a porta; por isso a árvore inteira é encerrada.
     */
    private static void killTree(Process p) {
        ProcessHandle h = p.toHandle();
        List<ProcessHandle> children = h.descendants().collect(Collectors.toList());
        children.forEach(ProcessHandle::destroy);
        h.destroy();
        try {
            p.waitFor(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        children.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (h.isAlive()) {
            h.destroyForcibly();
        }
    }

    // ---------------------------------------------------------------- helpers

    private void pump(Process p, MessageConsoleStream out, String secret, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (secret != null && !secret.isEmpty()) {
                        line = line.replace(secret, "****");
                    }
                    safePrint(out, line);
                }
            } catch (IOException e) {
                // stream fechado ao parar o processo
            }
        }, "mcp-abap-adt-output-" + name);
        t.setDaemon(true);
        t.start();
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setReuseAddress(false);
            ss.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Pasta vazia do plugin, para o servidor não pegar por engano um .env de outra pasta. */
    private static File workDir() {
        Activator a = Activator.getDefault();
        if (a != null) {
            File dir = a.getStateLocation().toFile();
            dir.mkdirs();
            return dir;
        }
        return new File(System.getProperty("user.home"));
    }

    private static MessageConsole console(Environment env) {
        IConsoleManager mgr = ConsolePlugin.getDefault().getConsoleManager();
        String title = "MCP ABAP – " + env.getName();
        for (IConsole c : mgr.getConsoles()) {
            if (c instanceof MessageConsole && title.equals(c.getName())) {
                return (MessageConsole) c;
            }
        }
        MessageConsole mc = new MessageConsole(title, null);
        mgr.addConsoles(new IConsole[] { mc });
        return mc;
    }

    private static void safePrint(MessageConsoleStream out, String text) {
        try {
            out.println(text);
        } catch (Throwable t) {
            // console indisponível (Eclipse encerrando)
        }
    }

    private static String now() {
        return LocalTime.now().withNano(0).toString();
    }

    private void fire() {
        for (Listener l : listeners) {
            try {
                l.stateChanged();
            } catch (Throwable t) {
                Activator.log("Erro ao notificar a view", t);
            }
        }
    }
}
