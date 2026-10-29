# Prüfkonfiguration der E-Rechnung (KoSIT-Validator)

Die Tests der E-Rechnung prüfen die erzeugte XML mit dem Validator der KoSIT
(`org.kosit:validator`, Testbereich) im Szenario EN 16931 in der Syntax CII (Plan #461, E20).
Die Dateien liegen nur im Testbereich und kommen nicht ins Produktionsabbild.

| Datei | Quelle | Lizenz |
|---|---|---|
| `scenarios.xml` | vom Projekt geschrieben: genau ein Szenario EN 16931 CII, ohne die Regeln von XRechnung | — |
| `EN16931-CII-validation.xslt` | CEN, `xslt/EN16931-CII-validation.xslt` aus Archiv 1 | EUPL 1.2, `LICENSE-EUPL-1.2.txt` |
| `cii/16b/xsd/*.xsd` (vier Dateien) | KoSIT, `resources/cii/16b/xsd/` aus Archiv 2 | Apache 2.0, `LICENSE-Apache-2.0.txt` |
| `default-report.xsl` | KoSIT, `resources/default-report.xsl` aus Archiv 2 | Apache 2.0, `LICENSE-Apache-2.0.txt` |

Die Dateien sind unverändert übernommen, nachdem die Prüfsumme des Archivs mit `shasum -a 256`
gestimmt hat.

1. CEN, Version 1.3.17:
   `https://github.com/ConnectingEurope/eInvoicing-EN16931/releases/download/validation-1.3.17/en16931-cii-1.3.17.zip`
   SHA-256 `e5f3235824b6b38454b1d54cf3f14e01efb5b33971fbefc4e64997d0ef619e89`
2. KoSIT, Konfiguration XRechnung 3.0.2 vom 2026-08-31:
   `https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v2026-08-31/xrechnung-3.0.2-validator-configuration-2026-08-31.zip`
   SHA-256 `2530cd107c414511c5d0462ec10f886910395abfca820db82e83d70bf01221a8`

Die Lizenztexte stammen aus den Repositories der beiden Herausgeber
(`ConnectingEurope/eInvoicing-EN16931`, `LICENSE.txt`; `itplr-kosit/validator-configuration-xrechnung`,
`LICENSE`).

Wer eine neuere Version übernimmt, lädt das Archiv, vergleicht die Prüfsumme mit der der
Veröffentlichung, ersetzt die Dateien und zieht Version und Prüfsumme hier nach.
