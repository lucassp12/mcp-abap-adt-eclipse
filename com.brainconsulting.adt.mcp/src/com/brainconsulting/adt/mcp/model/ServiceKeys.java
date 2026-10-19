package com.brainconsulting.adt.mcp.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

import com.brainconsulting.adt.mcp.json.MiniJson;
import com.brainconsulting.adt.mcp.server.PrivateFiles;

/**
 * Service keys usadas como "destinos" pelo auth-broker do mcp-abap-adt: o servidor recebe
 * {@code --mcp=<destino>} e lê {@code <pasta>/<destino>.json}.
 */
public final class ServiceKeys {

    private ServiceKeys() {
    }

    /** Windows: %USERPROFILE%\Documents\mcp-abap-adt\service-keys; demais: ~/.config/mcp-abap-adt/service-keys. */
    public static Path defaultDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            String profile = System.getenv("USERPROFILE");
            String home = profile != null ? profile : System.getProperty("user.home");
            return Paths.get(home, "Documents", "mcp-abap-adt", "service-keys");
        }
        return Paths.get(System.getProperty("user.home"), ".config", "mcp-abap-adt", "service-keys");
    }

    public static Path file(String dir, String destination) {
        Path base = dir == null || dir.isBlank() ? defaultDir() : Paths.get(dir);
        return base.resolve(destination + ".json");
    }

    public static boolean isValidDestination(String d) {
        return d != null && d.matches("[A-Za-z0-9_.-]+");
    }

    /** Sugestão de destino a partir do nome do ambiente: "Arezzo DEV" → "AREZZO_DEV". */
    public static String suggestDestination(String environmentName) {
        return Names.slug(environmentName).replace('-', '_').toUpperCase(Locale.ROOT);
    }

    /** @return null se o arquivo parece uma service key; senão, a mensagem do problema. */
    public static String validate(Path json) {
        if (json == null || !Files.isRegularFile(json)) {
            return "Arquivo não encontrado: " + json;
        }
        Object parsed;
        try {
            parsed = MiniJson.parse(new String(Files.readAllBytes(json), StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            return "O arquivo não é um JSON válido: " + e.getMessage();
        }
        if (!(parsed instanceof Map)) {
            return "O arquivo não contém um objeto JSON.";
        }
        Map<?, ?> m = (Map<?, ?>) parsed;
        if (!m.containsKey("uaa") && !m.containsKey("url") && !m.containsKey("abap")) {
            return "Não parece uma service key do SAP BTP (faltam as chaves \"uaa\"/\"url\").";
        }
        return null;
    }

    /** URL do sistema ABAP declarada na service key, quando houver. */
    public static String abapUrl(Path json) {
        try {
            Object parsed = MiniJson.parse(new String(Files.readAllBytes(json), StandardCharsets.UTF_8));
            if (parsed instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) parsed;
                String url = MiniJson.asString(m.get("url"), null);
                if (url == null && m.get("abap") instanceof Map) {
                    url = MiniJson.asString(((Map<?, ?>) m.get("abap")).get("url"), null);
                }
                return url;
            }
        } catch (IOException | IllegalArgumentException e) {
            // sem URL
        }
        return null;
    }

    /** Copia a service key para a pasta de destinos, com permissão só para o dono. */
    public static Path importKey(Path source, String dir, String destination) throws IOException {
        String err = validate(source);
        if (err != null) {
            throw new IOException(err);
        }
        Path target = file(dir, destination);
        PrivateFiles.write(target, new String(Files.readAllBytes(source), StandardCharsets.UTF_8));
        return target;
    }
}
