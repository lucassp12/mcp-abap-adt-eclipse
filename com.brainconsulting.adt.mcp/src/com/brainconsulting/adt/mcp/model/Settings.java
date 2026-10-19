package com.brainconsulting.adt.mcp.model;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.brainconsulting.adt.mcp.json.MiniJson;

/** Configurações gerais do plugin (valem para todos os ambientes). */
public class Settings {

    /** true = usa o Node.js + mcp-abap-adt embutidos no plugin; false = executável instalado na máquina. */
    private boolean embeddedServer = true;
    /** Caminho do executável do servidor MCP (só quando embeddedServer = false). */
    private String commandPath = defaultCommandPath();
    /** Argumentos; aceita ${port} e ${name}. */
    private String argsTemplate = "--transport=http --port=${port}";
    /** Caminho do endpoint HTTP exposto pelo servidor (confira no log de inicialização). */
    private String endpointPath = "/mcp/stream/http";
    /** Prefixo do nome do servidor dentro do Claude Code. */
    private String serverPrefix = "sap-";
    private boolean registerInClaude = true;
    private boolean removeOnStop = true;
    private String claudeConfigPath = Paths.get(System.getProperty("user.home"), ".claude.json").toString();
    /** Onde abrir o Claude Code: "external" (Windows Terminal/console) ou "internal" (view Terminal do Eclipse). */
    private String claudeTerminal = "external";
    /** Argumentos extras do comando claude (ex.: --continue). */
    private String claudeArgs = "";
    /** Pasta onde o auth-broker do mcp-abap-adt procura as service keys (destinos). */
    private String serviceKeysDir = ServiceKeys.defaultDir().toString();

    /** Corrige padrões da versão 1 do plugin, que estavam errados para o mcp-abap-adt (fr0ster). */
    public void migrateFromV1() {
        if ("/mcp".equals(endpointPath)) {
            endpointPath = "/mcp/stream/http";
        }
        if ("--transport=http --port ${port}".equals(argsTemplate)) {
            argsTemplate = "--transport=http --port=${port}";
        }
        if (argsTemplate != null) {
            // Na 1.1 ferramentas e conexão são definidas por ambiente; --exposition, --env-path,
            // --env e --mcp deixados nos argumentos gerais duplicariam esses parâmetros.
            argsTemplate = argsTemplate.replaceAll("\\s*--(exposition|env-path|env|mcp)[= ]\\S+", "").trim();
        }
    }

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
        s.embeddedServer = embeddedServer;
        s.commandPath = commandPath;
        s.argsTemplate = argsTemplate;
        s.endpointPath = endpointPath;
        s.serverPrefix = serverPrefix;
        s.registerInClaude = registerInClaude;
        s.removeOnStop = removeOnStop;
        s.claudeConfigPath = claudeConfigPath;
        s.serviceKeysDir = serviceKeysDir;
        s.claudeTerminal = claudeTerminal;
        s.claudeArgs = claudeArgs;
        return s;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("embeddedServer", embeddedServer);
        m.put("commandPath", commandPath);
        m.put("argsTemplate", argsTemplate);
        m.put("endpointPath", endpointPath);
        m.put("serverPrefix", serverPrefix);
        m.put("registerInClaude", registerInClaude);
        m.put("removeOnStop", removeOnStop);
        m.put("claudeConfigPath", claudeConfigPath);
        m.put("serviceKeysDir", serviceKeysDir);
        m.put("claudeTerminal", claudeTerminal);
        m.put("claudeArgs", claudeArgs);
        return m;
    }

    public static Settings fromJson(Map<?, ?> m) {
        Settings s = new Settings();
        s.embeddedServer = MiniJson.asBool(m.get("embeddedServer"), true);
        s.commandPath = MiniJson.asString(m.get("commandPath"), s.commandPath);
        s.argsTemplate = MiniJson.asString(m.get("argsTemplate"), s.argsTemplate);
        s.endpointPath = MiniJson.asString(m.get("endpointPath"), s.endpointPath);
        s.serverPrefix = MiniJson.asString(m.get("serverPrefix"), s.serverPrefix);
        s.registerInClaude = MiniJson.asBool(m.get("registerInClaude"), s.registerInClaude);
        s.removeOnStop = MiniJson.asBool(m.get("removeOnStop"), s.removeOnStop);
        s.claudeConfigPath = MiniJson.asString(m.get("claudeConfigPath"), s.claudeConfigPath);
        s.serviceKeysDir = MiniJson.asString(m.get("serviceKeysDir"), s.serviceKeysDir);
        s.claudeTerminal = MiniJson.asString(m.get("claudeTerminal"), s.claudeTerminal);
        s.claudeArgs = MiniJson.asString(m.get("claudeArgs"), s.claudeArgs);
        return s;
    }

    public boolean isEmbeddedServer() { return embeddedServer; }
    public void setEmbeddedServer(boolean embeddedServer) { this.embeddedServer = embeddedServer; }
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
    public boolean isClaudeInternalTerminal() { return "internal".equals(claudeTerminal); }
    public void setClaudeInternalTerminal(boolean internal) { claudeTerminal = internal ? "internal" : "external"; }
    public String getClaudeArgs() { return claudeArgs; }
    public void setClaudeArgs(String claudeArgs) { this.claudeArgs = claudeArgs; }
    public String getServiceKeysDir() { return serviceKeysDir; }
    public void setServiceKeysDir(String serviceKeysDir) { this.serviceKeysDir = serviceKeysDir; }
}
