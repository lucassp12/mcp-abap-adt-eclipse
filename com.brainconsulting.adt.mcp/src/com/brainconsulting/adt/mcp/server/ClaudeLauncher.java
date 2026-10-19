package com.brainconsulting.adt.mcp.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Abre o Claude Code numa janela de terminal do sistema.
 * <p>
 * No Windows, o comando roda via {@code cmd.exe /k claude}: o cmd encontra tanto o
 * {@code claude.exe} (instalador nativo) quanto o {@code claude.cmd} (npm) e não esbarra na
 * política de execução de scripts do PowerShell.
 */
public final class ClaudeLauncher {

    public static final String INSTALL_URL = "https://docs.claude.com/en/docs/claude-code/overview";

    private ClaudeLauncher() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** Pasta de trabalho do ambiente, ou a pasta do usuário se não houver uma válida. */
    public static Path workDir(String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                Path p = Paths.get(configured.trim());
                if (Files.isDirectory(p)) {
                    return p;
                }
            } catch (RuntimeException e) {
                // usa a pasta do usuário
            }
        }
        return Paths.get(System.getProperty("user.home"));
    }

    /**
     * Localiza o executável do Claude Code. Procura, nesta ordem: o caminho configurado, o PATH do
     * Eclipse, o PATH atual do Windows (lido do registro: pega instalações feitas depois que o
     * Eclipse ou o login começaram) e as pastas padrão dos instaladores.
     *
     * @return caminho absoluto, ou null se não encontrar
     */
    public static Path resolveClaude(String configured) {
        if (configured != null && !configured.isBlank()) {
            try {
                Path p = Paths.get(configured.trim());
                if (Files.isRegularFile(p)) {
                    return p;
                }
            } catch (RuntimeException e) {
                // segue procurando
            }
        }
        for (String dir : searchDirs()) {
            for (String name : executableNames()) {
                try {
                    Path p = Paths.get(dir.trim(), name);
                    if (Files.isRegularFile(p)) {
                        return p;
                    }
                } catch (RuntimeException e) {
                    // entrada de PATH inválida
                }
            }
        }
        return null;
    }

    /** Pastas pesquisadas por {@link #resolveClaude(String)}, para mostrar na mensagem de erro. */
    public static List<String> searchDirs() {
        LinkedHashSet<String> dirs = new LinkedHashSet<>();
        addPath(dirs, System.getenv("PATH"));
        String home = System.getProperty("user.home");
        if (isWindows()) {
            addPath(dirs, freshWindowsPath());
            dirs.add(Paths.get(home, ".local", "bin").toString());
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                dirs.add(Paths.get(appData, "npm").toString());
            }
        } else {
            dirs.add(Paths.get(home, ".local", "bin").toString());
            dirs.add(Paths.get(home, ".claude", "local").toString());
            dirs.add("/usr/local/bin");
            dirs.add("/opt/homebrew/bin");
        }
        return new ArrayList<>(dirs);
    }

    private static List<String> executableNames() {
        return isWindows() ? List.of("claude.exe", "claude.cmd", "claude.bat") : List.of("claude");
    }

    private static void addPath(LinkedHashSet<String> dirs, String path) {
        if (path == null) {
            return;
        }
        for (String d : path.split(File.pathSeparator)) {
            if (!d.isBlank()) {
                dirs.add(d.trim());
            }
        }
    }

    /**
     * PATH atual do Windows (sistema + usuário), lido do registro com as variáveis expandidas.
     * Diferente de System.getenv("PATH"), reflete instalações feitas depois que o Eclipse abriu.
     */
    public static String freshWindowsPath() {
        String machine = readRegistryValue("HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment", "Path");
        String user = readRegistryValue("HKCU\\Environment", "Path");
        StringBuilder sb = new StringBuilder();
        if (machine != null) {
            sb.append(expandWindowsVars(machine));
        }
        if (user != null) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(expandWindowsVars(user));
        }
        return sb.toString();
    }

    private static String readRegistryValue(String key, String name) {
        try {
            Process p = new ProcessBuilder("reg", "query", key, "/v", name).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset());
            if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) {
                return null;
            }
            return parseRegQuery(out, name);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Extrai o valor de uma linha "    Path    REG_EXPAND_SZ    C:\\...;..." da saída do reg query. */
    public static String parseRegQuery(String output, String name) {
        for (String line : output.split("\\r?\\n")) {
            Matcher m = Pattern.compile("^\\s*" + Pattern.quote(name) + "\\s+REG_(?:EXPAND_)?SZ\\s+(.*)$",
                    Pattern.CASE_INSENSITIVE).matcher(line);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    /** Expande %VARIAVEL% com o ambiente atual (sem diferenciar maiúsculas, como o Windows). */
    public static String expandWindowsVars(String value) {
        Map<String, String> env = new HashMap<>();
        System.getenv().forEach((k, v) -> env.put(k.toUpperCase(Locale.ROOT), v));
        Matcher m = Pattern.compile("%([^%;]+)%").matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = env.get(m.group(1).toUpperCase(Locale.ROOT));
            m.appendReplacement(sb, Matcher.quoteReplacement(v != null ? v : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Espera o servidor MCP aceitar conexões na porta local (ou o tempo acabar). */
    public static boolean waitForPort(int port, long timeoutMillis) {
        long end = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < end) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 500);
                return true;
            } catch (IOException e) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    /** Caminho do Windows Terminal, se instalado. */
    public static Path findWindowsTerminal() {
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            Path wt = Paths.get(local, "Microsoft", "WindowsApps", "wt.exe");
            if (Files.exists(wt)) {
                return wt;
            }
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                try {
                    Path wt = Paths.get(dir.trim(), "wt.exe");
                    if (Files.exists(wt)) {
                        return wt;
                    }
                } catch (RuntimeException e) {
                    // entrada de PATH inválida
                }
            }
        }
        return null;
    }

    /** Executável + argumentos do claude como lista (respeitando aspas). */
    public static List<String> claudeCommand(Path claude, String extraArgs) {
        List<String> c = new ArrayList<>();
        c.add(claude == null ? "claude" : claude.toString());
        c.addAll(CommandLine.tokenize(extraArgs == null ? "" : extraArgs));
        return c;
    }

    /** Comando para abrir uma janela externa já rodando o Claude Code na pasta indicada. */
    public static List<String> externalCommand(Path dir, String title, Path claude, String extraArgs,
            Path windowsTerminal) {
        List<String> cmd = new ArrayList<>();
        if (isWindows()) {
            if (windowsTerminal != null) {
                cmd.add(windowsTerminal.toString());
                cmd.add("-d");
                cmd.add(dir.toString());
                cmd.add("--title");
                cmd.add(title);
            } else {
                cmd.add("cmd.exe");
                cmd.add("/c");
                cmd.add("start");
                cmd.add(title);
                cmd.add("/D");
                cmd.add(dir.toString());
            }
            cmd.add("cmd.exe");
            cmd.add("/k");
            cmd.addAll(claudeCommand(claude, extraArgs));
            return cmd;
        }
        String line = "cd '" + dir.toString().replace("'", "'\\''") + "' && "
                + String.join(" ", quoteAll(claudeCommand(claude, extraArgs))) + "; exec $SHELL";
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            String osa = "tell application \"Terminal\" to do script \""
                    + line.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            cmd.add("osascript");
            cmd.add("-e");
            cmd.add(osa);
            cmd.add("-e");
            cmd.add("tell application \"Terminal\" to activate");
        } else {
            cmd.add("x-terminal-emulator");
            cmd.add("-e");
            cmd.add("bash");
            cmd.add("-lc");
            cmd.add(line);
        }
        return cmd;
    }

    public static void launchExternal(Path dir, String title, Path claude, String extraArgs) throws IOException {
        Path wt = isWindows() ? findWindowsTerminal() : null;
        ProcessBuilder pb = new ProcessBuilder(externalCommand(dir, title, claude, extraArgs, wt))
                .directory(dir.toFile())
                .redirectErrorStream(true);
        if (isWindows()) {
            // Entrega ao terminal o PATH atual do Windows, não o PATH antigo herdado pelo Eclipse.
            String fresh = freshWindowsPath();
            if (!fresh.isBlank()) {
                String current = System.getenv("PATH");
                pb.environment().put("PATH", current == null ? fresh : fresh + ";" + current);
            }
        }
        pb.start();
    }

    /** Programa e argumentos para o terminal interno do Eclipse. */
    public static String[] internalProcess(Path claude, String extraArgs) {
        String line = String.join(" ", quoteAll(claudeCommand(claude, extraArgs)));
        if (isWindows()) {
            String sys = System.getenv("SystemRoot");
            String cmdExe = sys != null ? Paths.get(sys, "System32", "cmd.exe").toString() : "cmd.exe";
            return new String[] { cmdExe, "/k " + line };
        }
        return new String[] { "/bin/bash", "-lc \"" + line.replace("\"", "\\\"") + "; exec bash\"" };
    }

    /** Coloca aspas nos itens que têm espaço (para montar uma linha de comando única). */
    private static List<String> quoteAll(List<String> parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            out.add(p.contains(" ") && !p.startsWith("\"") ? "\"" + p + "\"" : p);
        }
        return out;
    }
}
