package com.brainconsulting.adt.mcp.server;

import java.io.IOException;
import java.io.InputStream;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import com.brainconsulting.adt.mcp.model.Environment;
import com.brainconsulting.adt.mcp.model.ServiceKeys;

/**
 * Testa um ambiente antes de subir o servidor: chama {@code /sap/bc/adt/discovery} da forma
 * possível para cada método de conexão e traduz o resultado num diagnóstico.
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

    /** Como interpretar a resposta HTTP. */
    private enum Mode {
        /** Enviamos usuário/senha: 200 = login OK. */
        CREDENTIALS,
        /** Sem credenciais: qualquer 200/401/403 prova que rede, porta e protocolo estão certos. */
        REACHABILITY,
        /** Enviamos certificado de cliente: 200 = certificado aceito. */
        CERTIFICATE,
        /** Sem credenciais: o 401 precisa oferecer Negotiate. */
        KERBEROS
    }

    private ConnectionTester() {
    }

    public static Result test(Environment env, Map<String, String> secrets, String serviceKeysDir) {
        switch (env.getAuthMethod()) {
            case BASIC:
                return probe(env.getUrl(), env.getClient(),
                        basic(env.getUser(), secrets.get(CommandLine.SECRET_PASSWORD)),
                        env.isIgnoreTls(), null, Mode.CREDENTIALS);

            case RFC:
                return new Result(true, "Conexões RFC não são testadas por HTTP.\n"
                        + "Pré-requisitos: SAP NW RFC SDK instalado e o pacote npm de RFC exigido pela sua versão "
                        + "do mcp-abap-adt (veja docs/installation/RFC_SETUP.md do projeto).\n"
                        + "Inicie o servidor e acompanhe o Console: erros de logon aparecem lá.");

            case CERTIFICATE:
                if (Environment.CERT_PFX.equals(env.getCertFormat())) {
                    SSLContext ctx;
                    try {
                        ctx = pfxContext(Paths.get(env.getPfxPath()),
                                secrets.get(CommandLine.SECRET_CERT_PASSPHRASE), env.isIgnoreTls());
                    } catch (Exception e) {
                        return new Result(false, "Não foi possível abrir o certificado .pfx: " + e.getMessage()
                                + "\nConfira o arquivo e a senha do certificado.");
                    }
                    return probe(env.getUrl(), env.getClient(), null, env.isIgnoreTls(), ctx, Mode.CERTIFICATE);
                } else {
                    Result r = probe(env.getUrl(), env.getClient(), null, env.isIgnoreTls(), null, Mode.REACHABILITY);
                    return new Result(r.ok, r.message + "\n\nCom certificado PEM, este teste confirma só que o servidor "
                            + "é alcançável; o certificado é validado quando o servidor MCP inicia (veja o Console).");
                }

            case KERBEROS:
                return probe(env.getUrl(), env.getClient(), null, env.isIgnoreTls(), null, Mode.KERBEROS);

            case SERVICE_KEY: {
                Path key = ServiceKeys.file(serviceKeysDir, env.getDestination());
                if (!Files.isRegularFile(key)) {
                    return new Result(false, "Service key não encontrada em:\n" + key
                            + "\n\nEdite o ambiente e use \"Importar service key...\".");
                }
                String err = ServiceKeys.validate(key);
                if (err != null) {
                    return new Result(false, err);
                }
                String url = ServiceKeys.abapUrl(key);
                if (url == null) {
                    return new Result(true, "Service key válida (" + key.getFileName() + ").\n"
                            + "Ela não declara a URL do sistema, então a rede não foi testada.");
                }
                Result r = probe(url, null, null, false, null, Mode.REACHABILITY);
                return new Result(r.ok, "Service key válida (" + key.getFileName() + ").\n" + r.message
                        + (r.ok ? "\nAo iniciar o servidor, o login acontece no navegador (XSUAA)." : ""));
            }

            case ENV_FILE: {
                Path f = Paths.get(env.getEnvFilePath());
                if (!Files.isRegularFile(f)) {
                    return new Result(false, "Arquivo .env não encontrado: " + f);
                }
                Map<String, String> kv;
                try {
                    kv = CommandLine.parseEnv(new String(Files.readAllBytes(f), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    return new Result(false, "Não foi possível ler " + f + ": " + e.getMessage());
                }
                String url = kv.get("SAP_URL");
                if (url == null || url.isBlank()) {
                    return new Result(false, "O .env não define SAP_URL.");
                }
                boolean ignoreTls = "0".equals(kv.get("TLS_REJECT_UNAUTHORIZED"))
                        || "0".equals(kv.get("NODE_TLS_REJECT_UNAUTHORIZED"));
                String type = kv.getOrDefault("SAP_AUTH_TYPE", "").toLowerCase(Locale.ROOT);
                if ("basic".equals(type) && kv.containsKey("SAP_USERNAME") && kv.containsKey("SAP_PASSWORD")) {
                    return probe(url, kv.get("SAP_CLIENT"), basic(kv.get("SAP_USERNAME"), kv.get("SAP_PASSWORD")),
                            ignoreTls, null, Mode.CREDENTIALS);
                }
                Result r = probe(url, kv.get("SAP_CLIENT"), null, ignoreTls, null, Mode.REACHABILITY);
                return new Result(r.ok, r.message + (type.isEmpty() ? "" : "\nAutenticação do .env: " + type
                        + " (validada quando o servidor inicia)."));
            }

            default:
                return new Result(false, "Método de conexão desconhecido.");
        }
    }

    // ----------------------------------------------------------------- probe

    private static Result probe(String baseUrl, String client, String authHeader, boolean ignoreTls,
            SSLContext custom, Mode mode) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        URI uri;
        try {
            String q = client == null || client.isBlank() ? ""
                    : "?sap-client=" + URLEncoder.encode(client.trim(), StandardCharsets.UTF_8);
            uri = URI.create(base + "/sap/bc/adt/discovery" + q);
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
            if (custom != null) {
                b.sslContext(custom);
            } else if (ignoreTls) {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, new TrustManager[] { trustAll() }, new SecureRandom());
                b.sslContext(ctx);
            }
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/atomsvc+xml, */*")
                    .GET();
            if (authHeader != null) {
                rb.header("Authorization", authHeader);
            }
            HttpResponse<Void> resp = b.build().send(rb.build(), HttpResponse.BodyHandlers.discarding());
            return interpret(resp, mode, hostPort);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, "Teste interrompido.");
        } catch (IOException e) {
            return classify(e, uri, hostPort);
        } catch (Exception e) {
            return new Result(false, "Erro inesperado: " + e);
        }
    }

    private static Result interpret(HttpResponse<Void> resp, Mode mode, String hostPort) {
        int sc = resp.statusCode();
        if (sc >= 300 && sc < 400) {
            String loc = resp.headers().firstValue("Location").orElse("(sem destino informado)");
            return new Result(false, "O servidor redirecionou (HTTP " + sc + ") para:\n" + loc
                    + "\nNormalmente isso indica que o certo é usar https (ou outra porta). Ajuste a URL.");
        }
        if (sc == 404) {
            return new Result(false, "O servidor respondeu, mas o serviço ADT não foi encontrado (HTTP 404).\n"
                    + "Verifique na SICF se /sap/bc/adt está ativo e se a URL aponta para o sistema certo.");
        }
        if (sc >= 500) {
            return new Result(false, "Erro no servidor SAP (HTTP " + sc + "). Veja ST22/SM21 ou o log do ICM (SMICM).");
        }
        switch (mode) {
            case CREDENTIALS:
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
                break;
            case REACHABILITY:
                if (sc == 200 || sc == 401 || sc == 403) {
                    return new Result(true, "O servidor SAP respondeu em " + hostPort + " (HTTP " + sc
                            + "): rede, porta e protocolo estão corretos.");
                }
                break;
            case CERTIFICATE:
                if (sc == 200) {
                    return new Result(true, "Certificado aceito (HTTP 200): o SAP autenticou pelo certificado.");
                }
                if (sc == 401 || sc == 403) {
                    return new Result(false, "O servidor respondeu, mas não aceitou o certificado (HTTP " + sc + ").\n"
                            + "Verifique o mapeamento certificado → usuário (CERTRULE / USREXTID) e se o ICM "
                            + "aceita certificado de cliente (icm/HTTPS/verify_client).");
                }
                break;
            case KERBEROS: {
                List<String> offers = resp.headers().allValues("WWW-Authenticate");
                String all = String.join(", ", offers);
                if (sc == 200) {
                    return new Result(true, "O servidor respondeu HTTP 200 sem pedir login.");
                }
                if (all.toLowerCase(Locale.ROOT).contains("negotiate")) {
                    return new Result(true, "Servidor alcançável e oferece Kerberos/SPNEGO (Negotiate).\n"
                            + "Para o servidor MCP funcionar: ticket Kerberos válido (klist) e o pacote npm "
                            + "\"kerberos\" instalado (npm i -g kerberos).");
                }
                if (all.toLowerCase(Locale.ROOT).contains("ntlm")) {
                    return new Result(false, "O servidor oferece NTLM, não Kerberos (" + all + ").\n"
                            + "O mcp-abap-adt recusa NTLM. Peça ao Basis para habilitar SPNEGO para o seu usuário.");
                }
                return new Result(false, "O servidor não ofereceu Kerberos/SPNEGO"
                        + (all.isEmpty() ? "." : " (ofereceu: " + all + ")."));
            }
            default:
                break;
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

    // --------------------------------------------------------------- helpers

    private static String basic(String user, String password) {
        String raw = (user == null ? "" : user) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static SSLContext pfxContext(Path pfx, String passphrase, boolean ignoreTls) throws Exception {
        char[] pass = passphrase == null ? new char[0] : passphrase.toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(pfx)) {
            ks.load(in, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pass);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), ignoreTls ? new TrustManager[] { trustAll() } : null, new SecureRandom());
        return ctx;
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

    /** Aceita qualquer certificado do servidor, sem checar hostname. Só com "ignorar validação" marcado. */
    private static TrustManager trustAll() {
        return new X509ExtendedTrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] c, String a) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a) { }
            @Override public void checkClientTrusted(X509Certificate[] c, String a, Socket s) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a, Socket s) { }
            @Override public void checkClientTrusted(X509Certificate[] c, String a, SSLEngine e) { }
            @Override public void checkServerTrusted(X509Certificate[] c, String a, SSLEngine e) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
    }
}
