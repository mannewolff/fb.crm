# Bedienung

Dieser Teil der Dokumentation führt durch die Oberfläche von fb.crm. Er beginnt mit dem Einstieg
und den beiden Bereichen, die eine Rechnung voraussetzt: den eigenen Angaben und den
Rechnungseinstellungen. Wie fb.crm eingerichtet, gesichert und aktualisiert wird, steht im Teil
„Betrieb".

## Erste Schritte

**Zweck:** Dieser Abschnitt zeigt, wie man sich anmeldet, wie die Oberfläche aufgebaut ist und wo
man diese Dokumentation in fb.crm findet.

**Einstieg:** Im Browser die Adresse der eigenen Instanz öffnen, etwa `https://localhost` oder die
eigene Domain. Wer nicht angemeldet ist, landet auf der Seite „Anmelden".

**Anmelden:**

1. Im Feld „E-Mail-Adresse" die Adresse des Kontos eingeben.
2. Im Feld „Passwort" das Passwort eingeben.
3. „Anmelden" wählen.

Danach öffnet fb.crm die Startseite. Sind Adresse oder Passwort falsch, meldet die Seite „E-Mail-Adresse
oder Passwort ist falsch." Wer das Passwort vergessen hat, wählt „Passwort vergessen?" und bekommt
eine Mail mit einem Verweis, über den sich ein neues Passwort setzen lässt.

**Aufbau der Oberfläche:** Links steht die Schiene mit der Marke oben, rechts der Inhaltsbereich
mit der gewählten Ansicht. Die Schiene trägt zwei Blöcke:

- „Geschäft" mit „Angebote", „Arbeitszeit", „Rechnungen" und „Jahresabschlüsse" — das
  Tagesgeschäft.
- „Stammdaten" mit „Firmen" und „Eigene Angaben" — die Angaben, auf die das Tagesgeschäft
  zurückgreift.

Im Fuß der Schiene stehen „Administration" und „Dokumentation", darunter die Karte mit dem Namen
des Kontos. Ein Klick auf diese Karte öffnet ein Menü mit „Abmelden". Die Taste „Einklappen"
neben der Marke verkleinert die Schiene auf ihre Symbole.

**Diese Dokumentation in fb.crm:** Im Fuß der Schiene „Dokumentation" wählen. Die Dokumentation
ist nur nach der Anmeldung erreichbar; wer nicht angemeldet ist, kommt zuerst auf die Seite
„Anmelden". Im Projekt liegt derselbe Text als Datei unter `docs/`.

**Ergebnis:** Man ist angemeldet, sieht die Startseite und findet jeden Bereich über die Schiene.

## Eigene Angaben

**Zweck:** Die eigenen Angaben sind der Absender von fb.crm: Name, Anschrift, Kontakt, Steuer- und
Bankangaben. Sie erscheinen auf jeder Rechnung. Ohne sie lässt sich keine Rechnung stellen —
fb.crm verlangt dafür den Namen, Straße und Hausnummer, Postleitzahl, Ort, die Bankverbindung und
die Steuernummer oder die Umsatzsteuer-Identifikationsnummer.

**Einstieg:** In der Schiene im Block „Stammdaten" den Eintrag „Eigene Angaben" wählen. Die
Ansicht zeigt oben das Formular „Eigene Angaben" und darunter die Karte „Briefpapier".

**Angaben speichern:**

1. Die Felder ausfüllen: „Name", „Berufsbezeichnung", „Straße und Hausnummer", „Postleitzahl",
   „Ort", „Land", „E-Mail-Adresse", „Telefon", „Webadresse", „Steuernummer",
   „Umsatzsteuer-Identifikationsnummer" und „Bankverbindung".
2. „Speichern" wählen.

Kein Feld ist Pflicht beim Speichern: Die Angaben lassen sich nach und nach vervollständigen.
Nach dem Speichern steht „Die Angaben sind gespeichert." über dem Formular. Ist ein Wert zu lang,
steht die Meldung am betroffenen Feld. „Abbrechen" nimmt alle ungespeicherten Änderungen zurück
auf den zuletzt gespeicherten Stand.

**Briefpapier:** Die Karte „Briefpapier" legt fest, worauf eine Rechnung gedruckt wird. Ohne
Briefpapier steht dort „Noch kein Briefpapier hinterlegt. Rechnungen tragen die Gestaltung von
fb.crm."

1. „Briefpapier hochladen" wählen und ein PDF auswählen. Es darf höchstens 5 MB groß sein, höchstens
   zwei Seiten haben und muss A4 hochkant sein. Danach zeigt die Karte Dateiname, Größe und
   Seitenzahl des Briefpapiers.
2. Mit den beiden Schaltern festlegen, was fb.crm auf das Briefpapier eindruckt: „Absenderzeile
   eindrucken" setzt die kleine Absenderzeile über die Anschrift des Empfängers, „Steuer- und
   Bankzeile eindrucken" setzt die Zeile mit Steuer- und Bankangaben unten auf jede Seite. Trägt
   das Briefpapier diese Angaben schon selbst, bleibt der jeweilige Schalter aus.
3. Die sechs Ränder in Millimetern eintragen, mit höchstens einer Nachkommastelle, Komma oder
   Punkt: „Linker Rand (mm)", „Rechter Rand (mm)", „Beginn des Inhalts auf Seite 1 (mm)",
   „Unterer Rand auf Seite 1 (mm)", „Oberer Rand auf Folgeseiten (mm)" und „Unterer Rand auf
   Folgeseiten (mm)". Die Ränder halten den Text von den gedruckten Teilen des Briefpapiers fern.
4. „Einstellungen speichern" wählen. Danach steht „Die Einstellungen sind gespeichert." in der
   Karte.
5. „Probedruck" wählen. fb.crm gibt die Datei `Probedruck.pdf` heraus: eine Rechnung mit
   Beispieldaten und den eigenen Angaben auf dem hinterlegten Briefpapier, quer über jede Seite
   mit „Muster" gekennzeichnet. Daran lässt sich prüfen, ob Schalter und Ränder passen.

Mit „Briefpapier ersetzen" lässt sich ein anderes PDF hinterlegen, mit „Briefpapier entfernen"
druckt fb.crm wieder in seiner eigenen Gestaltung. Schalter und Ränder bleiben dabei, wie sie
eingestellt waren.

**Ergebnis:** Die eigenen Angaben sind gespeichert, und der Probedruck zeigt, wie eine Rechnung
auf dem eigenen Briefpapier aussieht.

## Rechnungseinstellungen

**Zweck:** Die Rechnungseinstellungen legen fest, woraus Nummer, Steuer und Zahlungsziel einer
Rechnung entstehen: das Muster der Rechnungsnummer, die nächste laufende Nummer, den
Mehrwertsteuersatz und das Zahlungsziel in Tagen.

**Einstieg:** Im Fuß der Schiene „Administration" wählen. Die Seite zeigt den Bereich „Rechnung".

**Einstellungen setzen:**

1. Im Feld „Muster der Rechnungsnummer" das Muster eintragen. `{NNNN}` steht für die laufende
   Nummer, die Zahl der N ist ihre Mindestbreite; `{JJJJ}` steht für das vierstellige und `{JJ}`
   für das zweistellige Jahr. Das Muster braucht genau einen Platzhalter für die laufende Nummer,
   höchstens einen für das Jahr und sonst nur Buchstaben, Ziffern und die Zeichen `-`, `_`, `/`
   und `.`. Beispiel: `{NNNN}-{JJJJ}` ergibt `0003-2026`.
2. Im Feld „Nächste laufende Nummer" die Nummer eintragen, mit der die nächste Rechnung gestellt
   wird — eine ganze Zahl ab 1.
3. Unter dem Muster steht laufend „Nächste Rechnungsnummer:" mit der Nummer, die sich aus Muster
   und nächster Nummer im laufenden Jahr ergibt.
4. Im Feld „Mehrwertsteuersatz in Prozent" den Steuersatz eintragen, etwa `19` oder `19,5` — eine
   Zahl von 0 bis 100 mit höchstens zwei Nachkommastellen.
5. Im Feld „Zahlungsziel in Tagen" die Zahl der Tage eintragen, die der Kunde bis zur Zahlung hat
   — eine ganze Zahl ab 0.
6. „Speichern" wählen.

Nach dem Speichern steht „Die Einstellungen sind gespeichert." über dem Formular. Was nicht
stimmt, meldet die Seite am betroffenen Feld, und es wird nichts gespeichert. „Abbrechen" nimmt
alle ungespeicherten Änderungen zurück auf den zuletzt gespeicherten Stand.

**Gestellte Rechnungen bleiben, wie sie sind:** Eine gestellte Rechnung hält ihre Nummer, ihren
Steuersatz und ihr Zahlungsziel fest. Eine spätere Änderung der Rechnungseinstellungen berührt sie
nicht mehr; sie gilt erst für die nächste Rechnung, die gestellt wird.

**Ergebnis:** Die Einstellungen sind gespeichert, und die Vorschau zeigt die Nummer, die die
nächste gestellte Rechnung trägt.
