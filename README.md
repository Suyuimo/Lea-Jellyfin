# lea-synology-proxy

Lea-Plugin zum Steuern des Synology DSM Reverse Proxys per Signal-Nachricht.
Damit lässt sich z.B. Jellyfin schnell von außen erreichbar machen oder wieder absichern.

## Konfiguration

In `SynologyProxyPlugin.java` die Konstanten anpassen:

```java
private static final String NAS_BASE_URL = "https://192.168.1.1:5001"; // DSM-Adresse (intern)
private static final String DSM_USER     = "admin";                      // DSM-Benutzername
private static final String DSM_PASSWORD = "geheim";                     // DSM-Passwort
private static final String RULE_NAME    = "Jellyfin";                   // Name der Proxy-Regel im DSM
```

> **Tipp:** Den Namen der Proxy-Regel findest du in DSM unter  
> *Systemsteuerung → Anmeldeportal → Erweitert → Reverse Proxy*

## Befehle

| Befehl         | Beschreibung                                      |
|----------------|---------------------------------------------------|
| `jellyfin hilfe`  | Zeigt alle verfügbaren Befehle                    |
| `jellyfin status` | Zeigt ob Jellyfin aktuell von außen erreichbar ist |
| `jellyfin an`     | Aktiviert die Jellyfin-Proxy-Regel                |
| `jellyfin aus`    | Deaktiviert die Jellyfin-Proxy-Regel              |

## Beispiel-Interaktion

```
Du:  jellyfin status
Lea: ✅ Jellyfin ist *erreichbar* – Proxy-Regel ist aktiv.

Du:  jellyfin aus
Lea: 🔒 Jellyfin wurde *deaktiviert*.

Du:  jellyfin an
Lea: ✅ Jellyfin wurde *aktiviert*.
```

## Abhängigkeiten

- Java 21+
- Lea-API `0.1.0`
- Keine externen Bibliotheken (nutzt Java 11+ `java.net.http.HttpClient`)

## Hinweise

- Das Plugin akzeptiert selbstsignierte TLS-Zertifikate (typisch für Synology intern).
- Die Synology-API `SYNO.Core.Web.DSM.RProxy` ist intern, aber seit DSM 6 stabil.
- Für produktiven Einsatz empfiehlt sich ein dedizierter DSM-Benutzer mit eingeschränkten Rechten.
