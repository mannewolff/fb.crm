import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { Dispatch, FormEvent, ReactNode, SetStateAction } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ansprechpartnerAendern, ansprechpartnerAnlegen, firmaLesen } from '../api/firmen';
import type { Ansprechpartner } from '../api/firmen';
import type { FieldErrors } from '../api/client';
import EigeneEingaben from '../components/EigeneEingaben';
import type { EigeneEingabe } from '../components/EigeneEingaben';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import Karte from '../components/Karte';
import Konfliktmeldung from '../components/Konfliktmeldung';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, istBearbeitungskonflikt, nichtGefunden } from '../lib/apifehler';
import { istEmailForm } from '../lib/emailform';
import { meldungAm } from '../lib/feldmeldung';
import { kennungAus } from '../lib/kennung';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Maske des Ansprechpartners — Anlegen unter `/firmen/:id/ansprechpartner/neu`, Aendern unter
 * `/firmen/:id/ansprechpartner/:ansprechpartnerId/bearbeiten` (Kriterien 9, 11, 12).
 *
 * Eine Komponente fuer beide Wege, aus demselben Grund wie bei der Firma-Maske: Felder, Meldungen
 * und Verhalten sind dieselben, und nur Herkunft der Werte und Ziel des Speicherns unterscheiden
 * sich. Auch der Rahmen ist derselbe — eine {@link Karte} auf der Buehne, keine `AuthCard` (E19).
 *
 * <b>Kein Feld fuer die Firma</b> (Kriterium 12, E7). Die Firma steht im Pfad, und Umhaengen gibt
 * es nicht. Sie erscheint als Notiz im Kopf, damit sichtbar ist, unter wem der Ansprechpartner
 * entsteht — als Angabe, nicht als Eingabe.
 *
 * Auch beim Anlegen liest die Maske die Firma: Ohne sie waere der Kopf namenlos, und beim Aendern
 * kommen die gespeicherten Werte ohnehin von dort — Ansprechpartner haben keinen eigenen Leseweg,
 * sie stehen eingebettet in der Antwort ihrer Firma (E7).
 *
 * Die Pruefung der E-Mail-Form ist Nutzerfuehrung, nicht die Entscheidung (E8): Sie spart den
 * Weg zum Server bei einem offensichtlichen Vertipper, aber die Quelle der Wahrheit bleibt der
 * Server, dessen Meldungen hier genauso am Feld landen.
 *
 * <b>Gespeichert wird auf dem gelesenen Stand</b> (#411, Kriterien 1 bis 4; Plan #415, E2) — wie
 * in der Firma-Maske: Die Maske fuehrt die Version des Ansprechpartners mit und legt sie beim
 * Speichern vor. Bei einer Abweisung bleiben die Eingaben stehen, die {@link Konfliktmeldung} sagt,
 * wer wann geaendert hat, und „Neu laden" holt den Stand des Servers ins Formular, waehrend die
 * eigenen Werte daneben in {@link EigeneEingaben} stehen, bis „Verwerfen" gewaehlt wird.
 */

const NACHNAME_FEHLT = 'Bitte einen Nachnamen angeben.';
const EMAIL_FORM = 'Bitte eine E-Mail-Adresse in der Form name@beispiel.de angeben.';
const FIRMA_FEHLT = 'Diese Firma gibt es nicht.';
const PARTNER_FEHLT = 'Diesen Ansprechpartner gibt es nicht.';
const AUSFALL_LESEN = 'Die Firma ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Der Ansprechpartner wurde nicht gespeichert. Bitte später erneut versuchen.';
/** Wo „Neu laden" nach einer Abweisung scheitert. */
const AUSFALL_NEU_LADEN = 'Der aktuelle Stand ist gerade nicht zu erreichen. Bitte erneut versuchen.';

/** Die sechs Felder der Maske, jedes als Zeichenkette — leer heisst „keine Angabe". */
interface Werte {
  readonly vorname: string;
  readonly nachname: string;
  readonly rolle: string;
  readonly email: string;
  readonly telefonFestnetz: string;
  readonly telefonMobil: string;
}

const NEUE_WERTE: Werte = {
  vorname: '',
  nachname: '',
  rolle: '',
  email: '',
  telefonFestnetz: '',
  telefonMobil: '',
};

/** Was die Maske gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'bereit'; readonly firmaId: number; readonly firmaName: string }
  | { readonly art: 'fehlt'; readonly meldung: string };

/** Eine fehlende Angabe wird zum leeren Feld — nie zu einem Platzhalter im Eingabefeld. */
function alsText(wert: string | null): string {
  return wert ?? '';
}

/** Die Werte der Maske aus dem Stand des Servers. */
function werteAus(partner: Ansprechpartner): Werte {
  return {
    vorname: alsText(partner.vorname),
    nachname: partner.nachname,
    rolle: alsText(partner.rolle),
    email: alsText(partner.email),
    telefonFestnetz: alsText(partner.telefonFestnetz),
    telefonMobil: alsText(partner.telefonMobil),
  };
}

/**
 * Was gerade in der Maske steht, als eigene Eingaben zum Kopieren (#411, Kriterium 3) — beschriftet
 * wie die Felder, damit sich jeder Wert seinem Feld zuordnen laesst.
 */
function eigeneEingabenAus(werte: Werte): readonly EigeneEingabe[] {
  return [
    { bezeichnung: 'Vorname', wert: werte.vorname },
    { bezeichnung: 'Nachname', wert: werte.nachname },
    { bezeichnung: 'Rolle', wert: werte.rolle },
    { bezeichnung: 'E-Mail-Adresse', wert: werte.email },
    { bezeichnung: 'Telefon Festnetz', wert: werte.telefonFestnetz },
    { bezeichnung: 'Telefon Mobil', wert: werte.telefonMobil },
  ];
}

/**
 * Eine optionale Angabe auf dem Weg zum Server.
 *
 * Der Leerstring waere dort eine Angabe aus null Zeichen; `null` ist „nicht hinterlegt". Nur das
 * zweite laesst die Detailansicht die Angabe weg (Kriterium 5).
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
  readonly art?: 'text' | 'email' | 'tel';
}

/** Ein Feld der Maske samt seiner Meldung — sechsmal derselbe Bau, einmal aufgeschrieben. */
function Eingabe({
  label,
  feld,
  werte,
  setzeWerte,
  meldung,
  pflicht = false,
  art = 'text',
}: EingabeProps) {
  return (
    <TextField
      label={label}
      type={art}
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
function nichtBereit(stand: Exclude<Stand, { readonly art: 'bereit' }>): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Der Ansprechpartner wird geladen …
      </Typography>
    );
  }
  return <Alert severity="error">{stand.meldung}</Alert>;
}

export default function AnsprechpartnerMaske() {
  const { id, ansprechpartnerId } = useParams();
  const aendern = ansprechpartnerId !== undefined;
  const firmaKennung = kennungAus(id);
  const partnerKennung = kennungAus(ansprechpartnerId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [werte, setzeWerte] = useState<Werte>(NEUE_WERTE);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Der Stand, den das naechste Speichern vorlegt: gelesen, oder aus „Neu laden".
  const [version, setzeVersion] = useState(0);
  // Die Abweisung eines Speicherns auf altem Stand, ungeprueft — die Konfliktmeldung liest sie selbst.
  const [abweisung, setzeAbweisung] = useState<unknown>(null);
  // Die eigenen Werte nach „Neu laden", bis „Verwerfen" gewaehlt wird (#411, Kriterium 3).
  const [eigene, setzeEigene] = useState<readonly EigeneEingabe[] | null>(null);

  useEffect(() => {
    // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
    if (firmaKennung === null) {
      setzeStand({ art: 'fehlt', meldung: FIRMA_FEHLT });
      return;
    }
    if (aendern && partnerKennung === null) {
      setzeStand({ art: 'fehlt', meldung: PARTNER_FEHLT });
      return;
    }
    firmaLesen(firmaKennung)
      .then((firma) => {
        if (partnerKennung === null) {
          setzeStand({ art: 'bereit', firmaId: firma.id, firmaName: firma.name });
          return;
        }
        const partner = firma.ansprechpartner.find((einer) => einer.id === partnerKennung);
        if (partner === undefined) {
          setzeStand({ art: 'fehlt', meldung: PARTNER_FEHLT });
          return;
        }
        setzeWerte(werteAus(partner));
        setzeVersion(partner.version);
        setzeStand({ art: 'bereit', firmaId: firma.id, firmaName: firma.name });
      })
      .catch((ursache: unknown) => {
        setzeStand({
          art: 'fehlt',
          meldung: nichtGefunden(ursache) ? FIRMA_FEHLT : AUSFALL_LESEN,
        });
      });
  }, [aendern, firmaKennung, partnerKennung]);

  /**
   * `firmaId` kommt aus der gelesenen Firma und nicht aus dem Pfad: Die Maske entsteht erst, wenn
   * die Firma gelesen ist — damit gibt es hier keine Kennung, die keine sein koennte.
   */
  const absenden = async (ereignis: FormEvent<HTMLFormElement>, firmaId: number) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeAbweisung(null);
    setzeFeldFehler({});
    const eigene: Record<string, readonly string[]> = {};
    if (werte.nachname.trim() === '') {
      eigene.nachname = [NACHNAME_FEHLT];
    }
    if (!istEmailForm(werte.email)) {
      eigene.email = [EMAIL_FORM];
    }
    setzeEigeneFehler(eigene);
    if (Object.keys(eigene).length > 0) {
      return;
    }
    setzeLaeuft(true);
    const eingabe = {
      vorname: oderNull(werte.vorname),
      nachname: werte.nachname.trim(),
      rolle: oderNull(werte.rolle),
      email: oderNull(werte.email),
      telefonFestnetz: oderNull(werte.telefonFestnetz),
      telefonMobil: oderNull(werte.telefonMobil),
    };
    try {
      if (partnerKennung === null) {
        await ansprechpartnerAnlegen(firmaId, eingabe);
      } else {
        await ansprechpartnerAendern(firmaId, partnerKennung, version, eingabe);
      }
      navigate(`/firmen/${String(firmaId)}`, { replace: true });
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
   * (#411, Kriterium 3; Plan #415, E12). Gelesen wird ueber die Firma — einen eigenen Leseweg hat
   * der Ansprechpartner nicht (E7).
   */
  const neuLaden = async (firmaId: number, partnerId: number) => {
    const meine = eigeneEingabenAus(werte);
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      const firma = await firmaLesen(firmaId);
      const partner = firma.ansprechpartner.find((einer) => einer.id === partnerId);
      if (partner === undefined) {
        setzeFehler(PARTNER_FEHLT);
        return;
      }
      setzeWerte(werteAus(partner));
      setzeVersion(partner.version);
      setzeEigene(meine);
      setzeAbweisung(null);
      setzeEigeneFehler({});
      setzeFeldFehler({});
    } catch {
      setzeFehler(AUSFALL_NEU_LADEN);
    } finally {
      setzeLaeuft(false);
    }
  };

  const titel = aendern ? 'Ansprechpartner bearbeiten' : 'Neuer Ansprechpartner';
  useKopfPfad(ZU_FIRMEN, titel);
  /** Die eigene Meldung geht vor: Sie beschreibt die Eingabe, die gar nicht erst abging. */
  const meldung = (feld: string) =>
    meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

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
      <Karte
        titel={titel}
        notiz={stand.art === 'bereit' ? stand.firmaName : undefined}
      >
        {stand.art === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              void absenden(ereignis, stand.firmaId);
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            {partnerKennung === null ? null : (
              <Konfliktmeldung
                ursache={abweisung}
                onNeuLaden={() => {
                  void neuLaden(stand.firmaId, partnerKennung);
                }}
                uebersicht={`/firmen/${String(stand.firmaId)}`}
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
              label="Vorname"
              feld="vorname"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldung('vorname')}
            />
            <Eingabe
              label="Nachname"
              feld="nachname"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldung('nachname')}
              pflicht
            />
            <Eingabe
              label="Rolle"
              feld="rolle"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldung('rolle')}
            />
            <Eingabe
              label="E-Mail-Adresse"
              feld="email"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldung('email')}
              art="email"
            />
            <Eingabe
              label="Telefon Festnetz"
              feld="telefonFestnetz"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldung('telefonFestnetz')}
              art="tel"
            />
            <Eingabe
              label="Telefon Mobil"
              feld="telefonMobil"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldung('telefonMobil')}
              art="tel"
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft}>{aendern ? 'Speichern' : 'Anlegen'}</KupferTaste>
              <WeicheTaste to={`/firmen/${String(stand.firmaId)}`}>Abbrechen</WeicheTaste>
            </Box>
          </Box>
        ) : (
          nichtBereit(stand)
        )}
      </Karte>
    </Box>
  );
}
