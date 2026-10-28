import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { Dispatch, FormEvent, ReactNode, SetStateAction } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { firmaAendern, firmaAnlegen, firmaLesen } from '../api/firmen';
import type { Firma } from '../api/firmen';
import { laenderLaden } from '../api/laender';
import type { Land } from '../api/laender';
import type { FieldErrors } from '../api/client';
import EigeneEingaben from '../components/EigeneEingaben';
import type { EigeneEingabe } from '../components/EigeneEingaben';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import AnschriftFelder from '../components/AnschriftFelder';
import Karte from '../components/Karte';
import Konfliktmeldung from '../components/Konfliktmeldung';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, istBearbeitungskonflikt, nichtGefunden } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { kennungAus } from '../lib/kennung';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Maske der Firma — Anlegen unter `/firmen/neu`, Aendern unter `/firmen/:id/bearbeiten`
 * (Kriterien 4, 7, 8).
 *
 * Eine Komponente fuer beide Wege: Die Felder, ihre Meldungen und ihr Verhalten sind dieselben,
 * und der einzige Unterschied ist, woher die Werte kommen und wohin das Speichern fuehrt. Zwei
 * Abschriften liefen beim ersten Nachziehen auseinander.
 *
 * Die Maske steht als {@link Karte} auf der Buehne und nicht in der Karte der Auth-Seiten (E19,
 * Plan-Review Fund 5): Die bringt ein eigenes `main` und die Marke mit — innerhalb des
 * angemeldeten Rahmens waere das ein zweites `main` in derselben Seite.
 *
 * Eine eigene Ruecknahme braucht es nicht: „ohne Speichern verlassen" ist der Weg zurueck (E10).
 *
 * Das Formular traegt `noValidate`. Die Pflichtangabe steht als `required` am Feld — fuer den
 * Screenreader und fuer das Sternchen —, die Meldung dazu schreibt aber diese Maske, damit sie
 * in derselben Sprache und an derselben Stelle steht wie die Meldungen des Servers.
 *
 * <b>Gespeichert wird auf dem gelesenen Stand</b> (#411, Kriterien 1 bis 4; Plan #415, E2). Die
 * Maske fuehrt die Version der Firma mit und legt sie beim Speichern vor. Hat inzwischen jemand
 * anderes gespeichert — oder dieselbe Person in einem anderen Fenster —, weist der Server ab, und
 * die {@link Konfliktmeldung} sagt, wer wann geaendert hat. Die eigenen Eingaben bleiben dabei im
 * Formular stehen. „Neu laden" holt den Stand des Servers samt neuer Version ins Formular und
 * stellt die eigenen Werte daneben in {@link EigeneEingaben}, bis „Verwerfen" gewaehlt wird.
 */

/**
 * Beim Anlegen vorbelegt; beim Aendern gilt der gespeicherte Wert (Kriterium 4). Das Land ist ein
 * Kuerzel der Laenderliste (Plan #461, E11) und wird aus ihr gewaehlt (Issue #463).
 */
const LAND_VORGABE = 'DE';

const NAME_FEHLT = 'Bitte einen Namen angeben.';
const NICHT_GEFUNDEN = 'Diese Firma gibt es nicht.';
const AUSFALL_LESEN = 'Die Firma ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Firma wurde nicht gespeichert. Bitte später erneut versuchen.';
/** Wo „Neu laden" nach einer Abweisung scheitert. */
const AUSFALL_NEU_LADEN = 'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte erneut versuchen.';

/** Wohin die Konfliktmeldung fuehrt, wenn es die Firma nicht mehr gibt. */
const UEBERSICHT = '/firmen';

/** Die sieben Felder der Maske, jedes als Zeichenkette — leer heisst „keine Angabe". */
interface Werte {
  readonly name: string;
  readonly strasse: string;
  readonly plz: string;
  readonly ort: string;
  readonly land: string;
  readonly steuernummer: string;
  readonly umsatzsteuerId: string;
}

const NEUE_WERTE: Werte = {
  name: '',
  strasse: '',
  plz: '',
  ort: '',
  land: LAND_VORGABE,
  steuernummer: '',
  umsatzsteuerId: '',
};

/** Was die Maske gerade weiss. */
type Stand = 'laedt' | 'bereit' | 'unbekannt' | 'ausfall';

/**
 * Die Laenderliste der Maske: noch unterwegs, geladen oder nicht zu haben.
 *
 * Ist sie nicht zu haben, fragt die Maske das Land als Kuerzel im Textfeld ab, statt das Anlegen
 * ganz zu sperren — der Server prueft das Kuerzel ohnehin gegen dieselbe Liste.
 */
type Laender = 'laedt' | readonly Land[] | 'ausfall';

/** Eine fehlende Angabe wird zum leeren Feld — nie zu einem Platzhalter im Eingabefeld. */
function alsText(wert: string | null): string {
  return wert ?? '';
}

/** Die Werte der Maske aus dem Stand des Servers. */
function werteAus(firma: Firma): Werte {
  return {
    name: firma.name,
    strasse: alsText(firma.strasse),
    plz: alsText(firma.plz),
    ort: alsText(firma.ort),
    land: alsText(firma.land),
    steuernummer: alsText(firma.steuernummer),
    umsatzsteuerId: alsText(firma.umsatzsteuerId),
  };
}

/**
 * Was gerade in der Maske steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3) — beschriftet
 * wie die Felder, damit sich jeder Wert seinem Feld zuordnen laesst.
 */
function eigeneEingabenAus(werte: Werte): readonly EigeneEingabe[] {
  return [
    { bezeichnung: 'Name', wert: werte.name },
    { bezeichnung: 'Straße und Hausnummer', wert: werte.strasse },
    { bezeichnung: 'Postleitzahl', wert: werte.plz },
    { bezeichnung: 'Ort', wert: werte.ort },
    { bezeichnung: 'Land', wert: werte.land },
    { bezeichnung: 'Steuernummer', wert: werte.steuernummer },
    { bezeichnung: 'Umsatzsteuer-Identifikationsnummer', wert: werte.umsatzsteuerId },
  ];
}

/**
 * Eine optionale Angabe auf dem Weg zum Server.
 *
 * Der Leerstring waere dort eine Angabe, die aus null Zeichen besteht; `null` ist „nicht
 * hinterlegt". Nur das zweite laesst die Detailansicht das Feldpaar weglassen (Kriterium 5).
 */
function oderNull(wert: string): string | null {
  const sauber = wert.trim();
  return sauber === '' ? null : sauber;
}

interface EingabeProps {
  readonly label: string;
  readonly feld: keyof Werte;
  readonly werte: Werte;
  readonly setzeWerte: Dispatch<SetStateAction<Werte>>;
  readonly meldung?: string;
  readonly pflicht?: boolean;
}

/** Ein Feld der Maske samt seiner Meldung — siebenmal derselbe Bau, einmal aufgeschrieben. */
function Eingabe({ label, feld, werte, setzeWerte, meldung, pflicht = false }: EingabeProps) {
  return (
    <TextField
      label={label}
      value={werte[feld]}
      onChange={(ereignis) => {
        setzeWerte((alt) => ({ ...alt, [feld]: ereignis.target.value }));
      }}
      error={meldung !== undefined}
      helperText={meldung}
      required={pflicht}
      fullWidth
    />
  );
}

/** Ueber jeder Firmen-Ansicht steht die Uebersicht (E6). */
const ZU_FIRMEN: readonly PfadVerweis[] = [{ titel: 'Firmen', ziel: '/firmen' }];

/** Was die Karte zeigt, solange das Formular nicht bereitsteht: Ladehinweis oder Meldung. */
function nichtBereit(stand: Exclude<Stand, 'bereit'>): ReactNode {
  if (stand === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Die Firma wird geladen …
      </Typography>
    );
  }
  return <Alert severity="error">{stand === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL_LESEN}</Alert>;
}

export default function FirmaMaske() {
  const { id } = useParams();
  const aendern = id !== undefined;
  const kennung = kennungAus(id);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>(aendern ? 'laedt' : 'bereit');
  const [werte, setzeWerte] = useState<Werte>(NEUE_WERTE);
  const [nameFehlt, setzeNameFehlt] = useState(false);
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Der Stand, den das naechste Speichern vorlegt: gelesen, oder aus „Neu laden".
  const [version, setzeVersion] = useState(0);
  // Die Abweisung eines Speicherns auf altem Stand, ungeprueft — die Konfliktmeldung liest sie selbst.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);
  const [laender, setzeLaender] = useState<Laender>('laedt');

  useEffect(() => {
    laenderLaden()
      .then(setzeLaender)
      .catch(() => {
        setzeLaender('ausfall');
      });
  }, []);

  useEffect(() => {
    if (!aendern) {
      return;
    }
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand('unbekannt');
      return;
    }
    firmaLesen(kennung)
      .then((firma) => {
        setzeWerte(werteAus(firma));
        setzeVersion(firma.version);
        setzeStand('bereit');
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? 'unbekannt' : 'ausfall');
      });
  }, [aendern, kennung]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeAbweisung(null);
    setzeFeldFehler({});
    const fehltName = werte.name.trim() === '';
    setzeNameFehlt(fehltName);
    if (fehltName) {
      return;
    }
    setzeLaeuft(true);
    const eingabe = {
      name: werte.name.trim(),
      strasse: oderNull(werte.strasse),
      plz: oderNull(werte.plz),
      ort: oderNull(werte.ort),
      land: oderNull(werte.land),
      steuernummer: oderNull(werte.steuernummer),
      umsatzsteuerId: oderNull(werte.umsatzsteuerId),
    };
    try {
      if (kennung === null) {
        const angelegt = await firmaAnlegen(eingabe);
        navigate(`/firmen/${String(angelegt.id)}`, { replace: true });
      } else {
        await firmaAendern(kennung, version, eingabe);
        navigate(`/firmen/${String(kennung)}`, { replace: true });
      }
    } catch (ursache) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (istBearbeitungskonflikt(ursache)) {
        // Die Eingaben bleiben stehen; den Weg weiter zeigt die Konfliktmeldung (Kriterium 3).
        setzeAbweisung(ursache);
      } else if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    }
  };

  /**
   * „Neu laden" nach einer Abweisung: der Stand des Servers ins Formular, die eigenen Werte daneben
   * (#411, Kriterium 3; Plan #415, E12).
   *
   * Die eigenen Werte werden genommen, wie sie in diesem Augenblick in der Maske stehen — auch was
   * nach der Abweisung noch getippt wurde, soll nicht verloren gehen.
   */
  const neuLaden = async (id: number) => {
    const meine = eigeneEingabenAus(werte);
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      const firma = await firmaLesen(id);
      setzeWerte(werteAus(firma));
      setzeVersion(firma.version);
      setzeEigene(meine);
      setzeAbweisung(null);
      setzeNameFehlt(false);
      setzeFeldFehler({});
    } catch {
      setzeFehler(AUSFALL_NEU_LADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  const titel = aendern ? 'Firma bearbeiten' : 'Neue Firma';
  useKopfPfad(ZU_FIRMEN, titel);
  const zurueck = kennung === null ? '/firmen' : `/firmen/${String(kennung)}`;
  const nameMeldung = nameFehlt ? NAME_FEHLT : meldungAm(feldFehler, 'name');
  // Das Formular steht erst mit der Laenderliste, damit das Land nicht vom Textfeld zur Auswahl
  // springt, waehrend schon jemand tippt.
  const sichtbar: Stand = stand === 'bereit' && laender === 'laedt' ? 'laedt' : stand;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: ABSTAND_BUEHNE,
      }}
    >
      <Karte titel={titel}>
        {sichtbar === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              void absenden(ereignis);
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            {kennung === null ? null : (
              <Konfliktmeldung
                ursache={abweisung}
                onNeuLaden={() => {
                  void neuLaden(kennung);
                }}
                uebersicht={UEBERSICHT}
              />
            )}
            {eigene === null ? null : (
              <EigeneEingaben
                werte={eigene}
                onVerwerfen={() => {
                  setzeEigene(null);
                }}
              />
            )}
            <Eingabe
              label="Name"
              feld="name"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={nameMeldung}
              pflicht
            />
            <AnschriftFelder
              werte={werte}
              setzeWerte={setzeWerte}
              feldFehler={feldFehler}
              laender={typeof laender === 'string' ? undefined : laender}
            />
            <Eingabe
              label="Steuernummer"
              feld="steuernummer"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'steuernummer')}
            />
            <Eingabe
              label="Umsatzsteuer-Identifikationsnummer"
              feld="umsatzsteuerId"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'umsatzsteuerId')}
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft}>{aendern ? 'Speichern' : 'Anlegen'}</KupferTaste>
              <WeicheTaste to={zurueck}>Abbrechen</WeicheTaste>
            </Box>
          </Box>
        ) : (
          nichtBereit(sichtbar)
        )}
      </Karte>
    </Box>
  );
}
