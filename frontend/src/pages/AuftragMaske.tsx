import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { auftragLesen, auftragPflegen } from '../api/auftraege';
import type { Auftrag } from '../api/auftraege';
import type { FieldErrors } from '../api/client';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import { EINHEIT_WORT } from '../components/Positionsmaske';
import Tafel from '../components/Tafel';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { auftragsstatusBild } from '../lib/auftragsstatus';
import type { Auftragsstatus } from '../lib/auftragsstatus';
import { meldungAm } from '../lib/feldmeldung';
import { dezimal, euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Pflege-Maske eines Auftrags (Kriterium 7, F3, F6; Plan E8, E16).
 *
 * <b>Vier Angaben und nicht mehr.</b> Auftragsdatum, Kundenbestellnummer, Leistungszeitraum und
 * Status gehen als Ganzes hinaus (`PUT`, Plan E8). Die Positionen stehen darunter **nur lesbar**:
 * Sie stehen ab der Anlage fest (F3), und die Stunden je Personentag aendern sich danach nie mehr
 * (R4). Eine Maske mit zwei Betriebsarten waere zwei Formulare in einer Datei — das Anlegen hat
 * seine eigene ({@link AuftragAnlegenMaske}).
 *
 * <b>Der Status laesst sich in jede Richtung setzen</b> (F6), auch von „Abgeschlossen" zurueck.
 * Die Worte der Auswahl kommen aus {@link auftragsstatusBild}, nicht aus dieser Datei.
 *
 * <b>Der Leistungszeitraum steht ganz oder gar nicht</b> (Kriterium 3): Die Maske meldet ein
 * fehlendes Gegenstueck und ein Ende vor dem Beginn am Feld, bevor etwas hinausgeht; das Backend
 * prueft dasselbe noch einmal (Plan E21), und seine Meldungen landen am selben Feld.
 */

const NICHT_GEFUNDEN = 'Diesen Auftrag gibt es nicht.';
const AUSFALL_LESEN = 'Der Auftrag ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_SPEICHERN = 'Der Auftrag wurde nicht gespeichert. Bitte später erneut versuchen.';
const LAEDT = 'Der Auftrag wird geladen …';
const DATUM_FEHLT = 'Bitte ein Auftragsdatum angeben.';
const BEGINN_FEHLT = 'Bitte auch den Beginn angeben — oder beide Felder leer lassen.';
const ENDE_FEHLT = 'Bitte auch das Ende angeben — oder beide Felder leer lassen.';
const ENDE_VOR_BEGINN = 'Das Ende liegt vor dem Beginn.';

const STATUS: readonly Auftragsstatus[] = ['OFFEN', 'IN_ARBEIT', 'ABGESCHLOSSEN'];
const SPALTEN = ['Bezeichnung', 'Menge', 'Einheit', 'Std. je PT', 'Betrag'] as const;

/** Was die Maske gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'bereit'; readonly auftrag: Auftrag; readonly felder: Felder }
  | { readonly art: 'meldung'; readonly meldung: string };

/**
 * Der Wert der Auswahl als Status.
 *
 * Eine Kette von Vergleichen und kein Nachschlagen: Die native Auswahl kennt nur die drei Werte,
 * und jeder Zweig ist ueber sie erreichbar — dasselbe Muster wie der Abrechnungsmodus in
 * {@link Positionsmaske}.
 */
function alsStatus(wert: string): Auftragsstatus {
  if (wert === 'IN_ARBEIT') {
    return 'IN_ARBEIT';
  }
  return wert === 'ABGESCHLOSSEN' ? 'ABGESCHLOSSEN' : 'OFFEN';
}

/** Die vier Angaben, so wie sie in den Feldern stehen. */
interface Felder {
  readonly auftragDatum: string;
  readonly kundenbestellnummer: string;
  readonly leistungAb: string;
  readonly leistungBis: string;
  readonly status: Auftragsstatus;
}

function alsFelder(auftrag: Auftrag): Felder {
  return {
    auftragDatum: auftrag.auftragDatum,
    kundenbestellnummer: auftrag.kundenbestellnummer ?? '',
    leistungAb: auftrag.leistungAb ?? '',
    leistungBis: auftrag.leistungBis ?? '',
    status: auftrag.status,
  };
}

/** Ein Feldinhalt als Angabe fuer den Rumpf — leer heisst „keine Angabe", nicht „leerer Text". */
function oderNull(wert: string): string | null {
  const getrimmt = wert.trim();
  return getrimmt === '' ? null : getrimmt;
}

/** Was die Maske selbst bemaengelt, bevor etwas hinausgeht (Kriterium 3). */
function eigenePruefung(felder: Felder): FieldErrors {
  const fehler: Record<string, readonly string[]> = {};
  if (felder.auftragDatum === '') {
    fehler.auftragDatum = [DATUM_FEHLT];
  }
  const ab = felder.leistungAb;
  const bis = felder.leistungBis;
  if (ab === '' && bis !== '') {
    fehler.leistungAb = [BEGINN_FEHLT];
  } else if (ab !== '' && bis === '') {
    fehler.leistungBis = [ENDE_FEHLT];
  } else if (bis < ab) {
    // Tage in der Form `YYYY-MM-DD` ordnen sich als Text wie als Datum.
    fehler.leistungBis = [ENDE_VOR_BEGINN];
  }
  return fehler;
}

/** Die Stufen des Kopfpfads ueber der Maske (Plan E16). */
const ZU_VORGAENGEN: PfadVerweis = { titel: 'Vorgänge', ziel: '/vorgaenge' };

export default function AuftragMaske() {
  const { id, auftragId } = useParams();
  const vorgangKennung = kennungAus(id);
  const kennung = kennungAus(auftragId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  useKopfPfad(
    [
      ZU_VORGAENGEN,
      { titel: 'Vorgang', ziel: `/vorgaenge/${String(vorgangKennung)}` },
      {
        titel: stand.art === 'bereit' ? `Auftrag ${stand.auftrag.nummer}` : 'Auftrag',
        ziel: `/vorgaenge/${String(vorgangKennung)}/auftraege/${String(kennung)}`,
      },
    ],
    'Bearbeiten',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'meldung', meldung: NICHT_GEFUNDEN });
      return;
    }
    void auftragLesen(kennung)
      .then((auftrag) => {
        setzeStand({ art: 'bereit', auftrag, felder: alsFelder(auftrag) });
      })
      .catch((ursache: unknown) => {
        setzeStand({
          art: 'meldung',
          meldung: nichtGefunden(ursache) ? NICHT_GEFUNDEN : AUSFALL_LESEN,
        });
      });
  }, [kennung]);

  const speichern = async (auftrag: Auftrag, eingabe: Felder) => {
    setzeFehler(null);
    const eigene = eigenePruefung(eingabe);
    setzeFeldFehler(eigene);
    if (Object.keys(eigene).length > 0) {
      // Was fehlt, steht am Feld; hinaus geht nichts.
      return;
    }
    setzeLaeuft(true);
    try {
      await auftragPflegen(auftrag.id, {
        auftragDatum: eingabe.auftragDatum,
        kundenbestellnummer: oderNull(eingabe.kundenbestellnummer),
        leistungAb: oderNull(eingabe.leistungAb),
        leistungBis: oderNull(eingabe.leistungBis),
        status: eingabe.status,
      });
      navigate(`/vorgaenge/${String(auftrag.vorgangId)}/auftraege/${String(auftrag.id)}`);
    } catch (ursache) {
      setzeLaeuft(false);
      const gemeldet = feldMeldungen(ursache);
      if (Object.keys(gemeldet).length > 0) {
        setzeFeldFehler(gemeldet);
      } else {
        setzeFehler(AUSFALL_SPEICHERN);
      }
    }
  };

  /** Der Rahmen jeder Bereitschaft: der Abstand zwischen den Karten der Buehne. */
  const spalten = { display: 'flex', flexDirection: 'column', gap: '22px' } as const;

  if (stand.art === 'laedt') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Typography
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {LAEDT}
          </Typography>
        </Karte>
      </Box>
    );
  }

  if (stand.art === 'meldung') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Alert severity="error">{stand.meldung}</Alert>
        </Karte>
      </Box>
    );
  }

  const { auftrag, felder } = stand;
  /** Eine Angabe aendern — der Auftrag bleibt, nur die Felder wechseln. */
  const setzeFelder = (neu: Felder) => {
    setzeStand({ art: 'bereit', auftrag, felder: neu });
  };
  const meldung = (feld: string) => meldungAm(feldFehler, feld);
  return (
    <Box
      component="form"
      noValidate
      onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
        ereignis.preventDefault();
        void speichern(auftrag, felder);
      }}
      sx={spalten}
    >
      <Karte titel={`Auftrag ${auftrag.nummer} bearbeiten`} titelEbene={1}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
          <TextField
            label="Auftragsdatum"
            type="date"
            value={felder.auftragDatum}
            onChange={(ereignis) => {
              setzeFelder({ ...felder, auftragDatum: ereignis.target.value });
            }}
            error={meldung('auftragDatum') !== undefined}
            helperText={meldung('auftragDatum')}
            required
            fullWidth
            // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld liegen.
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Kundenbestellnummer"
            value={felder.kundenbestellnummer}
            onChange={(ereignis) => {
              setzeFelder({ ...felder, kundenbestellnummer: ereignis.target.value });
            }}
            error={meldung('kundenbestellnummer') !== undefined}
            helperText={meldung('kundenbestellnummer')}
            fullWidth
          />
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <TextField
              label="Leistung ab"
              type="date"
              value={felder.leistungAb}
              onChange={(ereignis) => {
                setzeFelder({ ...felder, leistungAb: ereignis.target.value });
              }}
              error={meldung('leistungAb') !== undefined}
              helperText={meldung('leistungAb')}
              sx={{ flex: '1 1 200px' }}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Leistung bis"
              type="date"
              value={felder.leistungBis}
              onChange={(ereignis) => {
                setzeFelder({ ...felder, leistungBis: ereignis.target.value });
              }}
              error={meldung('leistungBis') !== undefined}
              helperText={meldung('leistungBis')}
              sx={{ flex: '1 1 200px' }}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Box>
          <TextField
            select
            label="Status"
            value={felder.status}
            onChange={(ereignis) => {
              setzeFelder({ ...felder, status: alsStatus(ereignis.target.value) });
            }}
            error={meldung('status') !== undefined}
            helperText={meldung('status')}
            fullWidth
            slotProps={{ select: { native: true }, inputLabel: { shrink: true } }}
          >
            {STATUS.map((status) => (
              <option key={status} value={status}>
                {auftragsstatusBild(status).wort}
              </option>
            ))}
          </TextField>
        </Box>
      </Karte>
      <Karte titel="Positionen" anzahl={auftrag.positionen.length}>
        <Tafel beschriftung="Positionen" spalten={[...SPALTEN]}>
          {auftrag.positionen.map((position, stelle) => (
            // Die Stelle ist der Schluessel: Eine Position traegt keine eigene Kennung.
            <Box component="tr" key={stelle}>
              <Box component="td" sx={{ fontWeight: 500 }}>
                {position.bezeichnung}
              </Box>
              <Box component="td" className={ZAHLEN_KLASSE} sx={{ textAlign: 'right' }}>
                {dezimal(position.mengeInHundertsteln, ',')}
              </Box>
              <Box component="td">{EINHEIT_WORT[position.einheit]}</Box>
              <Box component="td" className={ZAHLEN_KLASSE} sx={{ textAlign: 'right' }}>
                {position.stundenJePersonentagInHundertsteln === null ? (
                  <span aria-label="keine Angabe">—</span>
                ) : (
                  dezimal(position.stundenJePersonentagInHundertsteln, ',')
                )}
              </Box>
              <Box
                component="td"
                className={ZAHLEN_KLASSE}
                sx={{ textAlign: 'right', fontWeight: 600, whiteSpace: 'nowrap' }}
              >
                {euro(position.betragInCent)}
              </Box>
            </Box>
          ))}
        </Tafel>
      </Karte>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
        <KupferTaste disabled={laeuft}>Speichern</KupferTaste>
        <WeicheTaste to={`/vorgaenge/${String(auftrag.vorgangId)}/auftraege/${String(auftrag.id)}`}>
          Zum Auftrag
        </WeicheTaste>
      </Box>
    </Box>
  );
}
