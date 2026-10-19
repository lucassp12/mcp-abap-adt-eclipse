package com.brainconsulting.adt.mcp.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.equinox.security.storage.ISecurePreferences;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.equinox.security.storage.StorageException;

import com.brainconsulting.adt.mcp.Activator;
import com.brainconsulting.adt.mcp.json.AtomicFile;
import com.brainconsulting.adt.mcp.json.MiniJson;

/**
 * Guarda os ambientes e as configurações em ~/.brain-mcp-abap/environments.json.
 * <p>
 * O arquivo fica no perfil do usuário (e não na instalação do Eclipse ou no workspace),
 * então sobrevive a reinstalações do Eclipse e pode ser compartilhado com o time: ele
 * não contém senhas. As senhas ficam no Secure Storage do Eclipse, criptografadas.
 */
public class EnvironmentStore {

    private static final String SECURE_ROOT = "com.brainconsulting.adt.mcp";

    private final Path file = Paths.get(System.getProperty("user.home"), ".brain-mcp-abap", "environments.json");
    private final List<Environment> environments = new ArrayList<>();
    private Settings settings = new Settings();

    public Path getFile() {
        return file;
    }

    public synchronized void load() {
        environments.clear();
        settings = new Settings();
        if (!Files.exists(file)) {
            return;
        }
        try {
            Object root = MiniJson.parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (root instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) root;
                Object st = m.get("settings");
                if (st instanceof Map) {
                    settings = Settings.fromJson((Map<?, ?>) st);
                }
                if (MiniJson.asInt(m.get("version"), 1) < 2) {
                    settings.migrateFromV1();
                }
                Object envs = m.get("environments");
                if (envs instanceof List) {
                    for (Object o : (List<?>) envs) {
                        if (o instanceof Map) {
                            environments.add(Environment.fromJson((Map<?, ?>) o));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Activator.log("Falha ao ler " + file + "; uma cópia foi preservada antes de qualquer gravação.", e);
            try {
                Files.copy(file, file.resolveSibling("environments.json.corrupt-" + System.currentTimeMillis()));
            } catch (IOException ignore) {
                // melhor esforço
            }
        }
    }

    public synchronized void save() throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 2);
        root.put("settings", settings.toJson());
        List<Object> list = new ArrayList<>();
        for (Environment e : environments) {
            list.add(e.toJson());
        }
        root.put("environments", list);
        AtomicFile.write(file, MiniJson.stringify(root) + "\n");
    }

    public synchronized List<Environment> getEnvironments() {
        return new ArrayList<>(environments);
    }

    public synchronized Environment find(String id) {
        for (Environment e : environments) {
            if (e.getId().equals(id)) {
                return e;
            }
        }
        return null;
    }

    public synchronized void addOrUpdate(Environment env) {
        for (int i = 0; i < environments.size(); i++) {
            if (environments.get(i).getId().equals(env.getId())) {
                environments.set(i, env);
                return;
            }
        }
        environments.add(env);
    }

    public synchronized void remove(String id) {
        environments.removeIf(e -> e.getId().equals(id));
        ISecurePreferences root = SecurePreferencesFactory.getDefault();
        if (root != null && root.nodeExists(SECURE_ROOT + "/" + id)) {
            root.node(SECURE_ROOT + "/" + id).removeNode();
            try {
                root.flush();
            } catch (IOException e) {
                Activator.log("Falha ao remover a senha do Secure Storage", e);
            }
        }
    }

    public synchronized Settings getSettings() {
        return settings;
    }

    public synchronized void setSettings(Settings settings) {
        this.settings = settings;
    }

    // ------------------------------------------------------------- segredos

    /** Retorna o segredo descriptografado (ex.: "password", "certPassphrase"), ou null se não houver. */
    public String getSecret(String id, String key) {
        ISecurePreferences node = existingNode(id);
        if (node == null) {
            return null;
        }
        try {
            return node.get(key, null);
        } catch (StorageException e) {
            Activator.log("Não foi possível ler um segredo do Secure Storage", e);
            return null;
        }
    }

    /** Verifica se o segredo existe sem precisar descriptografá-lo. */
    public boolean hasSecret(String id, String key) {
        ISecurePreferences node = existingNode(id);
        return node != null && Arrays.asList(node.keys()).contains(key);
    }

    public void setSecret(String id, String key, String value) throws IOException {
        ISecurePreferences root = SecurePreferencesFactory.getDefault();
        if (root == null) {
            throw new IOException("O Secure Storage do Eclipse não está disponível.");
        }
        ISecurePreferences node = root.node(SECURE_ROOT + "/" + id);
        try {
            node.put(key, value, true);
            node.flush();
        } catch (StorageException e) {
            throw new IOException("Não foi possível gravar no Secure Storage: " + e.getMessage(), e);
        }
    }

    /** Todos os segredos de um ambiente, para montar o processo ou o teste de conexão. */
    public Map<String, String> getSecrets(String id) {
        Map<String, String> m = new LinkedHashMap<>();
        ISecurePreferences node = existingNode(id);
        if (node == null) {
            return m;
        }
        for (String key : node.keys()) {
            String v = getSecret(id, key);
            if (v != null) {
                m.put(key, v);
            }
        }
        return m;
    }

    private ISecurePreferences existingNode(String id) {
        ISecurePreferences root = SecurePreferencesFactory.getDefault();
        if (root == null || !root.nodeExists(SECURE_ROOT + "/" + id)) {
            return null;
        }
        return root.node(SECURE_ROOT + "/" + id);
    }
}
