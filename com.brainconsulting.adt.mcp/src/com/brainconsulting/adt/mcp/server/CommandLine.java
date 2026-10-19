package com.brainconsulting.adt.mcp.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.Names;
import com.brainconsulting.adt.mcp.model.Settings;

/** Monta a linha de comando do servidor MCP para um ambiente. */
public final class CommandLine {

    private CommandLine() {
    }

    public static List<String> build(Settings s, Environment env) {
        List<String> cmd = new ArrayList<>();
        String exe = s.getCommandPath() == null ? "" : s.getCommandPath().trim();
        String lower = exe.toLowerCase(Locale.ROOT);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (windows && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            // .cmd/.bat (como os gerados pelo npm) precisam passar pelo cmd.exe
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add(exe);
        String args = s.getArgsTemplate() == null ? "" : s.getArgsTemplate()
                .replace("${port}", String.valueOf(env.getPort()))
                .replace("${name}", Names.slug(env.getName()));
        cmd.addAll(tokenize(args));
        return cmd;
    }

    /** Separa por espaços, respeitando trechos entre aspas duplas. */
    public static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean hasToken = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                hasToken = true;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (hasToken) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    hasToken = false;
                }
            } else {
                cur.append(c);
                hasToken = true;
            }
        }
        if (hasToken) {
            out.add(cur.toString());
        }
        return out;
    }
}
