package com.brainconsulting.adt.mcp.model;

import java.text.Normalizer;
import java.util.Locale;

/** Utilitários de nomes. */
public final class Names {

    private Names() {
    }

    /** "Arezzo DEV (Produção)" → "arezzo-dev-producao". */
    public static String slug(String s) {
        String n = Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return n.replaceAll("^-+|-+$", "");
    }
}
