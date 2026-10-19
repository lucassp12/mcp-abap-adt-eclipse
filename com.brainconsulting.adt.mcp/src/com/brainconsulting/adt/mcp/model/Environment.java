package com.brainconsulting.adt.mcp.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.brainconsulting.adt.mcp.json.MiniJson;

/**
 * Um ambiente SAP (sistema + mandante + usuário) que pode ter o seu próprio servidor MCP.
 * A senha NÃO fica aqui: ela é guardada no Secure Storage do Eclipse.
 */
public class Environment {

    private String id = UUID.randomUUID().toString();
    private String name = "";
    private String url = "";
    private String client = "100";
    private String user = "";
    private String language = "PT";
    private int port = 3001;
    private boolean ignoreTls;

    public Environment copy() {
        Environment e = new Environment();
        e.id = id;
        e.name = name;
        e.url = url;
        e.client = client;
        e.user = user;
        e.language = language;
        e.port = port;
        e.ignoreTls = ignoreTls;
        return e;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("url", url);
        m.put("client", client);
        m.put("user", user);
        m.put("language", language);
        m.put("port", port);
        m.put("ignoreTls", ignoreTls);
        return m;
    }

    public static Environment fromJson(Map<?, ?> m) {
        Environment e = new Environment();
        String id = MiniJson.asString(m.get("id"), null);
        if (id != null && !id.isBlank()) {
            e.id = id;
        }
        e.name = MiniJson.asString(m.get("name"), "");
        e.url = MiniJson.asString(m.get("url"), "");
        e.client = MiniJson.asString(m.get("client"), "100");
        e.user = MiniJson.asString(m.get("user"), "");
        e.language = MiniJson.asString(m.get("language"), "PT");
        e.port = MiniJson.asInt(m.get("port"), 3001);
        e.ignoreTls = MiniJson.asBool(m.get("ignoreTls"), false);
        return e;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isIgnoreTls() { return ignoreTls; }
    public void setIgnoreTls(boolean ignoreTls) { this.ignoreTls = ignoreTls; }
}
