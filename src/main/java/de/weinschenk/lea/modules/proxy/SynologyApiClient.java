package de.weinschenk.lea.modules.proxy;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.regex.*;

/**
 * Kommuniziert mit der Synology DSM Web API.
 *
 * Verwendete API-Endpunkte:
 *   POST /webapi/auth.cgi          → Login / Logout
 *   GET  /webapi/entry.cgi         → Reverse-Proxy-Regeln lesen / schreiben
 *
 * Synology stellt für Reverse-Proxy-Regeln keine offizielle öffentliche API
 * bereit – wir verwenden SYNO.Core.Web.DSM.RProxy (intern, aber stabil).
 */
public class SynologyApiClient {

    private final String baseUrl;
    private final String user;
    private final String password;
    private final HttpClient httpClient;

    public SynologyApiClient(String baseUrl, String user, String password) {
        this.baseUrl   = baseUrl;
        this.user      = user;
        this.password  = password;
        this.httpClient = buildHttpClient();
    }

    // ── Login ────────────────────────────────────

    /** Meldet sich am DSM an und gibt die Session-ID (sid) zurück. */
    public String login() throws IOException, InterruptedException {
        String url = baseUrl + "/webapi/auth.cgi"
                + "?api=SYNO.API.Auth"
                + "&version=3"
                + "&method=login"
                + "&account=" + URLEncoder.encode(user, "UTF-8")
                + "&passwd=" + URLEncoder.encode(password, "UTF-8")
                + "&session=LeaProxy"
                + "&format=sid";

        String body = get(url);
        String sid  = extractJsonString(body, "sid");

        if (sid == null || sid.isEmpty()) {
            throw new IOException("Login fehlgeschlagen. Antwort: " + body);
        }
        return sid;
    }

    // ── Logout ───────────────────────────────────

    public void logout(String sid) {
        try {
            String url = baseUrl + "/webapi/auth.cgi"
                    + "?api=SYNO.API.Auth"
                    + "&version=3"
                    + "&method=logout"
                    + "&session=LeaProxy"
                    + "&_sid=" + sid;
            get(url);
        } catch (Exception ignored) {
            // Logout-Fehler sind unkritisch
        }
    }

    // ── Reverse-Proxy-Regeln ─────────────────────

    /**
     * Liest alle Proxy-Regeln und gibt den enabled-Status der gesuchten Regel zurück.
     * @return true/false wenn Regel gefunden, null wenn nicht gefunden
     */
    public Boolean getRuleEnabled(String sid, String ruleName)
            throws IOException, InterruptedException {

        String json = fetchRules(sid);
        return parseEnabledForRule(json, ruleName);
    }

    /**
     * Aktiviert oder deaktiviert eine Proxy-Regel anhand ihres Namens.
     * @return true wenn die Regel gefunden und aktualisiert wurde
     */
    public boolean setRuleEnabled(String sid, String ruleName, boolean enable)
            throws IOException, InterruptedException {

        // 1. Alle Regeln laden
        String rulesJson = fetchRules(sid);

        // 2. ID der Ziel-Regel heraussuchen
        String ruleId = findRuleId(rulesJson, ruleName);
        if (ruleId == null) return false;

        // 3. Regel aktualisieren
        String enabledStr = enable ? "true" : "false";
        String url = baseUrl + "/webapi/entry.cgi"
                + "?api=SYNO.Core.Web.DSM.RProxy"
                + "&version=1"
                + "&method=set"
                + "&id=" + URLEncoder.encode(ruleId, "UTF-8")
                + "&enable=" + enabledStr
                + "&_sid=" + sid;

        String response = get(url);
        return isSuccess(response);
    }

    // ── Interne Hilfsmethoden ────────────────────

    private String fetchRules(String sid) throws IOException, InterruptedException {
        String url = baseUrl + "/webapi/entry.cgi"
                + "?api=SYNO.Core.Web.DSM.RProxy"
                + "&version=1"
                + "&method=list"
                + "&_sid=" + sid;
        return get(url);
    }

    /**
     * Einfaches Regex-basiertes JSON-Parsing – kein externer Parser nötig.
     * Sucht nach dem Block der Regel mit dem gesuchten description/name-Feld
     * und liest daraus das "enable"-Feld.
     */
    private Boolean parseEnabledForRule(String json, String ruleName) {
        // Wir suchen nach "description":"<ruleName>" und nehmen den nächsten "enable"-Wert
        String escaped = Pattern.quote(ruleName);
        Pattern ruleBlock = Pattern.compile(
                "\\{[^}]*\"description\"\\s*:\\s*\"" + escaped + "\"[^}]*\\}");
        Matcher m = ruleBlock.matcher(json);
        if (!m.find()) return null;

        String block = m.group();
        Pattern enablePat = Pattern.compile("\"enable\"\\s*:\\s*(true|false)");
        Matcher em = enablePat.matcher(block);
        if (!em.find()) return null;
        return "true".equals(em.group(1));
    }

    private String findRuleId(String json, String ruleName) {
        String escaped = Pattern.quote(ruleName);
        Pattern ruleBlock = Pattern.compile(
                "\\{[^}]*\"description\"\\s*:\\s*\"" + escaped + "\"[^}]*\\}");
        Matcher m = ruleBlock.matcher(json);
        if (!m.find()) return null;

        String block = m.group();
        return extractJsonString(block, "id");
    }

    private boolean isSuccess(String json) {
        return json != null && json.contains("\"success\":true");
    }

    private String extractJsonString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " für URL: " + url);
        }
        return response.body();
    }

    /**
     * Baut einen HttpClient der selbstsignierte Zertifikate akzeptiert.
     * Synology NAS nutzen intern oft selbstsignierte Certs – für interne
     * Netzwerke ist das akzeptabel.
     */
    private HttpClient buildHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        } catch (Exception e) {
            // Fallback: Standard-HttpClient
            return HttpClient.newHttpClient();
        }
    }
}
