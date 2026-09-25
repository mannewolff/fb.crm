import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import { firmaAendern, firmaAnlegen, firmaLesen } from '../api/firmen';
import type { FieldErrors } from '../api/client';
import KupferTaste from '../components/KupferTaste';
import Platte from '../components/Platte';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { kennungAus } from '../lib/kennung';

/**
 * Die Maske der Firma — Anlegen unter `/firmen/neu`, Aendern unter `/firmen/:id/bearbeiten`
 * (Kriterien 4, 7, 8).
 *
 * Eine Komponente fuer beide Wege: Die Felder, ihre Meldungen und ihr Verhalten sind dieselben,
 * und der einzige Unterschied ist, woher die Werte kommen und wohin das Speichern fuehrt. Zwei
 * Abschriften liefen beim ersten Nachziehen auseinander.
 *
 * Die Maske steht als {@link Platte} auf der Buehne und nicht in der Karte der Auth-Seiten (E19,
 * Plan-Review Fund 5): Die bringt ein eigenes `main` und die Marke mit — innerhalb des
 * angemeldeten Rahmens waere das ein zweites `main` in derselben Seite.
 *
 * Eine eigene Ruecknahme braucht es nicht: „ohne Speichern verlassen" ist der Weg zurueck (E10).
 *
 * Das Formular traegt `noValidate`. Die Pflichtangabe steht als `required` am Feld — fuer den
 * Screenreader und fuer das Sternchen —, die Meldung dazu schreibt aber diese Maske, damit sie
 * in derselben Sprache und an derselben Stelle steht wie die Meldungen des Servers.
 */

/** Beim Anlegen vorbelegt; beim Aendern gilt der gespeicherte Wert (Kriterium 4). */
const LAND_VORGABE = 'Deutschland';

const NAME_FEHLT = 'Bitte einen Namen angeben.';
const NICHT_GEFUNDEN = 'Diese Firma gibt es nicht.';
const AUSFALL_LESEN = 'Die Firma ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN =
  'Die Firma wurde nicht gespeichert. Bitte später erneut versuchen.';

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

/** Eine fehlende Angabe wird zum leeren Feld — nie zu einem Platzhalter im Eingabefeld. */
function alsText(wert: string | null): string {
  return wert ?? '';
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
        setzeWerte({
          name: firma.name,
          strasse: alsText(firma.strasse),
          plz: alsText(firma.plz),
          ort: alsText(firma.ort),
          land: alsText(firma.land),
          steuernummer: alsText(firma.steuernummer),
          umsatzsteuerId: alsText(firma.umsatzsteuerId),
        });
        setzeStand('bereit');
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? 'unbekannt' : 'ausfall');
      });
  }, [aendern, kennung]);

  const absenden = async (ereignis: FormEvent<HTMLFormElement>) => {
    ereignis.preventDefault();
    setzeFehler(null);
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
        await firmaAendern(kennung, eingabe);
        navigate(`/firmen/${String(kennung)}`, { replace: true });
      }
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

  const titel = aendern ? 'Firma bearbeiten' : 'Neue Firma';
  const zurueck = kennung === null ? '/firmen' : `/firmen/${String(kennung)}`;
  const nameMeldung = nameFehlt ? NAME_FEHLT : meldungAm(feldFehler, 'name');

  return (
    <Box
      sx={{
        padding: { xs: 2, sm: '22px 26px 44px' },
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
      }}
    >
      <Platte titel={titel}>
        {stand === 'bereit' ? (
          <Box
            component="form"
            noValidate
            onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
              void absenden(ereignis);
            }}
            sx={{ display: 'flex', flexDirection: 'column', gap: 2, padding: '18px 16px' }}
          >
            {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
            <Eingabe
              label="Name"
              feld="name"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={nameMeldung}
              pflicht
            />
            <Eingabe
              label="Straße und Hausnummer"
              feld="strasse"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'strasse')}
            />
            <Eingabe
              label="Postleitzahl"
              feld="plz"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'plz')}
            />
            <Eingabe
              label="Ort"
              feld="ort"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'ort')}
            />
            <Eingabe
              label="Land"
              feld="land"
              werte={werte}
              setzeWerte={setzeWerte}
              meldung={meldungAm(feldFehler, 'land')}
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
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap' }}>
              <KupferTaste disabled={laeuft}>{aendern ? 'Speichern' : 'Anlegen'}</KupferTaste>
              <Link component={RouterLink} to={zurueck} underline="hover" sx={{ fontSize: 12.5 }}>
                Abbrechen
              </Link>
            </Box>
          </Box>
        ) : (
          <Box sx={{ padding: '18px 16px' }}>
            {stand === 'laedt' ? (
              <Typography
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwarte.textSchwach,
                })}
              >
                Die Firma wird geladen …
              </Typography>
            ) : (
              <Alert severity="error">
                {stand === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL_LESEN}
              </Alert>
            )}
          </Box>
        )}
      </Platte>
    </Box>
  );
}
