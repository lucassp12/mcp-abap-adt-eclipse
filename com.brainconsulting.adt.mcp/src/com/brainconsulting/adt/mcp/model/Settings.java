package com.brainconsulting.adt.mcp.model;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.brainconsulting.adt.mcp.json.MiniJson;

/** Configurações gerais do plugin (valem para todos os ambientes). */
public class Settings {

    /** Caminho do executável do servidor MCP. */
    private String commandPath = defaultCommandPath();
    /** Argumentos; aceita ${port} e ${name}. */
    private String argsTemplate = "--transport=http --port ${port}";
    /** Caminho do endpoint HTTP exposto pelo servidor (confira no log de inicialização). */
    private String endpointPath = "/mcp";
    /** Prefixo do nome do servidor dentro do Claude Code. */
    private String serverPrefix = "sap-";
    private boolean registerInClaude = true;
    private boolean removeOnStop = true;
    private String claudeConfigPath = Paths.get(System.getProperty("user.home"), ".claude.json").toString();

    public static String defaultCommandPath() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String appData = System.getenv("APPDATA");
        if (os.startsWith("windows") && appData != null) {
            return Paths.get(appData, "npm", "mcp-abap-adt.cmd").toString();
        }
        return "mcp-abap-adt";
    }

    public Settings copy() {
        Settings s = new Settings();
        s.commandPath = commandPath;
        s.argsTemplate = argsTemplate;
        s.endpointPath = endpointPath;
        s.serverPrefix = serverPrefix;
        s.registerInClaude = registerInClaude;
        s.removeOnStop = removeOnStop;
        s.claudeConfigPath = claudeConfigPath;
        return s;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("commandPath", commandPath);
        m.put("argsTemplate", argsTemplate);
        m.put("endpointPath", endpointPath);
        m.put("serverPrefix", serverPrefix);
        m.put("registerInClaude", registerInClaude);
        m.put("removeOnStop", removeOnStop);
        m.put("claudeConfigPath", claudeConfigPath);
        return m;
    }

    public static Settings fromJson(Map<?, ?> m) {
        Settings s = new Settings();
        s.commandPath = MiniJson.asString(m.get("commandPath"), s.commandPath);
        s.argsTemplate = MiniJson.asString(m.get("argsTemplate"), s.argsTemplate);
        s.endpointPath = MiniJson.asString(m.get("endpointPath"), s.endpointPath);
        s.serverPrefix = MiniJson.asString(m.get("serverPrefix"), s.serverPrefix);
        s.registerInClaude = MiniJson.asBool(m.get("registerInClaude"), s.registerInClaude);
        s.removeOnStop = MiniJson.asBool(m.get("removeOnStop"), s.removeOnStop);
        s.claudeConfigPath = MiniJson.asString(m.get("claudeConfigPath"), s.claudeConfigPath);
        return s;
    }

    public String getCommandPath() { return commandPath; }
    public void setCommandPath(String commandPath) { this.commandPath = commandPath; }
    public String getArgsTemplate() { return argsTemplate; }
    public void setArgsTemplate(String argsTemplate) { this.argsTemplate = argsTemplate; }
    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String endpointPath) { this.endpointPath = endpointPath; }
    public String getServerPrefix() { return serverPrefix; }
    public void setServerPrefix(String serverPrefix) { this.serverPrefix = serverPrefix; }
    public boolean isRegisterInClaude() { return registerInClaude; }
    public void setRegisterInClaude(boolean registerInClaude) { this.registerInClaude = registerInClaude; }
    public boolean isRemoveOnStop() { return removeOnStop; }
    public void setRemoveOnStop(boolean removeOnStop) { this.removeOnStop = removeOnStop; }
    public String getClaudeConfigPath() { return claudeConfigPath; }
    public void setClaudeConfigPath(String claudeConfigPath) { this.claudeConfigPath = claudeConfigPath; }
}
