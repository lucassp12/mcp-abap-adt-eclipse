package com.brainconsulting.adt.mcp.claude;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.brainconsulting.adt.mcp.json.AtomicFile;
import com.brainconsulting.adt.mcp.json.MiniJson;

/**
 * Registra/remove servidores na chave "mcpServers" do ~/.claude.json (escopo de usuário
 * do Claude Code). O restante do arquivo é preservado; na primeira alteração da sessão
 * é feita uma cópia de segurança (.claude.json.brain-mcp.bak).
 */
public final class ClaudeConfig {

    private static final Set<Path> BACKED_UP = new HashSet<>();

    private ClaudeConfig() {
    }

    public static synchronized void register(Path file, String serverName, String url) throws IOException {
        Map<String, Object> root = read(file);
        Map<String, Object> servers = childObject(root, "mcpServers");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "http");
        entry.put("url", url);
        servers.put(serverName, entry);
        write(file, root);
    }

    public static synchronized void unregister(Path file, String serverName) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        Map<String, Object> root = read(file);
        Object servers = root.get("mcpServers");
        if (servers instanceof Map && ((Map<?, ?>) servers).remove(serverName) != null) {
            write(file, root);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childObject(Map<String, Object> root, String key) {
        Object o = root.get(key);
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        root.put(key, m);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> read(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return new LinkedHashMap<>();
        }
        Object parsed;
        try {
            parsed = MiniJson.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IOException(file + " não é um JSON válido (" + e.getMessage() + "). Nada foi alterado.", e);
        }
        if (!(parsed instanceof Map)) {
            throw new IOException(file + " não contém um objeto JSON. Nada foi alterado.");
        }
        return (Map<String, Object>) parsed;
    }

    private static void write(Path file, Map<String, Object> root) throws IOException {
        Path abs = file.toAbsolutePath();
        if (Files.exists(abs) && BACKED_UP.add(abs)) {
            Files.copy(abs, abs.resolveSibling(abs.getFileName() + ".brain-mcp.bak"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        AtomicFile.write(abs, MiniJson.stringify(root) + "\n");
    }
}
