package com.brainconsulting.adt.mcp.server;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import com.brainconsulting.adt.mcp.model.Environment;

/**
 * Testa se o SAP responde no endpoint ADT ({@code /sap/bc/adt/discovery}) com as
 * credenciais do ambiente, e traduz a falha num diagnóstico útil.
 */
public final class ConnectionTester {

    public static final class Result {
        public final boolean ok;
        public final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private ConnectionTester() {
    }

    public static Result test(Environment env, String password) {
        String base = env.getUrl() == null ? "" : env.getUrl().trim().replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(base + "/sap/bc/adt/discovery?sap-client="
                    + URLEncoder.encode(env.getClient(), StandardCharsets.UTF_8));
            if (uri.getHost() == null) {
                return new Result(false, "URL inválida: não foi possível identificar o host em \"" + base + "\".");
            }
        } catch (IllegalArgumentException e) {
            return new Result(false, "URL inválida: " + e.getMessage());
        }
        String hostPort = uri.getHost() + ":" + effectivePort(uri);

        try {
            HttpClient.Builder b = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER);
            if (env.isIgnoreTls()) {
                b.sslContext(trustAll());
            }
            HttpClient client = b.build();
            String auth = Base64.getEncoder().encodeToString(
                    (env.getUser() + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/atomsvc+xml, */*")
                    .GET()
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            return fromStatus(resp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Teste interrompido.");
        } catch (IOException e) {
            return classify(e, uri, hostPort);
        } catch (Exception e) {
            return new Result(false, "Erro inesperado: " + e);
        }
    }

    private static Result fromStatus(HttpResponse<Void> resp) {
        int sc = resp.statusCode();
        if (sc == 200) {
            return new Result(true, "Conexão OK (HTTP 200): o SAP respondeu e aceitou o login.\n"
                    + "O servidor MCP deve funcionar com estes dados.");
        }
        if (sc == 401) {
            return new Result(false, "O SAP respondeu, mas recusou o login (HTTP 401).\n"
                    + "Confira usuário, senha e mandante. Usuário bloqueado também dá 401.");
        }
        if (sc == 403) {
            return new Result(false, "Login aceito, mas sem autorização para o ADT (HTTP 403).\n"
                    + "Verifique os perfis do usuário (objetos S_ADT_RES, S_DEVELOP).");
        }
        if (sc == 404) {
            return new Result(false, "O servidor respondeu, mas o serviço ADT não foi encontrado (HTTP 404).\n"
                    + "Verifique na SICF se /sap/bc/adt está ativo, e se a URL aponta para o sistema certo.");
        }
        if (sc >= 300 && sc < 400) {
            String loc = resp.headers().firstValue("Location").orElse("(sem destino informado)");
            return new Result(false, "O servidor redirecionou (HTTP " + sc + ") para:\n" + loc
                    + "\nNormalmente isso indica que o certo é usar https (ou outra porta). Ajuste a URL.");
        }
        if (sc >= 500) {
            return new Result(false, "Erro no servidor SAP (HTTP " + sc + "). Veja a ST22/SM21 ou o log do ICM (SMICM).");
        }
        return new Result(false, "Resposta inesperada do SAP: HTTP " + sc + ".");
    }

    private static Result classify(IOException e, URI uri, String hostPort) {
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        if (e instanceof HttpConnectTimeoutException) {
            return new Result(false, "Tempo esgotado ao conectar em " + hostPort + ".\n"
                    + "A conexão TCP nem chegou a abrir. Verifique, nesta ordem:\n"
                    + "• VPN do cliente conectada\n"
                    + "• porta correta (SMICM → Goto → Services)\n"
                    + "• http x https (portas 80xx costumam ser http; 443xx, https)\n"
                    + "• firewall entre você e o servidor");
        }
        if (has(e, UnresolvedAddressException.class) || has(e, UnknownHostException.class)) {
            return new Result(false, "Host não encontrado: " + uri.getHost() + ".\n"
                    + "O nome não resolveu no DNS. Verifique a VPN e se o nome do host está correto.");
        }
        if (has(e, SSLException.class)) {
            return new Result(false, "Falha na negociação TLS/SSL com " + hostPort + ".\n"
                    + (https
                        ? "Se esta porta for HTTP, troque a URL para http://. Se for HTTPS com certificado interno, "
                          + "marque \"Ignorar validação de certificado\" no ambiente (somente DEV/QAS)."
                        : "Esta porta parece exigir HTTPS: troque a URL para https://."));
        }
        if (has(e, ConnectException.class)) {
            return new Result(false, "Conexão recusada em " + hostPort + ".\n"
                    + "O servidor existe, mas nada está escutando nessa porta. Confira a porta na SMICM.");
        }
        if (e instanceof HttpTimeoutException) {
            return new Result(false, "Conectou em " + hostPort + ", mas o SAP não respondeu em 30 segundos.");
        }
        return new Result(false, "Falha de comunicação com " + hostPort + ": " + e);
    }

    private static boolean has(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /**
     * Aceita qualquer certificado e não valida o hostname. Usado SOMENTE quando o
     * ambiente está marcado com "ignorar validação de certificado".
     */
    private static SSLContext trustAll() throws Exception {
        TrustManager tm = new X509ExtendedTrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a) { }
            @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a, Socket s) { }
            @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] { tm }, new java.security.SecureRandom());
        return ctx;
    }
}
