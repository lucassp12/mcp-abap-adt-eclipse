package com.brainconsulting.adt.mcp.server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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

    /** Confere se o comando claude está no PATH. */
    public static boolean isClaudeInstalled() {
        List<String> cmd = isWindows()
                ? List.of("cmd.exe", "/c", "where", "claude")
                : List.of("sh", "-c", "command -v claude");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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

    /** Argumentos do claude como lista (respeitando aspas). */
    public static List<String> claudeCommand(String extraArgs) {
        List<String> c = new ArrayList<>();
        c.add("claude");
        c.addAll(CommandLine.tokenize(extraArgs == null ? "" : extraArgs));
        return c;
    }

    /** Comando para abrir uma janela externa já rodando o Claude Code na pasta indicada. */
    public static List<String> externalCommand(Path dir, String title, String extraArgs, Path windowsTerminal) {
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
            cmd.addAll(claudeCommand(extraArgs));
            return cmd;
        }
        String line = "cd '" + dir.toString().replace("'", "'\\''") + "' && "
                + String.join(" ", claudeCommand(extraArgs)) + "; exec $SHELL";
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

    public static void launchExternal(Path dir, String title, String extraArgs) throws IOException {
        Path wt = isWindows() ? findWindowsTerminal() : null;
        new ProcessBuilder(externalCommand(dir, title, extraArgs, wt))
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
    }

    /** Programa e argumentos para o terminal interno do Eclipse. */
    public static String[] internalProcess(String extraArgs) {
        if (isWindows()) {
            String sys = System.getenv("SystemRoot");
            String cmdExe = sys != null ? Paths.get(sys, "System32", "cmd.exe").toString() : "cmd.exe";
            return new String[] { cmdExe, "/k " + String.join(" ", claudeCommand(extraArgs)) };
        }
        return new String[] { "/bin/bash", "-lc \"" + String.join(" ", claudeCommand(extraArgs)) + "; exec bash\"" };
    }
}
