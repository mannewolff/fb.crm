import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';

import { firmaLesen, firmenUebersicht } from '../api/firmen';
import type { Ansprechpartner, FirmaZeile } from '../api/firmen';
import type { FieldErrors } from '../api/client';
import { vorgangAnlegen } from '../api/vorgaenge';
import KupferTaste from '../components/KupferTaste';
import Platte from '../components/Platte';
import { feldMeldungen } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { namensZug } from '../lib/namenszug';

/**
 * Die Maske des Vorgangs — Anlegen unter `/vorgaenge/neu` (Kriterien 5, 6, 7, 26).
 *
 * Rahmen und Aufbau folgen {@link FirmaMaske}: eine {@link Platte} auf der Buehne und nicht die
 * Karte der Auth-Seiten (E19) — die bringt ein eigenes `main` und die Marke mit, und innerhalb des
 * angemeldeten Rahmens waere das ein zweites `main` in derselben Seite. Eine eigene Ruecknahme
 * braucht es nicht: „ohne Speichern verlassen" ist der Weg zurueck (E10).
 *
 * <b>Kein eigener Leseweg fuer die Auswahllisten</b> (E18). Die Firmen kommen aus
 * `GET /api/firmen` — ohne Schalter liefert der Weg nur aktive —, die Ansprechpartner aus
 * `GET /api/firmen/{id}`, dort samt `aktiv`, weshalb diese Maske sie filtert. Ein dritter Weg auf
 * dieselben Daten waere eine zweite Wahrheit. Die Schranke liegt ohnehin nicht hier, sondern
 * serverseitig (E19): Die Oberflaeche fuehrt, sie sperrt nicht.
 *
 * Beide Auswahllisten sind <b>native</b> `select`-Elemente. Sie tragen die Bedienung des jeweiligen
 * Systems mit — Tastatur, Sprachsteuerung, Screenreader —, statt sie nachzubilden (Kriterium 26).
 *
 * Das Formular traegt `noValidate`. Die Pflichtangabe steht als `required` am Feld — fuer den
 * Screenreader und fuer das Sternchen —, die Meldung dazu schreibt aber diese Maske, damit sie in
 * derselben Sprache und an derselben Stelle steht wie die Meldungen des Servers.
 */

/** Der Wert, mit dem „nichts gewaehlt" im `select` steht. */
const KEINE_WAHL = '';

const TITEL_FEHLT = 'Bitte einen Titel angeben.';
const FIRMA_FEHLT = 'Bitte eine Firma wählen.';
const OHNE_AKTIVE_FIRMA = 'Es ist keine aktive Firma vorhanden.';
const AUSFALL_FIRMEN = 'Die Firmen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_PARTNER =
  'Die Ansprechpartner sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Der Vorgang wurde nicht gespeichert. Bitte später erneut versuchen.';

/** Was die Maske ueber die Firmen weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'bereit'; readonly firmen: readonly FirmaZeile[] }
  | { readonly art: 'ausfall' };

/** Was die Maske ueber die Ansprechpartner der gewaehlten Firma weiss. */
type PartnerStand =
  | { readonly art: 'ohne' }
  | { readonly art: 'bereit'; readonly partner: readonly Ansprechpartner[] }
  | { readonly art: 'ausfall' };

const OHNE_PARTNER: PartnerStand = { art: 'ohne' };

/** Holt die Firmen und macht auch aus dem Fehlschlag einen Stand. */
async function firmenLaden(): Promise<Stand> {
  try {
    // Ohne Schalter: nur aktive Firmen (E18, Kriterium 6).
    const uebersicht = await firmenUebersicht('', false);
    return { art: 'bereit', firmen: uebersicht.firmen };
  } catch {
    // Jeder Grund fuehrt zur selben Meldung; welcher es war, hilft dem Benutzer nicht.
    return { art: 'ausfall' };
  }
}

/** Holt die aktiven Ansprechpartner einer Firma und macht auch aus dem Fehlschlag einen Stand. */
async function partnerLaden(firmaId: number): Promise<PartnerStand> {
  try {
    const firma = await firmaLesen(firmaId);
    return { art: 'bereit', partner: firma.ansprechpartner.filter((einer) => einer.aktiv) };
  } catch {
    return { art: 'ausfall' };
  }
}

/** Die Eintraege einer Auswahlliste, samt dem Eintrag fuer „nichts gewaehlt". */
interface WahlProps {
  readonly label: string;
  readonly leerEintrag: string;
  readonly wert: string;
  readonly setzeWert: (wert: string) => void;
  readonly eintraege: readonly { readonly id: number; readonly aufschrift: string }[];
  readonly meldung?: ReactNode;
  /** Rot und als fehlerhaft angesagt. Ein Hinweis (Kriterium 7) ist kein Fehler am Feld. */
  readonly fehlerhaft?: boolean;
  readonly pflicht?: boolean;
}

/**
 * Eine Auswahlliste der Maske samt ihrer Meldung.
 *
 * `shrink` steht fest: Ein `select` zeigt immer einen Eintrag, also gibt es keinen Stand, in dem
 * die Beschriftung noch im Feld liegen duerfte.
 */
function Wahl({
  label,
  leerEintrag,
  wert,
  setzeWert,
  eintraege,
  meldung,
  fehlerhaft = false,
  pflicht = false,
}: WahlProps) {
  return (
    <TextField
      select
      label={label}
      value={wert}
      onChange={(ereignis) => {
        setzeWert(ereignis.target.value);
      }}
      error={fehlerhaft}
      helperText={meldung}
      required={pflicht}
      fullWidth
      slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
    >
      <option value={KEINE_WAHL}>{leerEintrag}</option>
      {eintraege.map((eintrag) => (
        <option key={eintrag.id} value={eintrag.id}>
          {eintrag.aufschrift}
        </option>
      ))}
    </TextField>
  );
}

/** Eine optionale Kennung auf dem Weg zum Server: „nichts gewaehlt" ist `null`, nicht 0. */
function oderNull(wert: string): number | null {
  return wert === KEINE_WAHL ? null : Number(wert);
}

export default function VorgangMaske() {
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [partnerStand, setzePartnerStand] = useState<PartnerStand>(OHNE_PARTNER);
  const [titel, setzeTitel] = useState('');
  const [firmaWahl, setzeFirmaWahl] = useState(KEINE_WAHL);
  const [partnerWahl, setzePartnerWahl] = useState(KEINE_WAHL);
  const [eigeneFehler, setzeEigeneFehler] = useState<FieldErrors>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);

  useEffect(() => {
    void firmenLaden().then(setzeStand);
  }, []);

  useEffect(() => {
    if (firmaWahl === KEINE_WAHL) {
      setzePartnerStand(OHNE_PARTNER);
      return;
    }
    let aktuell = true;
    setzePartnerStand(OHNE_PARTNER);
    void partnerLaden(Number(firmaWahl)).then((neu) => {
      // Die Firma kann inzwischen gewechselt sein. Dann gehoert diese Antwort zu einer Wahl,
      // die es nicht mehr gibt — sie darf die Liste der neuen Firma nicht ueberschreiben.
      if (aktuell) {
        setzePartnerStand(neu);
      }
    });
    return () => {
      aktuell = false;
    };
  }, [firmaWahl]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
    setzeFeldFehler({});
    const eigene: Record<string, readonly string[]> = {};
    if (titel.trim() === '') {
      eigene.titel = [TITEL_FEHLT];
    }
    if (firmaWahl === KEINE_WAHL) {
      eigene.firmaId = [FIRMA_FEHLT];
    }
    setzeEigeneFehler(eigene);
    if (Object.keys(eigene).length > 0) {
      return;
    }
    setzeLaeuft(true);
    try {
      const angelegt = await vorgangAnlegen({
        titel: titel.trim(),
        firmaId: Number(firmaWahl),
        ansprechpartnerId: oderNull(partnerWahl),
      });
      navigate(`/vorgaenge/${String(angelegt.id)}`, { replace: true });
    } catch (ursache) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    }
  };

  /** Die eigene Meldung geht vor: Sie beschreibt die Eingabe, die gar nicht erst abging. */
  const meldung = (feld: string) => meldungAm(eigeneFehler, feld) ?? meldungAm(feldFehler, feld);

  const ohneAktiveFirma = stand.art === 'bereit' && stand.firmen.length === 0;

  /**
   * Der Hinweis an der Firmenauswahl (Kriterium 7): Ohne aktive Firma steht dort der Weg zum
   * Anlegen einer Firma — und zwar als Link, damit er im Tabulatorweg liegt.
   */
  const firmaMeldung: ReactNode = ohneAktiveFirma ? (
    <>
      {`${OHNE_AKTIVE_FIRMA} `}
      <Link component={RouterLink} to="/firmen/neu" underline="hover">
        Firma anlegen
      </Link>
    </>
  ) : (
    meldung('firmaId')
  );

  const partnerMeldung: ReactNode =
    partnerStand.art === 'ausfall' ? AUSFALL_PARTNER : meldung('ansprechpartnerId');

  return (
    <Box
      sx={{
        padding: { xs: 2, sm: '22px 26px 44px' },
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
      }}
    >
      <Platte titel="Neuer Vorgang">
        {stand.art === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              void absenden(ereignis);
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2, padding: '18px 16px' }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            <TextField
              label="Titel"
              value={titel}
              onChange={(ereignis) => {
                setzeTitel(ereignis.target.value);
              }}
              error={meldung('titel') !== undefined}
              helperText={meldung('titel')}
              required
              fullWidth
            />
            <Wahl
              label="Firma"
              leerEintrag="— bitte wählen —"
              wert={firmaWahl}
              setzeWert={(neu) => {
                setzeFirmaWahl(neu);
                // Kriterium 6: Mit der Firma wechselt der Kreis der Ansprechpartner — ein zuvor
                // gewaehlter gehoert nicht mehr dazu.
                setzePartnerWahl(KEINE_WAHL);
              }}
              eintraege={stand.firmen.map((firma) => ({ id: firma.id, aufschrift: firma.name }))}
              meldung={firmaMeldung}
              fehlerhaft={!ohneAktiveFirma && meldung('firmaId') !== undefined}
              pflicht
            />
            <Wahl
              label="Ansprechpartner"
              leerEintrag="— keiner —"
              wert={partnerWahl}
              setzeWert={setzePartnerWahl}
              eintraege={
                partnerStand.art === 'bereit'
                  ? partnerStand.partner.map((einer) => ({
                      id: einer.id,
                      aufschrift: namensZug(einer),
                    }))
                  : []
              }
              meldung={partnerMeldung}
              fehlerhaft={partnerMeldung !== undefined}
            />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft || ohneAktiveFirma}>Anlegen</KupferTaste>
              <Link
                component={RouterLink}
                to="/vorgaenge"
                underline="hover"
                sx={{ fontSize: 12.5 }}
              >
                Abbrechen
              </Link>
            </Box>
          </Box>
        ) : (
          <Box sx={{ padding: '18px 16px' }}>
            {stand.art === 'laedt' ? (
              <Typography
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwarte.textSchwach,
                })}
              >
                Die Firmen werden geladen …
              </Typography>
            ) : (
              <Alert severity="error">{AUSFALL_FIRMEN}</Alert>
            )}
          </Box>
        )}
      </Platte>
    </Box>
  );
}
