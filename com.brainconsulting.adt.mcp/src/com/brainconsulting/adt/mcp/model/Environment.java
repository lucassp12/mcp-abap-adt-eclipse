package com.brainconsulting.adt.mcp.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.brainconsulting.adt.mcp.json.MiniJson;

/**
 * Um ambiente SAP com o seu próprio servidor MCP.
 * Segredos (senha, senha do certificado) NÃO ficam aqui: vão para o Secure Storage do Eclipse.
 */
public class Environment {

    public static final String CERT_PEM = "pem";
    public static final String CERT_PFX = "pfx";

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private AuthMethod authMethod = AuthMethod.BASIC;
    private SystemType systemType = SystemType.ONPREM;
    private int port = 3001;

    // URL + mandante (basic, rfc, certificate, kerberos)
    private String url = "";
    private String client = "100";
    private String language = "PT";
    private boolean ignoreTls;

    // basic / rfc
    private String user = "";

    // certificate
    private String certFormat = CERT_PEM;
    private String certPath = "";
    private String certKeyPath = "";
    private String pfxPath = "";

    // kerberos
    private String kerberosSpn = "";

    // service key (destino do auth-broker)
    private String destination = "";
    private int browserAuthPort = 61001;
    private boolean persistSession;

    // .env próprio
    private String envFilePath = "";

    // grupos de ferramentas (--exposition)
    private boolean exposeReadonly = true;
    private boolean exposeHigh = true;
    private boolean exposeLow = false; // high e low são mutuamente exclusivos no mcp-abap-adt

    // Claude Code
    private String workDir = "";

    // avançado
    private String extraArgs = "";
    private String extraEnv = "";

    public Environment copy() {
        return fromJson(toJson());
    }

    /** "readonly,high,low" conforme os grupos marcados. */
    public String exposition() {
        List<String> groups = new ArrayList<>();
        if (exposeReadonly) {
            groups.add("readonly");
        }
        if (exposeHigh) {
            groups.add("high");
        }
        if (exposeLow) {
            groups.add("low");
        }
        return String.join(",", groups);
    }

    /** Texto curto para a coluna "Alvo" da view. */
    public String targetLabel() {
        switch (authMethod) {
            case SERVICE_KEY:
                return "destino " + destination;
            case ENV_FILE:
                return envFilePath;
            default:
                return url + (client == null || client.isBlank() ? "" : "  (" + client + ")");
        }
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("authMethod", authMethod.id);
        m.put("systemType", systemType.name());
        m.put("port", port);
        m.put("url", url);
        m.put("client", client);
        m.put("language", language);
        m.put("ignoreTls", ignoreTls);
        m.put("user", user);
        m.put("certFormat", certFormat);
        m.put("certPath", certPath);
        m.put("certKeyPath", certKeyPath);
        m.put("pfxPath", pfxPath);
        m.put("kerberosSpn", kerberosSpn);
        m.put("destination", destination);
        m.put("browserAuthPort", browserAuthPort);
        m.put("persistSession", persistSession);
        m.put("envFilePath", envFilePath);
        m.put("exposeReadonly", exposeReadonly);
        m.put("exposeHigh", exposeHigh);
        m.put("exposeLow", exposeLow);
        m.put("workDir", workDir);
        m.put("extraArgs", extraArgs);
        m.put("extraEnv", extraEnv);
        return m;
    }

    public static Environment fromJson(Map<?, ?> m) {
        Environment e = new Environment();
        String id = MiniJson.asString(m.get("id"), null);
        if (id != null && !id.isBlank()) {
            e.id = id;
        }
        e.name = MiniJson.asString(m.get("name"), "");
        e.authMethod = AuthMethod.fromId(MiniJson.asString(m.get("authMethod"), null), AuthMethod.BASIC);
        e.systemType = SystemType.fromId(MiniJson.asString(m.get("systemType"), null), e.authMethod.defaultSystemType);
        e.port = MiniJson.asInt(m.get("port"), 3001);
        e.url = MiniJson.asString(m.get("url"), "");
        e.client = MiniJson.asString(m.get("client"), "100");
        e.language = MiniJson.asString(m.get("language"), "PT");
        e.ignoreTls = MiniJson.asBool(m.get("ignoreTls"), false);
        e.user = MiniJson.asString(m.get("user"), "");
        e.certFormat = MiniJson.asString(m.get("certFormat"), CERT_PEM);
        e.certPath = MiniJson.asString(m.get("certPath"), "");
        e.certKeyPath = MiniJson.asString(m.get("certKeyPath"), "");
        e.pfxPath = MiniJson.asString(m.get("pfxPath"), "");
        e.kerberosSpn = MiniJson.asString(m.get("kerberosSpn"), "");
        e.destination = MiniJson.asString(m.get("destination"), "");
        e.browserAuthPort = MiniJson.asInt(m.get("browserAuthPort"), 61001);
        e.persistSession = MiniJson.asBool(m.get("persistSession"), false);
        e.envFilePath = MiniJson.asString(m.get("envFilePath"), "");
        e.exposeReadonly = MiniJson.asBool(m.get("exposeReadonly"), true);
        e.exposeHigh = MiniJson.asBool(m.get("exposeHigh"), true);
        e.exposeLow = MiniJson.asBool(m.get("exposeLow"), false);
        if (e.exposeHigh && e.exposeLow) {
            e.exposeLow = false; // combinação recusada pelo servidor
        }
        e.workDir = MiniJson.asString(m.get("workDir"), "");
        e.extraArgs = MiniJson.asString(m.get("extraArgs"), "");
        e.extraEnv = MiniJson.asString(m.get("extraEnv"), "");
        return e;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public AuthMethod getAuthMethod() { return authMethod; }
    public void setAuthMethod(AuthMethod v) { authMethod = v; }
    public SystemType getSystemType() { return systemType; }
    public void setSystemType(SystemType v) { systemType = v; }
    public int getPort() { return port; }
    public void setPort(int v) { port = v; }
    public String getUrl() { return url; }
    public void setUrl(String v) { url = v; }
    public String getClient() { return client; }
    public void setClient(String v) { client = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { language = v; }
    public boolean isIgnoreTls() { return ignoreTls; }
    public void setIgnoreTls(boolean v) { ignoreTls = v; }
    public String getUser() { return user; }
    public void setUser(String v) { user = v; }
    public String getCertFormat() { return certFormat; }
    public void setCertFormat(String v) { certFormat = v; }
    public String getCertPath() { return certPath; }
    public void setCertPath(String v) { certPath = v; }
    public String getCertKeyPath() { return certKeyPath; }
    public void setCertKeyPath(String v) { certKeyPath = v; }
    public String getPfxPath() { return pfxPath; }
    public void setPfxPath(String v) { pfxPath = v; }
    public String getKerberosSpn() { return kerberosSpn; }
    public void setKerberosSpn(String v) { kerberosSpn = v; }
    public String getDestination() { return destination; }
    public void setDestination(String v) { destination = v; }
    public int getBrowserAuthPort() { return browserAuthPort; }
    public void setBrowserAuthPort(int v) { browserAuthPort = v; }
    public boolean isPersistSession() { return persistSession; }
    public void setPersistSession(boolean v) { persistSession = v; }
    public String getEnvFilePath() { return envFilePath; }
    public void setEnvFilePath(String v) { envFilePath = v; }
    public boolean isExposeReadonly() { return exposeReadonly; }
    public void setExposeReadonly(boolean v) { exposeReadonly = v; }
    public boolean isExposeHigh() { return exposeHigh; }
    public void setExposeHigh(boolean v) { exposeHigh = v; }
    public boolean isExposeLow() { return exposeLow; }
    public void setExposeLow(boolean v) { exposeLow = v; }
    public String getWorkDir() { return workDir; }
    public void setWorkDir(String v) { workDir = v; }
    public String getExtraArgs() { return extraArgs; }
    public void setExtraArgs(String v) { extraArgs = v; }
    public String getExtraEnv() { return extraEnv; }
    public void setExtraEnv(String v) { extraEnv = v; }
}
