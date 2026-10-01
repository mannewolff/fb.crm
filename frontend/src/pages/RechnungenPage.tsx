import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import { IconDownload, IconFilePlus } from '@tabler/icons-react';
import { useEffect, useId, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';

import { ALLGEMEINE_MELDUNG, ApiError } from '../api/client';
import {
  abrechenbareAngebote,
  rechnungAnlegen,
  rechnungDokumentPfad,
  rechnungenUebersicht,
} from '../api/rechnungen';
import type {
  AbrechenbareAngebote,
  AbrechenbaresAngebot,
  RechnungZeile,
  RechnungenUebersicht,
} from '../api/rechnungen';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import RechnungszustandChip from '../components/RechnungszustandChip';
import Tafel from '../components/Tafel';
import TastenSymbol from '../components/TastenSymbol';
import WeicheTaste, { weichSx } from '../components/WeicheTaste';
import { euro } from '../lib/geld';
import { tagWort } from '../lib/tag';
import { RADIUS_KLEIN, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Liste aller Rechnungen mit „Neue Rechnung" (#160, Kriterien 1, 2, 24; Plan E15).
 *
 * Aufbau wie die Angebotsuebersicht: eine {@link Karte} mit der Hauptaktion im Kopf und einer
 * {@link Tafel} darunter. Sortiert wird am Server — die Zeilen stehen in der Reihenfolge der
 * Antwort, neueste zuerst.
 *
 * <b>Die Zeile fuehrt ueber ihre Nummernspalte auf die Rechnung</b> — ein Link je Zeile, wie in
 * {@link Angebotsliste} und {@link FirmenPage}. Mehrere Zellen zu verlinken gaebe demselben Ziel
 * mehrere Eintraege im Tabulatorweg, und ein Klickbereich ueber die ganze Zeile liesse sich nur mit
 * einem gestreckten Anker bauen, den die uebrigen Tafeln dieser Anwendung nicht kennen. Die Zeile
 * hebt sich im Hover als Flaeche — das macht die {@link Tafel} selbst.
 *
 * <b>Nur die gestellte Rechnung traegt „Herunterladen"</b>, und zwar als `<a download>` auf den Weg
 * des Dokuments: Der antwortet mit `Content-Disposition: attachment`, und genau das verlangt ein
 * solcher Verweis. Ein Entwurf hat kein Dokument — der Weg antwortete dort mit 409, und eine Taste,
 * die in einen Fehler fuehrt, ist keine Taste.
 *
 * <b>„Neue Rechnung" ist die eine Kupfertaste.</b> Sie oeffnet die Wahl unter den abrechenbaren
 * Angeboten; die Wahl legt den Entwurf an und fuehrt auf ihn. <b>Die Liste fuehrt, der Server
 * entscheidet</b>: Dass ein Angebot in der Wahl steht, ist eine Auskunft und keine Zusage — weist
 * das Anlegen mit 409 ab, steht die Meldung des Servers in der Wahl.
 */

/** Die Liste ist die erste Stufe des Pfades — ueber ihr steht nichts (E6). */
const KEIN_WEG: readonly PfadVerweis[] = [];

const LAEDT = 'Rechnungen werden geladen …';
const AUSFALL = 'Die Rechnungen sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const LEER = 'Noch keine Rechnung. Schreiben Sie die erste über „Neue Rechnung".';

const WAHL_LAEDT = 'Die abrechenbaren Angebote werden geladen …';
const WAHL_AUSFALL =
  'Die abrechenbaren Angebote sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const WAHL_LEER =
  'Kein Angebot ist abrechenbar. Ein Angebot muss bestellt sein und noch etwas offen haben.';

/** Was an der Stelle der Nummer steht, solange die Rechnung keine hat (Kriterium 15). */
const OHNE_NUMMER = 'Entwurf';

const SPALTEN: readonly string[] = [
  'Nummer',
  'Firma',
  'Rechnungsdatum',
  'Betrag',
  'Zustand',
  'Dokument',
];

/** Die Symbolgroesse in den Tasten (wie in {@link AngebotPage}). */
const SYMBOL_TASTE = 16;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly uebersicht: RechnungenUebersicht }
  | { readonly art: 'fehler' };

/** Was die Wahl gerade weiss. */
type Wahlstand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly wahl: AbrechenbareAngebote }
  | { readonly art: 'fehler' };

/** Holt die Liste und macht auch aus dem Fehlschlag einen Stand. */
async function laden(): Promise<Stand> {
  try {
    return { art: 'daten', uebersicht: await rechnungenUebersicht() };
  } catch {
    return { art: 'fehler' };
  }
}

/** Holt die abrechenbaren Angebote und macht auch aus dem Fehlschlag einen Stand. */
async function wahlLaden(): Promise<Wahlstand> {
  try {
    return { art: 'daten', wahl: await abrechenbareAngebote() };
  } catch {
    return { art: 'fehler' };
  }
}

/** Eine Zeile der Tafel; die Nummernspalte traegt den Weg zur Rechnung. */
function Zeile({ rechnung }: { readonly rechnung: RechnungZeile }) {
  const kennung = String(rechnung.id);
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500, whiteSpace: 'nowrap' }}>
        <Box
          component={RouterLink}
          to={`/rechnungen/${kennung}`}
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            color: 'inherit',
            textDecoration: 'none',
            '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
          })}
        >
          {rechnung.nummer ?? OHNE_NUMMER}
        </Box>
      </Box>
      <Box component="td">{rechnung.firmaName}</Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ whiteSpace: 'nowrap' }}>
        {tagWort(rechnung.rechnungDatum)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, whiteSpace: 'nowrap', textAlign: 'right' }}
      >
        {euro(rechnung.bruttoInCent)}
      </Box>
      <Box component="td">
        <RechnungszustandChip zustand={rechnung.zustand} />
      </Box>
      <Box component="td" sx={{ textAlign: 'right' }}>
        {rechnung.zustand === 'ENTWURF' ? null : (
          <Button
            component="a"
            href={rechnungDokumentPfad(rechnung.id)}
            download
            aria-label="Herunterladen"
            sx={weichSx}
          >
            <TastenSymbol>
              <IconDownload size={SYMBOL_TASTE} stroke={1.8} />
            </TastenSymbol>
          </Button>
        )}
      </Box>
    </Box>
  );
}

/** Ein abrechenbares Angebot als Zeile der Wahl: Firma, Angebotsdatum, offener Betrag. */
function Wahlzeile({
  angebot,
  laeuft,
  waehlen,
}: {
  readonly angebot: AbrechenbaresAngebot;
  readonly laeuft: boolean;
  readonly waehlen: () => void;
}) {
  return (
    <Box
      component="button"
      type="button"
      onClick={waehlen}
      disabled={laeuft}
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        flexWrap: 'wrap',
        width: '100%',
        padding: '12px 14px',
        border: 0,
        cursor: 'pointer',
        textAlign: 'left',
        fontFamily: 'inherit',
        fontSize: 13.5,
        color: theme.vars.palette.kupferwolke.text,
        borderRadius: `${RADIUS_KLEIN}px`,
        background: 'transparent',
        '&:hover': { background: theme.vars.palette.kupferwolke.flaecheWeich },
        '&:disabled': { cursor: 'default' },
      })}
    >
      <Box component="span" sx={{ fontWeight: 600, flex: '1 1 160px' }}>
        {angebot.firmaName}
      </Box>
      <Box
        component="span"
        className={ZAHLEN_KLASSE}
        sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {tagWort(angebot.angebotDatum)}
      </Box>
      <Box
        component="span"
        className={ZAHLEN_KLASSE}
        sx={{ fontWeight: 600, marginLeft: 'auto', whiteSpace: 'nowrap' }}
      >
        {euro(angebot.offenerBetragInCent)}
      </Box>
    </Box>
  );
}

/** Was in der Karte steht: Ladehinweis, Meldung, Satz zur Leere oder die Tafel. */
function inhaltZu(stand: Stand): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {LAEDT}
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  if (stand.uebersicht.rechnungen.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {LEER}
      </Typography>
    );
  }
  return (
    <Tafel beschriftung="Rechnungen" spalten={SPALTEN}>
      {stand.uebersicht.rechnungen.map((rechnung) => (
        <Zeile key={rechnung.id} rechnung={rechnung} />
      ))}
    </Tafel>
  );
}

export default function RechnungenPage() {
  useKopfPfad(KEIN_WEG, 'Rechnungen');
  const navigate = useNavigate();
  const titelId = useId();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [wahlOffen, setzeWahlOffen] = useState(false);
  const [wahlstand, setzeWahlstand] = useState<Wahlstand>({ art: 'laedt' });
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);

  useEffect(() => {
    let gueltig = true;
    void laden().then((neu) => {
      if (gueltig) {
        setzeStand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, []);

  useEffect(() => {
    if (!wahlOffen) {
      return;
    }
    let gueltig = true;
    setzeWahlstand({ art: 'laedt' });
    void wahlLaden().then((neu) => {
      // Die Wahl wurde geschlossen, bevor die Antwort kam; sie schreibt dann nichts mehr.
      if (gueltig) {
        setzeWahlstand(neu);
      }
    });
    return () => {
      gueltig = false;
    };
  }, [wahlOffen]);

  const schliessen = () => {
    setzeWahlOffen(false);
    setzeMeldung(null);
  };

  /** Die Wahl eines Angebots: Der Entwurf entsteht am Server, die Antwort nennt seine Kennung. */
  const waehlen = async (angebotId: number) => {
    setzeMeldung(null);
    setzeLaeuft(true);
    try {
      const entwurf = await rechnungAnlegen(angebotId);
      navigate(`/rechnungen/${String(entwurf.id)}`);
    } catch (ursache: unknown) {
      // Die Meldung des Servers, wo er eine schickt — er allein weiss, warum er abgewiesen hat.
      setzeMeldung(ursache instanceof ApiError ? ursache.message : ALLGEMEINE_MELDUNG);
    } finally {
      setzeLaeuft(false);
    }
  };

  let wahlinhalt: ReactNode;
  if (wahlstand.art === 'laedt') {
    wahlinhalt = (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        {WAHL_LAEDT}
      </Typography>
    );
  } else if (wahlstand.art === 'fehler') {
    wahlinhalt = <Alert severity="error">{WAHL_AUSFALL}</Alert>;
  } else if (wahlstand.wahl.angebote.length === 0) {
    wahlinhalt = (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textMatt })}
      >
        {WAHL_LEER}
      </Typography>
    );
  } else {
    wahlinhalt = (
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
        {wahlstand.wahl.angebote.map((angebot) => (
          <Wahlzeile
            key={angebot.angebotId}
            angebot={angebot}
            laeuft={laeuft}
            waehlen={() => {
              void waehlen(angebot.angebotId);
            }}
          />
        ))}
      </Box>
    );
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      <Karte
        titel="Rechnungen"
        titelEbene={1}
        werkzeug={
          <KupferTaste
            onClick={() => {
              setzeWahlOffen(true);
            }}
            disabled={laeuft}
            symbol={<IconFilePlus size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Neue Rechnung
          </KupferTaste>
        }
      >
        {inhaltZu(stand)}
      </Karte>
      {wahlOffen ? (
        <Dialog open onClose={schliessen} fullWidth maxWidth="sm" aria-labelledby={titelId}>
          <DialogTitle id={titelId} sx={{ fontSize: 16 }}>
            Neue Rechnung
          </DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
            {wahlinhalt}
          </DialogContent>
          <DialogActions sx={{ padding: '4px 24px 20px' }}>
            <WeicheTaste onClick={schliessen} disabled={laeuft}>
              Abbrechen
            </WeicheTaste>
          </DialogActions>
        </Dialog>
      ) : null}
    </Box>
  );
}
