package com.brainconsulting.adt.mcp.server;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.brainconsulting.adt.mcp.model.AuthMethod;
import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.Names;
import com.brainconsulting.adt.mcp.model.Settings;
import com.brainconsulting.adt.mcp.model.SystemType;

/**
 * Traduz um ambiente em linha de comando + variáveis do mcp-abap-adt (fr0ster).
 * Classe pura (sem Eclipse), testável isoladamente.
 */
public final class CommandLine {

    public static final String SECRET_PASSWORD = "password";
    public static final String SECRET_CERT_PASSPHRASE = "certPassphrase";

    private CommandLine() {
    }

    /** Variáveis SAP_* e afins para o processo e para o .env gerado. */
    public static Map<String, String> variables(Environment env, Map<String, String> secrets) {
        Map<String, String> v = new LinkedHashMap<>();
        AuthMethod a = env.getAuthMethod();
        if (a.usesSapUrl()) {
            v.put("SAP_URL", env.getUrl());
            putIfSet(v, "SAP_CLIENT", env.getClient());
            putIfSet(v, "SAP_LANGUAGE", env.getLanguage());
        }
        switch (a) {
            case BASIC:
            case RFC:
                v.put("SAP_AUTH_TYPE", "basic");
                v.put("SAP_USERNAME", env.getUser());
                putIfSet(v, "SAP_PASSWORD", secrets.get(SECRET_PASSWORD));
                if (a == AuthMethod.RFC) {
                    v.put("SAP_CONNECTION_TYPE", "rfc");
                }
                break;
            case CERTIFICATE:
                v.put("SAP_AUTH_TYPE", "certificate");
                if (Environment.CERT_PFX.equals(env.getCertFormat())) {
                    v.put("SAP_CERT_PFX_PATH", env.getPfxPath());
                    putIfSet(v, "SAP_CERT_PASSPHRASE", secrets.get(SECRET_CERT_PASSPHRASE));
                } else {
                    v.put("SAP_CERT_PATH", env.getCertPath());
                    v.put("SAP_CERT_KEY_PATH", env.getCertKeyPath());
                }
                break;
            case KERBEROS:
                v.put("SAP_AUTH_TYPE", "kerberos");
                putIfSet(v, "SAP_KERBEROS_SPN", env.getKerberosSpn());
                break;
            default:
                break;
        }
        if (env.getSystemType() != SystemType.FROM_CONFIG) {
            v.put("SAP_SYSTEM_TYPE", env.getSystemType().id);
        }
        if (env.isIgnoreTls()) {
            v.put("TLS_REJECT_UNAUTHORIZED", "0");
            v.put("NODE_TLS_REJECT_UNAUTHORIZED", "0");
        }
        putIfSet(v, "MCP_EXPOSITION", env.exposition());
        v.putAll(parseEnv(env.getExtraEnv()));
        return v;
    }

    /** Métodos baseados em URL recebem um .env gerado pelo plugin (forma documentada pelo servidor). */
    public static boolean needsGeneratedEnvFile(Environment env) {
        return env.getAuthMethod().usesSapUrl();
    }

    public static String envFileContent(Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Gerado pelo MCP ABAP ADT Launcher (Brain Consulting).\n");
        sb.append("# Arquivo temporário: é apagado quando o servidor para.\n");
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String value = e.getValue() == null ? "" : e.getValue().replace("\r", "").replace("\n", "");
            sb.append(e.getKey()).append('=').append(value).append('\n');
        }
        return sb.toString();
    }

    public static List<String> build(Settings s, Environment env, Path generatedEnvFile) {
        List<String> cmd = new ArrayList<>();
        String exe = s.getCommandPath() == null ? "" : s.getCommandPath().trim();
        String lower = exe.toLowerCase(Locale.ROOT);
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        if (windows && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            cmd.add("cmd.exe");
            cmd.add("/c");
        }
        cmd.add(exe);
        String args = s.getArgsTemplate() == null ? "" : s.getArgsTemplate()
                .replace("${port}", String.valueOf(env.getPort()))
                .replace("${name}", Names.slug(env.getName()));
        cmd.addAll(tokenize(args));

        switch (env.getAuthMethod()) {
            case SERVICE_KEY:
                cmd.add("--mcp=" + env.getDestination());
                if (env.getBrowserAuthPort() > 0) {
                    cmd.add("--browser-auth-port=" + env.getBrowserAuthPort());
                }
                if (env.isPersistSession()) {
                    cmd.add("--unsafe");
                }
                break;
            case ENV_FILE:
                cmd.add("--env-path=" + env.getEnvFilePath());
                break;
            case RFC:
                cmd.add("--connection-type=rfc");
                addEnvPath(cmd, generatedEnvFile);
                break;
            default:
                addEnvPath(cmd, generatedEnvFile);
                break;
        }
        String expo = env.exposition();
        if (!expo.isEmpty()) {
            cmd.add("--exposition=" + expo);
        }
        cmd.addAll(tokenize(env.getExtraArgs() == null ? "" : env.getExtraArgs()));
        return cmd;
    }

    private static void addEnvPath(List<String> cmd, Path file) {
        if (file != null) {
            cmd.add("--env-path=" + file.toAbsolutePath());
        }
    }

    private static void putIfSet(Map<String, String> v, String key, String value) {
        if (value != null && !value.isBlank()) {
            v.put(key, value);
        }
    }

    /** Lê linhas KEY=VALOR (ignora vazias e comentários #; remove aspas externas). */
    public static Map<String, String> parseEnv(String text) {
        Map<String, String> m = new LinkedHashMap<>();
        if (text == null) {
            return m;
        }
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring(7).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            m.put(key, value);
        }
        return m;
    }

    /** @return null se todas as linhas forem válidas; senão, a primeira linha inválida. */
    public static String invalidEnvLine(String text) {
        if (text == null) {
            return null;
        }
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || !line.substring(0, eq).trim().matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return raw;
            }
        }
        return null;
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
