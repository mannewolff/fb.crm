# Bedienung

Dieser Teil der Dokumentation führt durch die Oberfläche von fb.crm. Er beginnt mit dem Einstieg,
sagt, was geschieht, wenn zwei Personen dasselbe zugleich bearbeiten, und folgt dann den beiden
Bereichen, die eine Rechnung voraussetzt: den eigenen Angaben und den Rechnungseinstellungen. Wie
fb.crm eingerichtet, gesichert und aktualisiert wird, steht im Teil „Betrieb".

## Erste Schritte

**Zweck:** Dieser Abschnitt zeigt, wie man sich anmeldet, wie die Oberfläche aufgebaut ist und wo
man diese Dokumentation in fb.crm findet.

**Einstieg:** Im Browser die Adresse der eigenen Instanz öffnen, etwa `https://localhost` oder die
eigene Domain. Wer nicht angemeldet ist, landet auf der Seite „Anmelden".

**Anmelden:** Jede Person hat ein eigenes Konto und meldet sich mit ihrer eigenen E-Mail-Adresse
an. Angemeldet wird man mit einem Code, den fb.crm per Mail an diese Adresse schickt. Wer neu ist,
bekommt vorher eine Begrüßungsmail mit der Adresse von fb.crm, sobald ein C-Level das Konto
angelegt hat.

1. Im Feld „E-Mail-Adresse" die eigene Adresse eingeben und „Code anfordern" wählen.
2. Die Seite zeigt nun das Feld „Code" und den Hinweis, dass ein Code unterwegs ist, falls es zu
   dieser Adresse ein Konto gibt. Der Hinweis lautet für jede Adresse gleich; er verrät nicht, ob
   es das Konto gibt.
3. Den sechsstelligen Code aus der Mail in das Feld „Code" eintippen und „Anmelden" wählen.

Danach ist man angemeldet und sieht die Startseite. Ein Code gilt 10 Minuten und nur einmal. Ist
er falsch, abgelaufen oder schon benutzt, meldet die Seite „Der Code ist falsch, abgelaufen oder
schon benutzt. Bitte einen neuen Code anfordern." Über „Andere Adresse" geht es zurück zum ersten
Schritt, dort fordert man einen neuen Code an oder korrigiert die Adresse. Ist der Mailversand
ausgeschaltet, steht der Code im Protokoll des Servers (siehe Teil „Betrieb").

**Aufbau der Oberfläche:** Links steht die Schiene mit der Marke oben, rechts der Inhaltsbereich
mit der gewählten Ansicht. Die Schiene trägt zwei Blöcke:

- „Geschäft" mit „Angebote", „Arbeitszeit", „Rechnungen" und „Jahresabschlüsse" — das
  Tagesgeschäft.
- „Stammdaten" mit „Firmen" und „Eigene Angaben" — die Angaben, auf die das Tagesgeschäft
  zurückgreift.

Im Fuß der Schiene stehen „Mitarbeitende", „Administration" und „Dokumentation", darunter die
Karte mit dem Namen des Kontos, seiner Adresse und seiner Rolle. Ein Klick auf diese Karte öffnet ein Menü mit
„Abmelden". Die Taste „Einklappen" neben der Marke verkleinert die Schiene auf ihre Symbole.

Die Schiene sieht je Rolle verschieden aus: Sie zeigt nur, was der eigenen Rolle offensteht. Ein
Employee findet darin weder „Eigene Angaben" noch „Administration"; C-Level und ReadOnly sehen
beide. „Mitarbeitende" sieht nur ein C-Level. Ruft man eine Ansicht, die der eigenen Rolle nicht
offensteht, über ihre Adresse direkt auf, erscheint die Meldung „Diese Ansicht steht deiner Rolle
nicht offen" mit einem Weg zur Startseite. Welche Rolle das eigene Konto hat, steht auf der Karte im Fuß der Schiene.

**Diese Dokumentation in fb.crm:** Im Fuß der Schiene „Dokumentation" wählen. Die Dokumentation
ist nur nach der Anmeldung erreichbar; wer nicht angemeldet ist, kommt zuerst auf die Seite
„Anmelden". Zuerst erscheint der Teil „Bedienung"; oben rechts wechselt man zum Teil „Betrieb"
und zurück. Die Karte „Inhalt" führt jeden Abschnitt des gezeigten Teils auf, ein Klick springt zu
ihm. Im Projekt liegt derselbe Text als Datei unter `docs/`.

**Ergebnis:** Man ist angemeldet, sieht die Startseite und findet jeden Bereich über die Schiene.

## Gleichzeitig arbeiten

**Zweck:** Mehrere Personen arbeiten mit fb.crm, und manchmal haben zwei dasselbe Angebot offen.
Speichern beide nacheinander, überschreibt fb.crm die Änderung des Ersten nicht still: Es weist
das zweite Speichern ab, sagt, wer wann geändert hat, und niemand verliert seine Eingaben. Dieser
Abschnitt zeigt, was dann geschieht und wie man weitermacht. Bisher gilt das beim Speichern in der
Maske „Angebot bearbeiten" und in der Ansicht eines Angebots bei „Status weiter", „Status zurück",
„Abschließen", „Wieder öffnen", „Stornieren" und „Storno zurücknehmen", dazu beim Bearbeiten und
Löschen eines Kommentars und beim Löschen einer Anlage. An einer Rechnung gilt es beim Speichern
und Löschen des Entwurfs, bei „Rechnung stellen" und bei „Als bezahlt markieren", „Abschreiben" und
„Zurück auf gestellt". An einer nachgetragenen Rechnung gilt es in jedem Zustand beim Speichern in
der Maske „Nachgetragene Rechnung bearbeiten", beim Ersetzen oder Entfernen ihres PDFs, beim Löschen
und bei „Als bezahlt markieren", „Abschreiben" und „Zurück auf gestellt". Unter „Administration"
gilt es beim Speichern der Einstellungen zur Rechnung. Bei den Eigenen Angaben gilt es beim
Speichern der Angaben und in der Karte „Briefpapier" beim Hochladen, Ersetzen und Entfernen des Briefpapiers und bei „Einstellungen
speichern". Bei Firmen und Ansprechpartnern gilt es beim Speichern in den Masken „Firma
bearbeiten" und „Ansprechpartner bearbeiten" und bei „Stilllegen" und „Wieder aktivieren". In der
Arbeitszeit gilt es beim Speichern im Dialog „Arbeitszeit ändern" und beim Löschen eines Eintrags.
Unter „Mitarbeitende" gilt es beim Speichern in der Maske „Konto bearbeiten" und bei „Stilllegen"
und „Wieder aktivieren".
Das
Angebot zählt dabei als Ganzes, mit seinen Positionen und seinem Status; jeder Kommentar ist ein
Eintrag für sich.

**Einstieg:** Die Abweisung erscheint von selbst, wenn man „Speichern" wählt und jemand anderes
das Angebot seit dem Öffnen gespeichert hat. Solange der geöffnete Stand aktuell ist, merkt man vom
Schutz nichts.

1. Oben in der Maske steht „Nicht ausgeführt" mit einem Satz wie „Anna hat diesen Eintrag
   inzwischen geändert (10:42)." Am selben Tag nennt er die Uhrzeit, sonst Datum und Uhrzeit. War
   es die eigene Änderung aus einem anderen Fenster, lautet er „Du hast diesen Eintrag inzwischen in
   einem anderen Fenster geändert". Die eigenen Eingaben stehen weiter im Formular.
2. „Neu laden" wählen. Das Formular zeigt nun den aktuellen Stand, so wie der andere ihn
   gespeichert hat. Daneben stehen unter „Deine Eingaben" die eigenen Werte Feld für Feld zum
   Kopieren.
3. Die eigene Änderung auf dem neuen Stand erneut eintragen, etwa den Titel aus „Deine Eingaben"
   in das Feld „Titel" kopieren.
4. „Speichern" wählen. Gespeichert wird jetzt auf dem aktuellen Stand, und die Änderung des anderen
   bleibt erhalten.
5. „Verwerfen" unter „Deine Eingaben" nimmt die eigenen Werte weg, sobald man sie nicht mehr
   braucht.

In der Ansicht eines Angebots steht dieselbe Meldung über der Karte, wenn ein Statuswechsel, ein
Storno oder die Rücknahme eines Stornos abgewiesen wird. Die Ansicht zeigt dann weiter den Stand,
den man gesehen hat. „Neu laden" holt den aktuellen Stand samt Status; danach wählt man die Taste
erneut, wenn der Wechsel auf dem neuen Stand noch passt. Einen Grund für das Storno trägt man dabei
im Dialog neu ein.

In der Karte „Kommentare" steht die Meldung über dem Feld „Neuer Kommentar", wenn ein anderer den
Kommentar inzwischen bearbeitet hat. Der eigene Text bleibt im Feld. „Neu laden" setzt den
aktuellen Text ins Feld und zeigt den eigenen unter „Deine Eingaben". Hat ein anderer den Kommentar
inzwischen gelöscht, nennt die Meldung, wer ihn wann gelöscht hat. Statt „Neu laden" führt dann
„Zur Übersicht" zur Liste der Angebote, und der eigene Text bleibt im Feld zum Kopieren. Einen neuen
Kommentar schreibt man immer ohne Rückfrage, auch wenn ein anderer das Angebot gerade bearbeitet;
dessen Speichern wird dadurch nicht abgewiesen.

In der Karte „Anlagen" steht die Meldung über der Liste, wenn man eine Anlage löschen will, die
inzwischen gelöscht ist — von einem anderen oder von einem selbst in einem anderen Fenster. Sie
nennt, wer die Anlage wann gelöscht hat, und „Zur Übersicht" führt zur Liste der Angebote. Eine
Anlage ändert sich nie; darum gibt es bei ihr nichts neu zu laden. Eine neue Anlage lädt man immer
ohne Rückfrage hoch.

An einer Rechnung steht die Meldung oben über der Kopfkarte. Wird das Speichern des Entwurfs
abgewiesen, bleiben die eigenen Mengen und Texte in der Maske; „Neu laden" zeigt den aktuellen
Entwurf und die eigenen Werte unter „Deine Eingaben", wie in der Maske eines Angebots. Bei „Rechnung
stellen" zählt allein der Entwurf: Hat ihn ein anderer seit dem Öffnen geändert, wird nichts
gestellt und keine Nummer verbraucht. Man lädt neu, sieht sich den Entwurf an und stellt ihn
erneut. Hat dagegen jemand inzwischen das Angebot, die Einstellungen zur Rechnung, die Eigenen
Angaben oder den Briefbogen geändert, wird trotzdem gestellt — mit dem Stand von diesem Augenblick.
Dasselbe gilt für „Als bezahlt markieren", „Abschreiben" und „Zurück auf gestellt": Nach einer
Abweisung holt „Neu laden" den aktuellen Zustand der Rechnung, danach wählt man die Taste erneut,
wenn sie noch passt.

„Entwurf löschen" löscht nur den Entwurf, den man gesehen hat: Hat ihn ein anderer seit dem Öffnen
geändert, bleibt er stehen, und die Meldung nennt die Änderung. Ist der Entwurf inzwischen gelöscht
— von einem anderen oder von einem selbst in einem anderen Fenster —, wird jede Taste daran
abgewiesen: Speichern, Löschen, „Rechnung stellen" und der Ausgang der Forderung. Die Meldung nennt,
wer ihn wann gelöscht hat, „Zur Übersicht" führt zur Liste der Rechnungen, und die eigenen Werte
stehen weiter in der Maske und unter „Deine Eingaben" zum Kopieren. Ein gelöschter Entwurf zählt
nirgends mehr mit: Seine Mengen sind am Angebot wieder offen.

In der Maske „Nachgetragene Rechnung bearbeiten" steht die Meldung oben im Formular, wenn ein
anderer die Rechnung seit dem Öffnen geändert hat — ihre Angaben, ihren Zustand oder ihr PDF.
Kunde, Nummer, Datum und Beträge bleiben in der Maske stehen. „Neu laden" zeigt den aktuellen Stand
und die eigenen Werte unter „Deine Eingaben"; ein gewähltes PDF steht dann nur noch dort und wird
nicht mitgespeichert, bis man es erneut wählt. In der Ansicht der nachgetragenen Rechnung steht die
Meldung über der Kopfkarte, wenn „Als bezahlt markieren", „Abschreiben" oder „Zurück auf gestellt"
abgewiesen wird; „Neu laden" holt den aktuellen Zustand, danach wählt man die Taste erneut, wenn sie
noch passt.

„Löschen" an einer nachgetragenen Rechnung löscht nur die Rechnung, die man gesehen hat: Hat sie ein
anderer seit dem Öffnen geändert, bleibt sie samt ihrem PDF stehen, und die Meldung nennt die
Änderung. Ist die nachgetragene Rechnung inzwischen gelöscht, wird jede Taste daran abgewiesen —
Speichern, Löschen, das PDF und der Ausgang der Forderung. Die Meldung nennt, wer sie wann gelöscht
hat, „Zur Übersicht" führt zur Liste der Rechnungen, und in der Maske stehen die eigenen Werte weiter
im Formular und unter „Deine Eingaben" zum Kopieren. Eine gelöschte nachgetragene Rechnung zählt
nirgends mehr mit, weder in der Übersicht noch auf der Startseite oder im Jahresabschluss, und ihre
Nummer ist wieder frei.

Unter „Administration" steht die Meldung oben in der Karte „Rechnung", wenn ein anderer die
Einstellungen zur Rechnung seit dem Öffnen gespeichert hat. Muster, nächste Nummer, Steuersatz und
Zahlungsziel bleiben in den Feldern; „Neu laden" zeigt den aktuellen Stand und die eigenen Werte
unter „Deine Eingaben".

Bei den Eigenen Angaben steht die Meldung oben in der Karte „Eigene Angaben", wenn ein anderer
die Angaben seit dem Öffnen gespeichert hat. Die eigenen Werte bleiben in den Feldern; „Neu laden"
zeigt den aktuellen Stand und die eigenen Werte unter „Deine Eingaben". Beim Stellen einer
Rechnung gelten die Eigenen Angaben, wie sie in diesem Augenblick sind; eine Änderung daran weist
das Stellen nicht ab.

In der Karte „Briefpapier" steht die Meldung über dem Briefpapier. Der Briefbogen zählt als ein
Eintrag: Hat ein anderer inzwischen ein Briefpapier hochgeladen, ersetzt oder entfernt, wird auch
„Einstellungen speichern" für Schalter und Ränder abgewiesen, und umgekehrt. Schalter und Ränder
bleiben in den Feldern; „Neu laden" zeigt den aktuellen Briefbogen und die eigenen Werte unter
„Deine Eingaben", nach einem abgewiesenen Hochladen auch den Namen der gewählten Datei. Das eigene
Hochladen weist das eigene Speichern danach nicht ab.

In den Masken „Firma bearbeiten" und „Ansprechpartner bearbeiten" steht die Meldung oben im
Formular, wenn ein anderer die Firma beziehungsweise den Ansprechpartner seit dem Öffnen geändert
hat — seine Angaben oder seinen Stand als aktiv oder stillgelegt. Die eigenen Angaben bleiben in den
Feldern; „Neu laden" zeigt den aktuellen Stand und die eigenen Werte unter „Deine Eingaben". In der
Ansicht einer Firma steht die Meldung über der Kopfkarte, wenn „Stilllegen" oder „Wieder
aktivieren" an der Firma oder an einem Ansprechpartner abgewiesen wird; „Neu laden" holt den
aktuellen Stand, danach wählt man den Eintrag erneut, wenn er noch passt. Firma und
Ansprechpartner sind getrennte Einträge: Wer die Firma ändert, weist das Speichern eines
Ansprechpartners nicht ab. Eine neue Firma und einen neuen Ansprechpartner legt man immer ohne
Rückfrage an.

Im Dialog „Arbeitszeit ändern" steht die Meldung oben im Dialog, wenn ein anderer den Eintrag seit
dem Laden der Monatsliste geändert hat — auch dann, wenn ein C-Level den Eintrag einer anderen
Person korrigiert. Tag, Uhrzeiten, Person und Position bleiben in den Feldern. „Neu laden" zeigt den
aktuellen Stand des Eintrags und die eigenen Werte unter „Deine Eingaben"; die Monatsliste dahinter
lädt dabei mit. Hat der andere den Eintrag inzwischen in einen anderen Monat oder an eine andere
Person gegeben, sagt der Dialog das; dann schließt man ihn und öffnet den Eintrag in der Liste neu,
die eigenen Werte stehen bis dahin unter „Deine Eingaben" zum Kopieren. Eine neue Arbeitszeit erfasst
man mit „Zeit erfassen" immer ohne Rückfrage.

„Löschen" im Menü ⋯ einer Zeile der Arbeitszeit löscht nur den Eintrag, den man in der Liste gesehen
hat: Hat ihn ein anderer seither geändert, bleibt er stehen, und die Meldung über der Liste nennt
die Änderung; „Neu laden" holt den Monat. Ist der Eintrag inzwischen gelöscht — von einem anderen
oder von einem selbst in einem anderen Fenster —, wird jede Aktion daran abgewiesen, Speichern wie
Löschen, und es entsteht nichts neu. Die Meldung nennt, wer ihn wann gelöscht hat, und „Zur
Übersicht" führt zurück zur Liste der Arbeitszeit, die dann frisch geladen ist; im Dialog
„Arbeitszeit ändern" schließt sich der Dialog dabei. Ein gelöschter Eintrag zählt nirgends mehr
mit — weder in der Monatsliste noch in den Stunden am Angebot, im Vorschlag einer Rechnung, auf der
Startseite oder im Jahresabschluss —, und seine Zeit ist für einen neuen Eintrag wieder frei.

In der Maske „Konto bearbeiten" steht die Meldung oben im Formular, wenn ein anderer C-Level das
Konto seit dem Öffnen geändert, stillgelegt oder wieder aktiviert hat. Adresse, Name und Rolle
bleiben in den Feldern; „Neu laden" zeigt den aktuellen Stand und die eigenen Werte unter „Deine
Eingaben". In der Ansicht „Mitarbeitende" steht die Meldung über der Liste, wenn „Stilllegen" oder
„Wieder aktivieren" abgewiesen wird; „Neu laden" holt die Liste, danach wählt man den Eintrag
erneut, wenn er noch passt. Dass sich die Person selbst an- oder abmeldet, weist nichts ab: Das
Konto gilt dadurch nicht als geändert. Ein neues Konto legt man immer ohne Rückfrage an.

**Ergebnis:** Angebot und Rechnung tragen die Änderungen beider, und keine ist unbemerkt verloren
gegangen.

## Eigene Angaben

**Zweck:** Die eigenen Angaben sind der Absender von fb.crm: Name, Anschrift, Kontakt, Steuer- und
Bankangaben. Sie erscheinen auf jeder Rechnung. Ohne sie lässt sich keine Rechnung stellen —
fb.crm verlangt dafür den Namen, Straße und Hausnummer, Postleitzahl, Ort, die Bankverbindung und
die Steuernummer oder die Umsatzsteuer-Identifikationsnummer.

**Einstieg:** In der Schiene im Block „Stammdaten" den Eintrag „Eigene Angaben" wählen. Die
Ansicht zeigt oben das Formular „Eigene Angaben" und darunter die Karte „Briefpapier". Speichern,
Briefpapier hochladen, ersetzen und entfernen kann nur ein C-Level; ReadOnly liest die Angaben nur
und kann den Probedruck holen.

**Angaben speichern:**

1. Die Felder ausfüllen: „Name", „Berufsbezeichnung", „Straße und Hausnummer", „Postleitzahl",
   „Ort", „Land", „E-Mail-Adresse", „Telefon", „Webadresse", „Steuernummer",
   „Umsatzsteuer-Identifikationsnummer", „Kontoinhaber", „IBAN" und „BIC (optional)".
2. „Speichern" wählen.

Kein Feld ist Pflicht beim Speichern: Die Angaben lassen sich nach und nach vervollständigen.
Nach dem Speichern steht „Die Angaben sind gespeichert." über dem Formular. Ist ein Wert zu lang,
steht die Meldung am betroffenen Feld. „Abbrechen" nimmt alle ungespeicherten Änderungen zurück
auf den zuletzt gespeicherten Stand.

**Bankverbindung:** Die Bankverbindung steht in drei Feldern: „Kontoinhaber", „IBAN" und „BIC".
Die BIC darf leer bleiben. Die IBAN darf mit oder ohne Leerzeichen eingetragen werden; fb.crm
prüft sie beim Speichern. Ist sie ungültig, etwa weil die Prüfziffer nicht stimmt, steht die
Meldung am Feld „IBAN", und nichts wird gespeichert. Ebenso wird eine BIC in falscher Form
abgewiesen.

Früher war die Bankverbindung ein einziges Textfeld. Bei der Umstellung hat fb.crm daraus eine
gültige IBAN und BIC übernommen, wo es eine fand, und als Kontoinhaber den eigenen Namen
eingetragen. Solange IBAN oder Kontoinhaber nicht gespeichert sind, steht unter den Feldern der
Hinweis „Bisherige Bankverbindung: … Bitte in die Felder oben übernehmen." mit dem früheren Text.
Die Angaben daraus in die Felder übertragen und speichern; danach verschwindet der Hinweis.

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
Speichern kann hier nur ein C-Level; ReadOnly liest die Einstellungen nur.

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

## Firmen und Ansprechpartner

**Zweck:** Eine Firma ist der Kunde, an den Angebote und Rechnungen gehen. Sie trägt Name,
Anschrift und Steuerangaben, dazu ihre Ansprechpartner. Auf der Seite einer Firma laufen ihre
Angebote zusammen.

**Einstieg:** In der Schiene im Block „Stammdaten" den Eintrag „Firmen" wählen. Die Übersicht
zeigt die Firmen mit „Firma", „Ort" und „Ansprechpartner". Das Feld „Firmen durchsuchen …"
schränkt die Liste ein, der Schalter „auch stillgelegte" zeigt auch stillgelegte Firmen an. Ein
Klick auf den Namen öffnet die Seite der Firma.

**Firma anlegen:**

1. In der Übersicht „Neue Firma" wählen.
2. Die Felder ausfüllen: „Name" und „Land" sind Pflicht, dazu „Straße und Hausnummer",
   „Postleitzahl", „Ort", „Steuernummer" und „Umsatzsteuer-Identifikationsnummer". „Land" ist
   eine Auswahl aus der Länderliste und beim Anlegen mit Deutschland vorbelegt. Ein anderes Land
   lässt sich aus der Liste wählen oder durch Tippen seines Namens suchen, etwa „Österreich".
3. „Anlegen" wählen. fb.crm öffnet die Seite der neuen Firma. Ihre Adresszeile nennt das Land mit
   seinem Namen.

Eine Rechnung an die Firma lässt sich erst stellen, wenn Name, Straße und Hausnummer, Postleitzahl
und Ort eingetragen sind. „Bearbeiten" auf der Seite der Firma öffnet dieselben Felder, „Speichern"
übernimmt die Änderung.

**Ansprechpartner anlegen und bearbeiten:**

1. Auf der Seite der Firma „Neuer Ansprechpartner" wählen.
2. Die Felder ausfüllen: „Vorname", „Nachname", „Rolle", „E-Mail-Adresse", „Telefon Festnetz" und
   „Telefon Mobil".
3. „Anlegen" wählen. Der Ansprechpartner steht danach in der Karte „Ansprechpartner" der Firma.

Ändern lässt sich ein Ansprechpartner über das Menü ⋯ an seinem Eintrag mit „Bearbeiten"; dort
steht auch „Stilllegen". Ein stillgelegter Ansprechpartner steht bei neuen Angeboten nicht mehr
zur Wahl, seine Angaben bleiben erhalten, und „Wieder aktivieren" holt ihn zurück.

**Firma stilllegen:** Auf der Seite der Firma das Menü ⋯ öffnen und „Stilllegen" wählen. fb.crm
fragt vorher nach: Eine stillgelegte Firma steht nicht mehr zur Auswahl, ihre Angebote bleiben
erhalten. Die Firma trägt danach den Zustand „Stillgelegt" und erscheint in der Übersicht nur noch
mit dem Schalter „auch stillgelegte". An einer stillgelegten Firma entsteht kein neues Angebot.
„Wieder aktivieren" im selben Menü macht den Schritt rückgängig.

**Die Seite der Firma:** Oben stehen Name, Anschrift und Zustand mit den Tasten „Bearbeiten",
„Neuer Ansprechpartner" und „Angebot anlegen". Darunter folgen die Karten „Ansprechpartner",
„Stammdaten" mit den Steuerangaben und „Angebote" mit allen Angeboten an diese Firma, jeweils mit
Titel, Datum, Summe und Status.

**Ergebnis:** Die Firma steht in der Übersicht, und ihre Seite zeigt Ansprechpartner, Stammdaten
und Angebote an einer Stelle.

## Angebote

**Zweck:** Ein Angebot hält fest, was einer Firma angeboten wird: einen Titel, eine Beschreibung
und Positionen mit Menge, Einzelpreis und Betrag. Es durchläuft einen Status von „Angelegt" bis
„Abgerechnet", trägt Kommentare und Anlagen und ist die Grundlage jeder Rechnung.

**Einstieg:** Ein neues Angebot entsteht an seiner Firma: auf der Seite der Firma „Angebot
anlegen" wählen. Alle Angebote zeigt die Übersicht in der Schiene unter „Geschäft" und „Angebote",
mit „Titel", „Datum", „Firma", „Status" und „Summe", neueste zuerst. Das Feld „Filter" zeigt nur
die Angebote eines Status oder die stornierten. Ein Klick auf den Titel öffnet das Angebot.

**Angebot anlegen:**

1. Auf der Seite der Firma „Angebot anlegen" wählen.
2. Den „Titel" eintragen — er ist Pflicht und höchstens 200 Zeichen lang.
3. Unter „Ansprechpartner" wahlweise einen der aktiven Ansprechpartner der Firma wählen.
4. Das Kästchen „Internes Projekt" bleibt für ein Angebot an den Kunden leer.
5. „Anlegen" wählen. fb.crm öffnet die Maske „Angebot bearbeiten".

**Positionen erfassen:**

1. In der Maske „Angebotsdatum" und wahlweise eine „Beschreibung" eintragen.
2. In der Karte „Positionen" „Position hinzufügen" wählen.
3. Je Position „Bezeichnung", „Abrechnung" (Aufwand oder Festpreis), „Menge", „Einheit" (Stunde,
   Personentag oder Pauschal), „Einzelpreis (netto)" und „Leistungsjahr" eintragen. Die Pfeile an
   der Position verschieben sie nach oben oder unten, das Papierkorbsymbol löscht sie.
4. Unter den Positionen rechnet „Summe (netto)" beim Tippen mit.
5. „Speichern" wählen. Danach steht „Gespeichert." oben in der Maske, und Beträge und Summe stehen
   so da, wie fb.crm sie gespeichert hat. „Zum Angebot" öffnet die Ansicht des Angebots.

Die Ansicht des Angebots zeigt oben Titel, Angaben und Status, darunter die Beschreibung, die
Karte „Positionen" mit Menge, Einzelpreis, Betrag und der Nettosumme, die Karte „Rechnungen" sowie
die Karten „Anlagen" und „Kommentare". „Bearbeiten" führt zurück in die Maske; Positionen lassen
sich in jedem Status ändern.

**Der Status und sein Weg:** Ein Angebot an den Kunden geht durch die Status „Angelegt",
„Abgegeben", „Bestellt", „Erledigt" und „Abgerechnet". „Status weiter" setzt es eine Stufe weiter,
„Status zurück" eine zurück; an den Enden fehlt die jeweilige Taste. Eine Rechnung lässt sich
schreiben, sobald das Angebot „Bestellt" ist. In „Angelegt", „Abgegeben" und „Bestellt" steht
außerdem „Stornieren": Nach einer Rückfrage, in der sich ein Grund festhalten lässt, trägt das
Angebot den Zustand „Storniert" und zählt weder im Forecast noch unter „Angebote in Arbeit"; es
bleibt mit Positionen, Kommentaren und Anlagen nachlesbar, und „Storno zurücknehmen" stellt es
wieder her.

**Kommentare:** In der Karte „Kommentare" den Text in das Feld „Neuer Kommentar" schreiben und
„Kommentar speichern" wählen. Ein Kommentar hat höchstens 2.000 Zeichen und steht danach oben in
der Liste. Jeder Kommentar nennt über seinem Zeitpunkt den Namen seines Verfassers. Über das Menü
⋯ am Kommentar lässt er sich bearbeiten oder nach einer Rückfrage löschen.

**Anlagen:** In der Karte „Anlagen" „Datei hochladen" wählen und eine Datei auswählen; sie darf
höchstens 25 MB groß sein. Die Anlage steht danach mit Dateiname und Zeitpunkt in der Liste. Das
Augensymbol zeigt ein Bild oder ein PDF in einem Fenster an, das Pfeilsymbol lädt die Datei
herunter, und über das Menü ⋯ lässt sich die Anlage nach einer Rückfrage löschen.

**Ergebnis:** Das Angebot steht mit Titel, Status und seiner Summe in der Übersicht „Angebote" und
auf der Seite seiner Firma.

## Rechnungen

**Zweck:** Eine Rechnung rechnet ab, was aus einem Angebot geleistet wurde — ganz oder in Teilen.
Sie entsteht als Entwurf gegen die Positionen des Angebots, bekommt beim Stellen ihre Nummer aus dem
Nummernkreis und wird damit festgeschrieben. Dazu gehört ein Rechnungsdokument als PDF.

**Einstieg:** An ihrem Angebot: In der Ansicht des Angebots „Rechnung schreiben" wählen. Die Taste
steht, sobald das Angebot „Bestellt" ist und noch etwas offen hat. Alle Rechnungen zeigt die
Übersicht in der Schiene unter „Geschäft" und „Rechnungen", mit „Nummer", „Firma",
„Rechnungsdatum", „Betrag", „Zustand" und „Dokument". Dort führt „Neue Rechnung" ebenfalls zum
Entwurf: Sie zeigt die abrechenbaren Angebote zur Wahl.

**Entwurf anlegen:**

1. „Rechnung schreiben" am Angebot oder „Neue Rechnung" in der Übersicht wählen.
2. Unter „Monat der Arbeitszeit" einen Monat wählen, dessen erfasste Stunden die Mengen vorbelegen,
   oder „ohne Arbeitszeit" — dann übernimmt der Entwurf an jeder Position die offene Menge.
3. „Anlegen" wählen (in der Übersicht das Angebot wählen). fb.crm öffnet den Entwurf.

**Teilabrechnung gegen die Angebotspositionen:** Der Entwurf zeigt in der Karte „Positionen" jede
Position des Angebots mit „Leistung", „Einheit", „Einzelpreis", „Angeboten", „Abgerechnet" und
„Offen". In der Spalte „Jetzt abrechnen" steht, welche Menge diese Rechnung berechnet; eine Menge
0 lässt die Position aus der Rechnung heraus. So lässt sich ein Angebot über mehrere Rechnungen
abrechnen. Eine Menge über dem Offenen ist erlaubt und wird an der Zeile als Hinweis gezeigt.

**Beträge prüfen:**

1. In der Karte „Angaben zur Rechnung" „Rechnungsdatum" und „Leistungszeitraum" prüfen; beide sind
   vorbelegt und Pflicht.
2. Die Mengen in „Jetzt abrechnen" anpassen. „Betrag" je Zeile sowie die Summen für netto, Steuer
   und brutto rechnen beim Tippen mit; der Steuersatz kommt aus den Rechnungseinstellungen.
3. „Speichern" hält den Zwischenstand fest, danach steht „Gespeichert." in der Karte.

Ein Entwurf, aus dem nichts werden soll, verschwindet über das Menü ⋯ mit „Entwurf löschen".

**Rechnung stellen:**

1. „Rechnung stellen" wählen. Die Taste ist gesperrt, solange keine Position eine Menge über 0
   hat.
2. fb.crm speichert ungespeicherte Änderungen und fragt nach, mit dem Bruttobetrag der Rechnung.
3. Die Rückfrage mit „Rechnung stellen" bestätigen.

Fehlen dafür noch eigene Angaben oder Angaben der Firma, nennt fb.crm sie in einer Liste unter
„Für das Stellen fehlen noch Angaben:", und die Rechnung bleibt Entwurf. Beim Stellen bekommt die
Rechnung die nächste Nummer aus dem Nummernkreis der Rechnungseinstellungen; keine Nummer wird
doppelt vergeben. Nummer, Beträge, Steuersatz und Zahlungsziel sind damit festgeschrieben.

**Das Rechnungsdokument:** Die gestellte Rechnung zeigt ihren Beleg mit Nummer, Angaben,
Positionen und Summen. „Herunterladen" gibt das Rechnungsdokument als PDF heraus, auf dem
hinterlegten Briefpapier, sonst in der Gestaltung von fb.crm. In der Übersicht „Rechnungen" lädt
das Symbol in der Spalte „Dokument" dasselbe PDF herunter. Neben „Herunterladen" stehen „Als bezahlt
markieren" und „Abschreiben" für den Ausgang der Forderung; „Zurück auf gestellt" nimmt beides
zurück.

**Eine gestellte Rechnung ändert sich nicht mehr:** Sie lässt sich weder bearbeiten noch löschen.
Der Weg zurück ist ein Storno; eine Taste dafür bietet fb.crm bisher nicht.

**Ergebnis:** Die Rechnung steht mit Nummer, Betrag und Zustand „Gestellt" in der Übersicht
„Rechnungen", in der Karte „Rechnungen" ihres Angebots, und ihr PDF lässt sich herunterladen.

## Startseite

**Zweck:** Die Startseite zeigt den Geschäftsstand auf einen Blick: was in Arbeit ist, was noch
nicht abgerechnet ist, welche Rechnungen offen sind und was im gewählten Zeitraum abgerechnet
wurde. Die Zahlen laufen dort aus Angeboten, Arbeitszeit und Rechnungen zusammen; entstanden sind
sie an diesen Stellen, die Startseite zeigt sie nur.

**Einstieg:** Nach der Anmeldung landet man auf der Startseite „Start" unter der Adresse `/`.

**Geschäftsstand lesen:**

1. Oben einen Zeitraum wählen, einen Monat oder ein ganzes Jahr. Der Zeitraum steht danach in der
   Adresse.
2. Die Kennzahlen lesen: „Angebote in Arbeit", „Noch nicht abgerechnet", „Offene Rechnungen" und
   „Abgerechnet". Unter jeder Kennzahl steht in einer eigenen Karte, woraus sie entstanden ist.
   „Offene Rechnungen" und „Angebote in Arbeit" zeigen den Stand von heute, unabhängig vom
   gewählten Zeitraum; „Abgerechnet" zählt nach Rechnungsdatum, nicht nach Zahlungseingang.
3. Bei einem gewählten Jahr nennt die Karte „Abgerechnet" die Monate des Jahres mit „Rechnungen",
   „Netto", „Brutto" und „Offen". „Zu den Rechnungen" führt zur Übersicht „Rechnungen".
4. Die Karte „Forecast" zeigt je Jahr, was „Zugesagt" und was „Angeboten" ist, mit der „Summe".
   Ein Jahr lässt sich aufklappen und nennt dann die noch offenen Positionen mit ihrem Restbetrag.

**Ergebnis:** Man sieht ohne weiteren Weg, wie das Geschäft im gewählten Zeitraum steht und was
noch zu erwarten ist.

## Arbeitszeit

**Zweck:** Die Arbeitszeit hält fest, wann an welcher Angebotsposition gearbeitet wurde, über alle
Angebote hinweg, nach Monaten. Die erfassten Stunden eines Monats belegen später die Mengen einer
Rechnung vor.

**Einstieg:** In der Schiene unter „Geschäft" „Arbeitszeit" wählen. Die Ansicht zeigt den
laufenden Monat; „Vorheriger Monat" und „Nächster Monat" neben dem Namen des Monats wechseln ihn.

**Eintrag anlegen:**

1. „Zeit erfassen" wählen.
2. „Tag", „von", „bis" und die „Position" des Angebots angeben, an der gearbeitet wurde.
3. „Speichern" wählen.

**Eintrag ändern:** In der Zeile des Eintrags die Zeitspanne wählen; es öffnet sich dieselbe Maske
mit den bisherigen Werten. Löschen steht im Menü ⋯ der Zeile unter „Löschen".

**Ergebnis:** Der Eintrag steht unter seinem Tag mit „Zeit", „Dauer" und „Position"; Tagessumme
und Monatssumme rechnen ihn mit.

**Wer welche Zeiten sieht:** Jeder Eintrag gehört einer Person. Ein Employee sieht und pflegt nur
die eigenen Zeiten. Ein C-Level sieht zunächst die eigenen Zeiten, schaltet oben in der Ansicht auf
eine andere Person oder auf „Alle" um, ändert und löscht jeden Eintrag und wählt beim Erfassen die
Person, für die die Zeit gilt. ReadOnly sieht beim Öffnen „Alle", kann ebenso umschalten und ändert
nichts. Bei „Alle" steht an jedem Eintrag der Name der Person.

**Überschneidungen:** Überschneidungen prüft fb.crm je Person: Dieselbe Zeit für zwei Personen ist
erlaubt, für eine Person nicht. Dann speichert fb.crm den Eintrag nicht und nennt den
kollidierenden Eintrag.

## Jahresabschlüsse

**Zweck:** Ein Jahresabschluss fasst ein Jahr zusammen: Einnahmen, Umsatzsteuer, Umsatz je Kunde,
die Bilanz der Angebote und die Arbeitszeit. Er ist eine Auswertung, keine Buchhaltung.

**Einstieg:** In der Schiene unter „Geschäft" „Jahresabschlüsse" wählen. Die Übersicht nennt je
Jahr „Einnahmen netto", „Rechnungen" und „Annahmequote", das jüngste Jahr zuerst; das laufende
trägt den Hinweis „läuft noch". Ein Jahr erscheint, sobald es eine gestellte Rechnung oder ein
abgegebenes Angebot hat.

**Abschluss eines Jahres ansehen:**

1. In der Übersicht das Jahr wählen.
2. Oben stehen „Einnahmen netto", „Einnahmen brutto" und „Enthaltene Umsatzsteuer". Gezählt wird
   nach Rechnungsdatum, nicht nach Zahlungseingang.
3. Darunter folgen die Karten „Rechnungen", „Umsatzsteuer je Steuersatz" (nur bei mehr als einem
   Steuersatz), „Umsatz je Kunde" mit dem Anteil am Jahresumsatz, „Angebote" mit abgegebenen,
   angenommenen und offenen Angeboten samt Annahmequote, und „Arbeitszeit" mit Kundenarbeit,
   internen Projekten und dem Erlös je Stunde.

Lässt sich eine Kennzahl nicht berechnen, steht ein Strich und daneben der Grund.

**Ergebnis:** Man sieht die Zahlen eines Jahres zusammen auf einer Seite.

## Nachgetragene Rechnungen

**Zweck:** Rechnungen, die außerhalb von fb.crm entstanden sind, etwa vor seiner Einführung,
lassen sich mit ihren Eckdaten nachtragen. So erscheinen sie in der Übersicht, auf der Startseite
und in den Jahresabschlüssen.

**Einstieg:** In der Schiene unter „Geschäft" „Rechnungen" wählen, dort „Rechnung nachtragen".

**Rechnung nachtragen:**

1. Unter „Kunde suchen" die Firma suchen und unter „Kunde" wählen; auch eine stillgelegte Firma
   steht zur Wahl.
2. „Rechnungsnummer", „Rechnungsdatum", „Nettobetrag" und „Bruttobetrag" angeben, die Beträge in
   Euro, so wie auf der Rechnung.
3. Optional unter „Originalrechnung (PDF)" das PDF der Rechnung wählen.
4. „Anlegen" wählen.

**Bearbeiten:** In der Ansicht der nachgetragenen Rechnung „Bearbeiten" wählen, Angaben ändern und
„Speichern" wählen. Eine neue Datei ersetzt das hinterlegte PDF, „Hinterlegtes PDF entfernen"
nimmt es heraus. Anders als eine gestellte Rechnung bleibt eine nachgetragene in jedem Zustand
änderbar und lässt sich über das Menü ⋯ mit „Löschen" entfernen. „Als bezahlt markieren",
„Abschreiben" und „Zurück auf gestellt" stehen wie bei jeder Rechnung bereit.

**Ergebnis:** Die nachgetragene Rechnung steht in der Übersicht „Rechnungen" und ist dort am
Zeichen „nachgetragen" neben ihrem Zustand zu erkennen.

## Mitarbeitende und Rollen

**Zweck:** fb.crm wird von einer Firma mit mehreren Personen genutzt. Jede Person meldet sich mit
einem eigenen Konto an, und die Rolle des Kontos bestimmt, was sie darf. Konten verwaltet ein
C-Level in der Ansicht „Mitarbeitende".

**Einstieg:** Im Fuß der Schiene „Mitarbeitende" wählen. Die Ansicht steht nur einem C-Level
offen. Sie listet jedes Konto mit Namen, E-Mail-Adresse, Rolle und Zustand, „Aktiv" oder
„Stillgelegt".

**Konto anlegen:**

1. „Neues Konto" wählen.
2. „E-Mail-Adresse" und „Name" angeben und unter „Rolle" C-Level, Employee oder ReadOnly wählen.
   Alle drei Angaben sind Pflicht; eine Rolle ist nicht vorgewählt.
3. „Anlegen" wählen.

Die neue Person erhält eine Begrüßungsmail mit der Adresse von fb.crm und meldet sich dort mit
ihrer Adresse und dem Code aus der Mail an, wie unter „Erste Schritte" beschrieben. Ist der
Mailversand nicht eingerichtet, sagt die Maske nach dem Anlegen, dass keine Begrüßungsmail
hinausging; dann gibt man der Person selbst Bescheid. Eine Adresse, die schon ein anderes Konto
trägt, auch ein stillgelegtes, weist die Maske am Feld „E-Mail-Adresse" ab, gleich in welcher
Schreibweise.

**Konto ändern:** In der Zeile des Kontos über das Menü ⋯ „Bearbeiten" wählen, Adresse, Namen oder
Rolle ändern und „Speichern" wählen. Die Änderung wirkt sofort, auch für eine Person, die gerade
angemeldet ist. Nach einer neuen Adresse ist die Person abgemeldet und meldet sich mit der neuen
Adresse wieder an.

**Stilllegen:** Wer ausscheidet, dessen Konto wird stillgelegt, nicht gelöscht. Über das Menü ⋯
„Stilllegen" wählen und die Rückfrage bestätigen. Die Person ist sofort abgemeldet und kann sich
nicht mehr anmelden; ihre Kommentare am Angebot bleiben mit ihrem Namen stehen.

**Wieder aktivieren:** Über das Menü ⋯ eines stillgelegten Kontos „Wieder aktivieren" wählen. Die
Person meldet sich danach wieder mit ihrer Adresse an.

**Was jede Rolle darf:**

- C-Level darf alles: Kunden, Angebote, Rechnungen und Arbeitszeit bearbeiten, die eigenen Angaben
  und die Rechnungseinstellungen ändern und in „Mitarbeitende" Konten verwalten.
- Employee darf alles Fachliche: Kunden, Angebote und Rechnungen anlegen und ändern, Rechnungen
  stellen und die Arbeitszeit bedienen. „Mitarbeitende", „Eigene Angaben" und „Administration"
  stehen einem Employee nicht offen.
- ReadOnly liest alles, auch die eigenen Angaben und die Rechnungseinstellungen, und ändert nichts.
  „Mitarbeitende" steht ReadOnly nicht offen. Bei Firmen, Angeboten, Rechnungen und in der
  Arbeitszeit fehlen ReadOnly die Tasten zum Anlegen, Ändern, Stellen, Stornieren, Hochladen,
  Kommentieren und „Zeit erfassen"; Anlagen und Rechnungen lassen sich weiter ansehen und
  herunterladen.

**Der letzte C-Level:** Es bleibt immer mindestens ein aktiver C-Level. Das letzte aktive
C-Level-Konto lässt sich weder stilllegen noch auf eine andere Rolle setzen; fb.crm sagt dann „Es
muss mindestens ein aktiver C-Level bleiben." Das gilt auch für das eigene Konto: Ein C-Level darf
sich selbst stilllegen oder herabstufen, solange ein anderer aktiver C-Level bleibt.

**Ergebnis:** Jede Person der Firma hat ein eigenes Konto mit der passenden Rolle, und wer
ausgeschieden ist, kommt nicht mehr hinein.
