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
import java.util.Collection;
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
import com.brainconsulting.adt.mcp.model.AuthMethod;
import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.EnvironmentStore;
import com.brainconsulting.adt.mcp.model.Names;
import com.brainconsulting.adt.mcp.model.ServiceKeys;
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
        final Path generatedEnvFile;

        Running(Process process, MessageConsoleStream out, String serverName, Path claudeFile,
                boolean unregisterOnStop, Path generatedEnvFile) {
            this.process = process;
            this.out = out;
            this.serverName = serverName;
            this.claudeFile = claudeFile;
            this.unregisterOnStop = unregisterOnStop;
            this.generatedEnvFile = generatedEnvFile;
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
        Map<String, String> secrets = store.getSecrets(env.getId());

        preflight(env, s, secrets);

        RuntimeInfo runtime = null;
        if (s.isEmbeddedServer()) {
            runtime = EmbeddedRuntime.find();
            if (runtime == null) {
                throw new McpException("O servidor embutido não está disponível nesta instalação "
                        + "(ele é distribuído só para Windows x64).\n\n"
                        + "Em Configurações, escolha \"Executável instalado na máquina\" e informe o mcp-abap-adt instalado via npm.");
            }
        } else {
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
        }
        if (!isPortFree(env.getPort())) {
            throw new McpException("A porta " + env.getPort() + " já está em uso neste computador.\n"
                    + "Escolha outra porta local para este ambiente.");
        }

        Map<String, String> vars = CommandLine.variables(env, secrets);
        Path envFile = null;
        if (CommandLine.needsGeneratedEnvFile(env)) {
            envFile = runDir().resolve(env.getId() + ".env");
            try {
                PrivateFiles.write(envFile, CommandLine.envFileContent(vars));
            } catch (IOException e) {
                throw new McpException("Não foi possível gerar o arquivo de conexão temporário:\n" + e.getMessage(), e);
            }
        }

        List<String> command = CommandLine.build(s, env, envFile, runtime);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        pb.directory(workDir());
        pb.environment().putAll(vars);

        MessageConsole console = console(env);
        MessageConsoleStream out = console.newMessageStream();
        out.println("------------------------------------------------------------");
        out.println("[" + now() + "] Iniciando \"" + env.getName() + "\"");
        out.println("Conexão: " + env.getAuthMethod().label);
        out.println("Alvo: " + env.targetLabel());
        out.println("Tipo de sistema: " + env.getSystemType().label);
        out.println("Ferramentas: " + (env.exposition().isEmpty() ? "(padrão do servidor)" : env.exposition()));
        out.println("Servidor: " + (runtime != null ? "embutido – " + runtime.describe()
                : "externo – " + s.getCommandPath()));
        out.println("Comando: " + String.join(" ", command));
        if (env.getAuthMethod() == AuthMethod.SERVICE_KEY) {
            out.println("Na primeira conexão o servidor abre o navegador para o login no BTP (porta de retorno "
                    + env.getBrowserAuthPort() + ").");
        }

        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            out.println("ERRO ao iniciar o processo: " + e.getMessage());
            deleteQuietly(envFile);
            throw new McpException("Não foi possível iniciar o servidor MCP:\n" + e.getMessage(), e);
        }

        boolean register = s.isRegisterInClaude();
        Path claudeFile = Paths.get(s.getClaudeConfigPath());
        Running r = new Running(p, out, serverName(env), claudeFile, register && s.isRemoveOnStop(), envFile);
        running.put(env.getId(), r);
        pump(p, out, secrets.values(), env.getName());
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

    /** Verifica o que dá para verificar antes de subir o processo, com mensagens acionáveis. */
    private void preflight(Environment env, Settings s, Map<String, String> secrets) throws McpException {
        switch (env.getAuthMethod()) {
            case BASIC:
            case RFC:
                if (isBlank(secrets.get(CommandLine.SECRET_PASSWORD))) {
                    throw new McpException("Não há senha cadastrada para \"" + env.getName()
                            + "\". Edite o ambiente e informe a senha.");
                }
                break;
            case CERTIFICATE:
                if (Environment.CERT_PFX.equals(env.getCertFormat())) {
                    requireFile(env.getPfxPath(), "Arquivo .pfx do certificado");
                } else {
                    requireFile(env.getCertPath(), "Certificado (.crt/.pem)");
                    requireFile(env.getCertKeyPath(), "Chave privada (.key)");
                }
                break;
            case SERVICE_KEY: {
                Path key = ServiceKeys.file(s.getServiceKeysDir(), env.getDestination());
                if (!Files.isRegularFile(key)) {
                    throw new McpException("Service key não encontrada em:\n" + key
                            + "\n\nEdite o ambiente e use \"Importar service key...\".");
                }
                if (env.getBrowserAuthPort() > 0 && !isPortFree(env.getBrowserAuthPort())) {
                    throw new McpException("A porta de retorno do login (" + env.getBrowserAuthPort()
                            + ") já está em uso. Escolha outra no cadastro do ambiente.");
                }
                break;
            }
            case ENV_FILE:
                requireFile(env.getEnvFilePath(), "Arquivo .env");
                break;
            default:
                break;
        }
    }

    private static void requireFile(String path, String what) throws McpException {
        if (isBlank(path) || !Files.isRegularFile(Paths.get(path))) {
            throw new McpException(what + " não encontrado: " + (isBlank(path) ? "(vazio)" : path));
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
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
        deleteQuietly(r.generatedEnvFile);
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

    private void pump(Process p, MessageConsoleStream out, Collection<String> secrets, String name) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    for (String secret : secrets) {
                        if (secret != null && secret.length() >= 3) {
                            line = line.replace(secret, "****");
                        }
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

    /** Onde ficam os .env temporários (perfil do usuário, permissão só do dono). */
    public static Path runDir() {
        return Paths.get(System.getProperty("user.home"), ".brain-mcp-abap", "run");
    }

    /** Apaga .env temporários que tenham sobrado de uma sessão encerrada de forma abrupta. */
    public static void cleanRunDir() {
        Path dir = runDir();
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".env")).forEach(ServerManager::deleteQuietly);
        } catch (IOException e) {
            // melhor esforço
        }
    }

    private static void deleteQuietly(Path f) {
        if (f == null) {
            return;
        }
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            // melhor esforço
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
