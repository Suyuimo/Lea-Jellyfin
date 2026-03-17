package de.weinschenk.lea.modules.proxy;

import de.weinschenk.lea.api.BotContext;
import de.weinschenk.lea.api.CommandRequest;
import de.weinschenk.lea.api.LeaModule;

public class SynologyProxyPlugin implements LeaModule {

    // ─────────────────────────────────────────────
    // Konfiguration – hier anpassen!
    // ─────────────────────────────────────────────
    private static final String NAS_BASE_URL  = "https://192.168.1.1:5001"; // DSM-URL (intern)
    private static final String DSM_USER      = "admin";                     // DSM-Benutzername
    private static final String DSM_PASSWORD  = "geheim";                    // DSM-Passwort
    private static final String RULE_NAME     = "Jellyfin";                  // Name der Proxy-Regel im DSM
    // ─────────────────────────────────────────────

    private final SynologyApiClient apiClient;

    public SynologyProxyPlugin() {
        this.apiClient = new SynologyApiClient(NAS_BASE_URL, DSM_USER, DSM_PASSWORD);
    }

    @Override
    public String id() {
        return "jellyfin";
    }

    @Override
    public void onCommand(CommandRequest request, BotContext context) {
        switch (request.command()) {

            case "an" -> {
                context.log("jellyfin an: aktiviere Proxy-Regel");
                setProxyRule(request, context, true);
            }

            case "aus" -> {
                context.log("jellyfin aus: deaktiviere Proxy-Regel");
                setProxyRule(request, context, false);
            }

            case "status" -> {
                context.log("jellyfin status: frage Status ab");
                queryStatus(request, context);
            }

            case "hilfe" -> context.reply(request, """
                    🎬 *Jellyfin – Befehle*

                    jellyfin status  – Zeigt ob Jellyfin von außen erreichbar ist
                    jellyfin an      – Schaltet Jellyfin von außen erreichbar
                    jellyfin aus     – Sperrt Jellyfin vom externen Zugriff
                    jellyfin hilfe   – Diese Hilfe
                    """);

            default -> context.reply(request,
                    "❓ Unbekannter Befehl: `" + request.command() + "`\n" +
                    "Schreib `jellyfin hilfe` für alle Befehle.");
        }
    }

    // ── Interne Methoden ─────────────────────────

    private void setProxyRule(CommandRequest request, BotContext context, boolean enable) {
        try {
            String sid = apiClient.login();
            boolean found = apiClient.setRuleEnabled(sid, RULE_NAME, enable);
            apiClient.logout(sid);

            if (found) {
                String emoji = enable ? "✅" : "🔒";
                String zustand = enable ? "aktiviert" : "deaktiviert";
                context.reply(request, emoji + " Jellyfin wurde *" + zustand + "*.");
            } else {
                context.reply(request, "⚠️ Keine Proxy-Regel mit dem Namen *" + RULE_NAME + "* gefunden.\n" +
                        "Bitte prüfe den Namen in der Konstante RULE_NAME.");
            }
        } catch (Exception e) {
            context.log("proxy setRule Fehler: " + e.getMessage());
            context.reply(request, "❌ Fehler bei der NAS-Kommunikation:\n`" + e.getMessage() + "`");
        }
    }

    private void queryStatus(CommandRequest request, BotContext context) {
        try {
            String sid = apiClient.login();
            Boolean enabled = apiClient.getRuleEnabled(sid, RULE_NAME);
            apiClient.logout(sid);

            if (enabled == null) {
                context.reply(request, "⚠️ Keine Proxy-Regel mit dem Namen *" + RULE_NAME + "* gefunden.");
            } else if (enabled) {
                context.reply(request, "✅ Jellyfin ist *erreichbar* – Proxy-Regel ist aktiv.");
            } else {
                context.reply(request, "🔒 Jellyfin ist *nicht erreichbar* – Proxy-Regel ist deaktiviert.");
            }
        } catch (Exception e) {
            context.log("proxy status Fehler: " + e.getMessage());
            context.reply(request, "❌ Fehler bei der NAS-Kommunikation:\n`" + e.getMessage() + "`");
        }
    }
}
