package com.brainconsulting.adt.mcp.model;

/** Valor de SAP_SYSTEM_TYPE: decide quais ferramentas o servidor oferece (ex.: programas só existem on-premise). */
public enum SystemType {
    ONPREM("onprem", "On-premise (ECC / S/4HANA)", "On-premise"),
    CLOUD("cloud", "ABAP Cloud (BTP / S/4HANA Cloud)", "Cloud"),
    FROM_CONFIG("", "Definido no .env / service key", "—");

    public final String id;
    public final String label;
    public final String shortLabel;

    SystemType(String id, String label, String shortLabel) {
        this.id = id;
        this.label = label;
        this.shortLabel = shortLabel;
    }

    public static SystemType fromId(String id, SystemType def) {
        for (SystemType t : values()) {
            if (t.id.equals(id) || t.name().equalsIgnoreCase(id == null ? "" : id)) {
                return t;
            }
        }
        return def;
    }
}
