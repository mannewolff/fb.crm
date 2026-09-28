import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { angebotLesen } from '../api/angebote';
import type { Angebot, AngebotPosition } from '../api/angebote';
import { auftragAnlegen } from '../api/auftraege';
import type { AuftragAnlegenEingabe, AuftragPositionwahl } from '../api/auftraege';
import type { FieldErrors } from '../api/client';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import { EINHEIT_WORT, MODUS_WORT } from '../components/Positionsmaske';
import WeicheTaste from '../components/WeicheTaste';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { meldungAm } from '../lib/feldmeldung';
import { betrag, dezimal, euro, hundertstel } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Maske, mit der aus einem angenommenen Angebot ein Auftrag entsteht (Kriterien 1 bis 4, F2, F4).
 *
 * <b>Die Maske waehlt und verringert, sie legt nichts an.</b> Je Position des Angebots steht ein
 * Haken (vorbelegt gesetzt), die Menge mit der Angebotsmenge als Obergrenze und — nur bei Aufwand —
 * die Stunden je Personentag, vorbelegt mit 8. Bezeichnung, Abrechnungsmodus, Einheit und
 * Einzelpreis stehen nur lesbar da und gehen **gar nicht** hinaus (Plan E7): Was der Absender nicht
 * aendern darf, kommt im Rumpf nicht vor. Eine Taste zum Hinzufuegen gibt es nicht.
 *
 * <b>Der Platz ist die Stellung im Angebot</b>, 1-basiert: `index + 1` der Liste aus der
 * Angebotsantwort, die keinen eigenen Platz fuehrt (Fund 10 der Plan-Pruefung). Meldet der Server
 * `positionen[i].menge`, zaehlt `i` dagegen in der **gesendeten** Liste — die Maske merkt sich
 * darum, welche Zeile an welcher Stelle hinausging.
 *
 * <b>Das Auftragsdatum kommt nicht aus dem Browser</b> (Plan E7): Bleibt das Feld leer, fehlt es im
 * Rumpf, und das Backend setzt den heutigen Tag der Geschaeftszone. Ein im Browser gesetzter Tag
 * waere zwischen 22:00 UTC und Mitternacht der falsche.
 *
 * Aufbau und Formsprache folgen der Angebotsmaske ({@link AngebotMaske}): Die Vorlage zeigt keine
 * Maske, und zwei Formsprachen fuer zwei Masken derselben Kette waeren ein sichtbarer Bruch.
 */

const NICHT_GEFUNDEN = 'Dieses Angebot gibt es nicht.';
const NICHT_ANGENOMMEN = 'Ein Auftrag entsteht nur aus einem angenommenen Angebot.';
const AUSFALL_LESEN = 'Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_ANLEGEN = 'Der Auftrag wurde nicht angelegt. Bitte später erneut versuchen.';
const LAEDT = 'Das Angebot wird geladen …';
const OHNE_POSITION = 'Das Angebot trägt keine Position — daraus entsteht kein Auftrag.';
const KEINE_ZAHL = 'Bitte eine Zahl mit höchstens zwei Nachkommastellen.';
const NICHT_POSITIV = 'Bitte eine Zahl größer 0.';
const HEUTE = 'Leer bleibt es der heutige Tag.';
const ZAHLEN_UNKLAR = 'Solange eine Menge keine Zahl ist, steht hier keine Summe.';

/** Vorbelegung der Stunden je Personentag an einer Aufwandsposition (F4). */
const STUNDEN_VORGABE = dezimal(800, ',');

/** Was die Maske gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'bereit'; readonly angebot: Angebot }
  | { readonly art: 'meldung'; readonly meldung: string };

/** Eine Zeile der Maske — die Wahl zu genau einer Position des Angebots. */
interface Wahl {
  readonly gewaehlt: boolean;
  /** Die Menge, wie sie im Feld steht. */
  readonly menge: string;
  /** Die Stunden je Personentag, wie sie im Feld stehen — `null` an einer Festpreisposition. */
  readonly stunden: string | null;
}

/** Was die Maske selbst an einer Zeile bemaengelt. */
interface Zeilenfehler {
  readonly menge?: string;
  readonly stunden?: string;
}

/** Die Positionen des Angebots als vorbelegte Zeilen der Maske. */
function vorbelegt(positionen: readonly AngebotPosition[]): readonly Wahl[] {
  return positionen.map((position) => ({
    gewaehlt: true,
    menge: dezimal(position.mengeInHundertsteln, ','),
    stunden: position.abrechnungsmodus === 'AUFWAND' ? STUNDEN_VORGABE : null,
  }));
}

/** Ein Feldinhalt als Angabe fuer den Rumpf — leer heisst „keine Angabe", nicht „leerer Text". */
function oderNull(wert: string): string | null {
  const getrimmt = wert.trim();
  return getrimmt === '' ? null : getrimmt;
}

/**
 * Eine gewaehlte Zeile als Positionswahl — oder das, was die Maske an ihr bemaengelt (F2, F4).
 *
 * Gelesen und geprueft wird in einem Schritt: Wer erst prueft und danach noch einmal liest,
 * braucht fuer den zweiten Lesevorgang einen Rueckfall, der nie greifen kann.
 */
function alsWahl(
  wahl: Wahl,
  position: AngebotPosition,
  platz: number,
): { readonly wahl: AuftragPositionwahl } | { readonly fehler: Zeilenfehler } {
  const menge = hundertstel(wahl.menge);
  let mengeFehler: string | undefined;
  if (menge === null) {
    mengeFehler = KEINE_ZAHL;
  } else if (menge > position.mengeInHundertsteln) {
    mengeFehler = `Höchstens ${dezimal(position.mengeInHundertsteln, ',')} — die Menge des Angebots.`;
  }
  const stunden = wahl.stunden === null ? null : hundertstel(wahl.stunden);
  let stundenFehler: string | undefined;
  if (wahl.stunden !== null && stunden === null) {
    stundenFehler = KEINE_ZAHL;
  } else if (stunden === 0) {
    stundenFehler = NICHT_POSITIV;
  }
  if (menge === null || mengeFehler !== undefined || stundenFehler !== undefined) {
    return {
      fehler: {
        ...(mengeFehler === undefined ? {} : { menge: mengeFehler }),
        ...(stundenFehler === undefined ? {} : { stunden: stundenFehler }),
      },
    };
  }
  return {
    wahl: {
      platz,
      menge: dezimal(menge, '.'),
      stundenJePersonentag: stunden === null ? null : dezimal(stunden, '.'),
    },
  };
}

/** Die Summe der gewaehlten Zeilen in Cent — `null`, solange eine Menge keine Zahl ist. */
function summeIn(wahlen: readonly Wahl[], positionen: readonly AngebotPosition[]): number | null {
  let summe = 0;
  for (const [stelle, wahl] of wahlen.entries()) {
    if (wahl.gewaehlt) {
      const menge = hundertstel(wahl.menge);
      if (menge === null) {
        return null;
      }
      summe += betrag(menge, positionen[stelle].einzelpreisInCent);
    }
  }
  return summe;
}

/** Die Stufen des Kopfpfads ueber der Maske (Plan E16). */
const ZU_VORGAENGEN: PfadVerweis = { titel: 'Vorgänge', ziel: '/vorgaenge' };

export default function AuftragAnlegenMaske() {
  const { id, angebotId } = useParams();
  const vorgangKennung = kennungAus(id);
  const angebotKennung = kennungAus(angebotId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [wahlen, setzeWahlen] = useState<readonly Wahl[]>([]);
  const [kopf, setzeKopf] = useState({
    auftragDatum: '',
    kundenbestellnummer: '',
    leistungAb: '',
    leistungBis: '',
  });
  const [zeilenfehler, setzeZeilenfehler] = useState<Readonly<Record<number, Zeilenfehler>>>({});
  const [feldFehler, setzeFeldFehler] = useState<FieldErrors>({});
  /** Welche Stelle im Angebot an welcher Stelle der gesendeten Liste stand. */
  const [gesendeteStellen, setzeGesendeteStellen] = useState<readonly number[]>([]);
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // `String(…)` und kein Rueckfall: Ohne Kennung zeigt die Maske ihre Meldung und bietet gar keinen
  // Weg an — ein zweiter Zweig nur fuer den Kopf waere ein Zweig ohne Wirkung.
  useKopfPfad(
    [
      ZU_VORGAENGEN,
      { titel: 'Vorgang', ziel: `/vorgaenge/${String(vorgangKennung)}` },
      {
        titel: 'Angebot',
        ziel: `/vorgaenge/${String(vorgangKennung)}/angebote/${String(angebotKennung)}`,
      },
    ],
    'Neuer Auftrag',
  );

  useEffect(() => {
    if (angebotKennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'meldung', meldung: NICHT_GEFUNDEN });
      return;
    }
    void angebotLesen(angebotKennung)
      .then((angebot) => {
        if (angebot.stand !== 'ANGENOMMEN') {
          // Geprueft wird an der Antwort und nicht an der Adresse: Wer die Adresse tippt, bekommt
          // dieselbe Antwort wie wer die Taste nimmt (Kriterium 1).
          setzeStand({ art: 'meldung', meldung: NICHT_ANGENOMMEN });
          return;
        }
        setzeWahlen(vorbelegt(angebot.positionen));
        setzeStand({ art: 'bereit', angebot });
      })
      .catch((ursache: unknown) => {
        setzeStand({
          art: 'meldung',
          meldung: nichtGefunden(ursache) ? NICHT_GEFUNDEN : AUSFALL_LESEN,
        });
      });
  }, [angebotKennung]);

  const aendere = (stelle: number, neu: Partial<Wahl>) => {
    setzeWahlen(wahlen.map((alt, index) => (index === stelle ? { ...alt, ...neu } : alt)));
  };

  const anlegen = async (angebot: Angebot) => {
    setzeFehler(null);
    setzeFeldFehler({});
    const eigene: Record<number, Zeilenfehler> = {};
    const gewaehlt: AuftragPositionwahl[] = [];
    const stellen: number[] = [];
    for (const [stelle, wahl] of wahlen.entries()) {
      if (wahl.gewaehlt) {
        // Der Platz ist die Stellung im Angebot, 1-basiert (Fund 10 der Plan-Pruefung).
        const gelesen = alsWahl(wahl, angebot.positionen[stelle], stelle + 1);
        if ('fehler' in gelesen) {
          eigene[stelle] = gelesen.fehler;
        } else {
          gewaehlt.push(gelesen.wahl);
          stellen.push(stelle);
        }
      }
    }
    setzeZeilenfehler(eigene);
    if (Object.keys(eigene).length > 0) {
      // Was fehlt, steht an der Zeile; hinaus geht nichts.
      return;
    }
    const eingabe: AuftragAnlegenEingabe = {
      ...(kopf.auftragDatum === '' ? {} : { auftragDatum: kopf.auftragDatum }),
      kundenbestellnummer: oderNull(kopf.kundenbestellnummer),
      leistungAb: oderNull(kopf.leistungAb),
      leistungBis: oderNull(kopf.leistungBis),
      positionen: gewaehlt,
    };
    setzeGesendeteStellen(stellen);
    setzeLaeuft(true);
    try {
      const auftrag = await auftragAnlegen(angebot.id, eingabe);
      // `replace`: Die Maske eines angelegten Auftrags gibt es nicht mehr, ein Schritt zurueck
      // fuehrte nur in eine Absage.
      navigate(`/vorgaenge/${String(auftrag.vorgangId)}/auftraege/${String(auftrag.id)}`, {
        replace: true,
      });
    } catch (ursache) {
      setzeLaeuft(false);
      const felder = feldMeldungen(ursache);
      if (Object.keys(felder).length > 0) {
        setzeFeldFehler(felder);
      } else {
        setzeFehler(AUSFALL_ANLEGEN);
      }
    }
  };

  /** Die Meldung des Servers zu einem Feld einer Zeile — gezaehlt in der gesendeten Liste. */
  const serverMeldung = (stelle: number, feld: string) => {
    const gesendet = gesendeteStellen.indexOf(stelle);
    return gesendet === -1 ? undefined : meldungAm(feldFehler, `positionen[${String(gesendet)}].${feld}`);
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

  const angebot = stand.angebot;
  const summe = summeIn(wahlen, angebot.positionen);
  const nichtsGewaehlt = !wahlen.some((wahl) => wahl.gewaehlt);
  const listenMeldung = meldungAm(feldFehler, 'positionen');
  return (
    <Box
      component="form"
      noValidate
      onSubmit={(ereignis: FormEvent<HTMLFormElement>) => {
        ereignis.preventDefault();
        void anlegen(angebot);
      }}
      sx={spalten}
    >
      <Karte titel="Neuer Auftrag" titelEbene={1}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
          <Typography
            sx={(theme) => ({ fontSize: 13, color: theme.vars.palette.kupferwolke.textMatt })}
          >
            {angebot.nummer === null ? 'Aus dem angenommenen Angebot' : `Aus Angebot ${angebot.nummer}`}
          </Typography>
          <TextField
            label="Auftragsdatum"
            type="date"
            value={kopf.auftragDatum}
            onChange={(ereignis) => {
              setzeKopf({ ...kopf, auftragDatum: ereignis.target.value });
            }}
            error={meldungAm(feldFehler, 'auftragDatum') !== undefined}
            helperText={meldungAm(feldFehler, 'auftragDatum') ?? HEUTE}
            fullWidth
            // Ein `date`-Feld zeigt immer seine Maske, also darf die Beschriftung nie im Feld liegen.
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Kundenbestellnummer"
            value={kopf.kundenbestellnummer}
            onChange={(ereignis) => {
              setzeKopf({ ...kopf, kundenbestellnummer: ereignis.target.value });
            }}
            error={meldungAm(feldFehler, 'kundenbestellnummer') !== undefined}
            helperText={meldungAm(feldFehler, 'kundenbestellnummer')}
            fullWidth
          />
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <TextField
              label="Leistung ab"
              type="date"
              value={kopf.leistungAb}
              onChange={(ereignis) => {
                setzeKopf({ ...kopf, leistungAb: ereignis.target.value });
              }}
              error={meldungAm(feldFehler, 'leistungAb') !== undefined}
              helperText={meldungAm(feldFehler, 'leistungAb')}
              sx={{ flex: '1 1 200px' }}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              label="Leistung bis"
              type="date"
              value={kopf.leistungBis}
              onChange={(ereignis) => {
                setzeKopf({ ...kopf, leistungBis: ereignis.target.value });
              }}
              error={meldungAm(feldFehler, 'leistungBis') !== undefined}
              helperText={meldungAm(feldFehler, 'leistungBis')}
              sx={{ flex: '1 1 200px' }}
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Box>
        </Box>
      </Karte>
      <Karte titel="Positionen" anzahl={angebot.positionen.length}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {listenMeldung === undefined ? null : <Alert severity="error">{listenMeldung}</Alert>}
          {angebot.positionen.length === 0 ? (
            <Typography
              role="status"
              sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {OHNE_POSITION}
            </Typography>
          ) : (
            angebot.positionen.map((position, stelle) => {
              const wahl = wahlen[stelle];
              const eigen = zeilenfehler[stelle] ?? {};
              const mengeMeldung = eigen.menge ?? serverMeldung(stelle, 'menge');
              const stundenMeldung =
                eigen.stunden ?? serverMeldung(stelle, 'stundenJePersonentag');
              const platzMeldung = serverMeldung(stelle, 'platz');
              return (
                <Box
                  // Die Stelle ist der Schluessel: Eine Position traegt keine eigene Kennung, ihre
                  // Stelle im Angebot ist ihre Identitaet (E24 in Plan #87).
                  key={stelle}
                  component="fieldset"
                  aria-label={position.bezeichnung}
                  sx={(theme) => ({
                    margin: 0,
                    padding: '14px 16px',
                    border: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
                    borderRadius: '14px',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 1.5,
                  })}
                >
                  <FormControlLabel
                    control={
                      <Checkbox
                        checked={wahl.gewaehlt}
                        onChange={(ereignis) => {
                          aendere(stelle, { gewaehlt: ereignis.target.checked });
                        }}
                      />
                    }
                    label={position.bezeichnung}
                    sx={{ '& .MuiFormControlLabel-label': { fontWeight: 600, fontSize: 14 } }}
                  />
                  <Typography
                    className={ZAHLEN_KLASSE}
                    sx={(theme) => ({ fontSize: 13, color: theme.vars.palette.kupferwolke.textMatt })}
                  >
                    {`${MODUS_WORT[position.abrechnungsmodus]} · ${EINHEIT_WORT[position.einheit]} · ${euro(position.einzelpreisInCent)}`}
                  </Typography>
                  {platzMeldung === undefined ? null : (
                    <Alert severity="error">{platzMeldung}</Alert>
                  )}
                  <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
                    <TextField
                      label={`Menge (${EINHEIT_WORT[position.einheit]})`}
                      name="menge"
                      value={wahl.menge}
                      disabled={!wahl.gewaehlt}
                      onChange={(ereignis) => {
                        aendere(stelle, { menge: ereignis.target.value });
                      }}
                      error={mengeMeldung !== undefined}
                      helperText={
                        mengeMeldung ??
                        `höchstens ${dezimal(position.mengeInHundertsteln, ',')}`
                      }
                      sx={{ flex: '1 1 160px' }}
                      slotProps={{ htmlInput: { inputMode: 'decimal', className: ZAHLEN_KLASSE } }}
                    />
                    {wahl.stunden === null ? null : (
                      <TextField
                        label="Stunden je Personentag"
                        name="stundenJePersonentag"
                        value={wahl.stunden}
                        disabled={!wahl.gewaehlt}
                        onChange={(ereignis) => {
                          aendere(stelle, { stunden: ereignis.target.value });
                        }}
                        error={stundenMeldung !== undefined}
                        helperText={stundenMeldung}
                        sx={{ flex: '1 1 160px' }}
                        slotProps={{
                          htmlInput: { inputMode: 'decimal', className: ZAHLEN_KLASSE },
                        }}
                      />
                    )}
                  </Box>
                </Box>
              );
            })
          )}
          <Box
            sx={(theme) => ({
              display: 'flex',
              alignItems: 'baseline',
              gap: '10px',
              flexWrap: 'wrap',
              paddingTop: '14px',
              borderTop: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
            })}
          >
            <Typography sx={{ fontSize: 13.5, fontWeight: 600 }}>Summe (netto)</Typography>
            <Typography
              data-testid="auftrag-summe"
              className={ZAHLEN_KLASSE}
              sx={{ fontSize: 17, fontWeight: 800 }}
            >
              {summe === null ? '—' : euro(summe)}
            </Typography>
            {summe === null ? (
              <Typography
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.toenung.rose.schrift,
                })}
              >
                {ZAHLEN_UNKLAR}
              </Typography>
            ) : null}
          </Box>
        </Box>
      </Karte>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.25, flexWrap: 'wrap' }}>
        <KupferTaste disabled={laeuft || nichtsGewaehlt}>Auftrag anlegen</KupferTaste>
        <WeicheTaste to={`/vorgaenge/${String(angebot.vorgangId)}/angebote/${String(angebot.id)}`}>
          Abbrechen
        </WeicheTaste>
      </Box>
    </Box>
  );
}
