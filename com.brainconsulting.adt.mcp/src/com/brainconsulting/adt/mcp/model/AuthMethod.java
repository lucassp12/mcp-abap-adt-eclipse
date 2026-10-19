package com.brainconsulting.adt.mcp.model;

/** As formas de conexão suportadas pelo mcp-abap-adt (fr0ster). */
public enum AuthMethod {
    BASIC("basic", "On-premise – usuário e senha (HTTP)", "Usuário/senha", SystemType.ONPREM),
    RFC("rfc", "On-premise – usuário e senha via RFC (requer SAP NW RFC SDK)", "RFC", SystemType.ONPREM),
    CERTIFICATE("certificate", "On-premise – certificado X.509 (mTLS)", "Certificado", SystemType.ONPREM),
    KERBEROS("kerberos", "On-premise – SSO Kerberos/SPNEGO", "Kerberos", SystemType.ONPREM),
    SERVICE_KEY("service-key", "BTP ABAP / S/4HANA Cloud – service key (login no navegador)", "Service key", SystemType.CLOUD),
    ENV_FILE("env-file", "Arquivo .env próprio (JWT gerado pelo mcp-auth, SNC, outros)", "Arquivo .env", SystemType.FROM_CONFIG);

    public final String id;
    public final String label;
    public final String shortLabel;
    public final SystemType defaultSystemType;

    AuthMethod(String id, String label, String shortLabel, SystemType defaultSystemType) {
        this.id = id;
        this.label = label;
        this.shortLabel = shortLabel;
        this.defaultSystemType = defaultSystemType;
    }

    /** Métodos configurados por URL + mandante, com .env gerado pelo plugin. */
    public boolean usesSapUrl() {
        return this == BASIC || this == RFC || this == CERTIFICATE || this == KERBEROS;
    }

    public boolean usesUserPassword() {
        return this == BASIC || this == RFC;
    }

    public static AuthMethod fromId(String id, AuthMethod def) {
        for (AuthMethod m : values()) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return def;
    }
}
