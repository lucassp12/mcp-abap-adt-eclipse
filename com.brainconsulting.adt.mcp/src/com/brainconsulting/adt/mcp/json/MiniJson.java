package com.brainconsulting.adt.mcp.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser e serializador JSON mínimo, sem dependências externas.
 * <p>
 * Escolha proposital: o plugin não importa bibliotecas de terceiros (Gson, Jackson...)
 * para não criar conflitos de versão com outros plugins instalados no Eclipse.
 * <p>
 * Objetos viram {@link LinkedHashMap} (preserva a ordem das chaves), arrays viram
 * {@link List}, números viram {@link Num} (guarda o texto original, sem perda de precisão).
 */
public final class MiniJson {

    /** Número JSON guardado exatamente como estava no texto. */
    public static final class Num {
        private final String raw;

        public Num(String raw) {
            this.raw = raw;
        }

        public String raw() {
            return raw;
        }

        @Override
        public String toString() {
            return raw;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Num && ((Num) o).raw.equals(raw);
        }

        @Override
        public int hashCode() {
            return raw.hashCode();
        }
    }

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    // ------------------------------------------------------------------ parse

    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("texto nulo");
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1); // BOM
        }
        MiniJson p = new MiniJson(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != p.s.length()) {
            throw p.err("conteúdo inesperado após o fim do JSON");
        }
        return v;
    }

    private Object value() {
        if (i >= s.length()) {
            throw err("fim inesperado do texto");
        }
        char c = s.charAt(i);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw err("caractere inesperado '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (peek() == '}') {
            i++;
            return m;
        }
        while (true) {
            ws();
            if (peek() != '"') {
                throw err("esperava o nome de uma chave");
            }
            String key = string();
            ws();
            expect(':');
            ws();
            m.put(key, value());
            ws();
            char c = next();
            if (c == ',') {
                continue;
            }
            if (c == '}') {
                return m;
            }
            throw err("esperava ',' ou '}'");
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++; // [
        ws();
        if (peek() == ']') {
            i++;
            return list;
        }
        while (true) {
            ws();
            list.add(value());
            ws();
            char c = next();
            if (c == ',') {
                continue;
            }
            if (c == ']') {
                return list;
            }
            throw err("esperava ',' ou ']'");
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw err("texto sem aspas de fechamento");
            }
            char c = s.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                char e = next();
                switch (e) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) {
                            throw err("escape \\u incompleto");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw err("escape \\u inválido");
                        }
                        i += 4;
                        break;
                    default:
                        throw err("escape inválido '\\" + e + "'");
                }
            } else if (c < 0x20) {
                throw err("caractere de controle dentro de texto");
            } else {
                sb.append(c);
            }
        }
    }

    private Num number() {
        int start = i;
        if (peek() == '-') {
            i++;
        }
        digits();
        if (peek() == '.') {
            i++;
            digits();
        }
        if (peek() == 'e' || peek() == 'E') {
            i++;
            if (peek() == '+' || peek() == '-') {
                i++;
            }
            digits();
        }
        return new Num(s.substring(start, i));
    }

    private void digits() {
        int start = i;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (start == i) {
            throw err("número inválido");
        }
    }

    private Object literal(String word, Object v) {
        if (!s.startsWith(word, i)) {
            throw err("valor inválido");
        }
        i += word.length();
        return v;
    }

    private void ws() {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
            } else {
                break;
            }
        }
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private char next() {
        if (i >= s.length()) {
            throw err("fim inesperado do texto");
        }
        return s.charAt(i++);
    }

    private void expect(char c) {
        if (next() != c) {
            i--;
            throw err("esperava '" + c + "'");
        }
    }

    private IllegalArgumentException err(String msg) {
        int line = 1;
        int col = 1;
        for (int k = 0; k < Math.min(i, s.length()); k++) {
            if (s.charAt(k) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new IllegalArgumentException(msg + " (linha " + line + ", coluna " + col + ")");
    }

    // -------------------------------------------------------------- stringify

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Boolean || v instanceof Num || v instanceof Number) {
            sb.append(v.toString());
        } else if (v instanceof String) {
            quote((String) v, sb);
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, indent + 2);
                quote(String.valueOf(e.getKey()), sb);
                sb.append(": ");
                write(e.getValue(), sb, indent + 2);
                if (++n < m.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof List) {
            List<?> list = (List<?>) v;
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int k = 0; k < list.size(); k++) {
                pad(sb, indent + 2);
                write(list.get(k), sb, indent + 2);
                if (k < list.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append(']');
        } else {
            quote(v.toString(), sb);
        }
    }

    private static void pad(StringBuilder sb, int n) {
        for (int k = 0; k < n; k++) {
            sb.append(' ');
        }
    }

    private static void quote(String str, StringBuilder sb) {
        sb.append('"');
        for (int k = 0; k < str.length(); k++) {
            char c = str.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------- helpers

    public static String asString(Object v, String def) {
        return v instanceof String ? (String) v : def;
    }

    public static boolean asBool(Object v, boolean def) {
        return v instanceof Boolean ? (Boolean) v : def;
    }

    public static int asInt(Object v, int def) {
        try {
            if (v instanceof Num) {
                return (int) Double.parseDouble(((Num) v).raw());
            }
            if (v instanceof Number) {
                return ((Number) v).intValue();
            }
            if (v instanceof String) {
                return Integer.parseInt(((String) v).trim());
            }
        } catch (NumberFormatException e) {
            // usa o padrão
        }
        return def;
    }
}
