import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { IconArrowLeft, IconArrowRight, IconPencil } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';

import { angebotLesen, angebotStatusWeiter, angebotStatusZurueck } from '../api/angebote';
import type { Angebot, AngebotPosition } from '../api/angebote';
import AngebotsstatusChip from '../components/AngebotsstatusChip';
import Anlagen from '../components/Anlagen';
import Karte from '../components/Karte';
import Kommentare from '../components/Kommentare';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import { EINHEIT_WORT, MODUS_WORT } from '../components/Positionsmaske';
import Tafel from '../components/Tafel';
import WeicheTaste from '../components/WeicheTaste';
import { nichtGefunden } from '../lib/apifehler';
import { dezimal, euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Ansicht eines Angebots (Issue #127).
 *
 * Oben die Karte mit der Ueberschrift, den Angaben und den Aktionen; darunter die Beschreibung,
 * sofern es eine gibt, die Positionen mit Summe, die Anlagen und zuletzt die Kommentare (Vorlage
 * `.karte` Z. 53, `.kopfzeile` Z. 79–80, `.stamm` Z. 100–102).
 *
 * <b>Anlagen und Kommentare laden sich selbst</b> ({@link Anlagen}, Issue #148; {@link Kommentare},
 * Issue #146): Die Seite gibt nur die Kennung weiter. Ein Ausfall eines der beiden Wege steht darum
 * in dessen Karte und laesst Ueberschrift, Angaben und Positionen stehen.
 *
 * <b>Die Aktionen.</b> „Status weiter" ist die eine Kupfertaste — der gewoehnliche naechste Schritt.
 * „Status zurueck" und „Bearbeiten" stehen weich daneben (CLAUDE-design.md, Leitgedanke 2). An den
 * Enden der Reihe fehlt die Taste, die ins Leere fuehrte: bei „angelegt" die zurueck, bei
 * „abgerechnet" die weiter. Bearbeiten laesst sich in jedem Status (Kriterium 5).
 *
 * <b>Nach jedem Statuswechsel gilt die Antwort</b>, nicht ein selbst umgeschalteter Status: Was der
 * Server fuehrt, ist die Wahrheit.
 */

const NICHT_GEFUNDEN = 'Dieses Angebot gibt es nicht.';
const AUSFALL = 'Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_AKTION = 'Der Status wurde nicht geändert. Bitte später erneut versuchen.';
const LAEDT = 'Das Angebot wird geladen …';
const OHNE_POSITION = 'Noch keine Position.';
const NETTO = 'Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer';

/** Die Symbolgroesse in den Tasten (wie in {@link FirmaPage}). */
const SYMBOL_TASTE = 16;

const SPALTEN = ['Bezeichnung', 'Abrechnung', 'Menge', 'Einheit', 'Einzelpreis', 'Betrag'] as const;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly angebot: Angebot }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die Ueberschrift: ein Angebot hat keine Nummer, es heisst nach seinem Datum. */
function ueberschriftZu(angebot: Angebot): string {
  return `Angebot vom ${tagWort(angebot.angebotDatum)}`;
}

/** Eine Zeile der Positionstafel (Kriterien 4, 5). */
function Positionszeile({ position }: { readonly position: AngebotPosition }) {
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {position.bezeichnung}
      </Box>
      <Box component="td">{MODUS_WORT[position.abrechnungsmodus]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {dezimal(position.mengeInHundertsteln, ',')}
      </Box>
      <Box component="td">{EINHEIT_WORT[position.einheit]}</Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap' }}
      >
        {euro(position.einzelpreisInCent)}
      </Box>
      <Box
        component="td"
        className={ZAHLEN_KLASSE}
        sx={{ textAlign: 'right', whiteSpace: 'nowrap', fontWeight: 600 }}
      >
        {euro(position.betragInCent)}
      </Box>
    </Box>
  );
}

/** Die Angaben des Angebots als Stammdaten-Liste (Vorlage `.stamm` Z. 100–102). */
function Angaben({ angebot }: { readonly angebot: Angebot }) {
  const zeilen: readonly { name: string; wert: ReactNode }[] = [
    {
      name: 'Firma',
      wert: (
        <Link
          component={RouterLink}
          to={`/firmen/${String(angebot.firmaId)}`}
          underline="hover"
          sx={{ fontSize: 13.5, fontWeight: 500 }}
        >
          {angebot.firmaName}
        </Link>
      ),
    },
    // Der Ansprechpartner ist optional (Issue #126); ohne ihn steht die Zeile gar nicht da.
    ...(angebot.ansprechpartnerName === null
      ? []
      : [{ name: 'Ansprechpartner', wert: angebot.ansprechpartnerName }]),
    { name: 'Angebotsdatum', wert: tagWort(angebot.angebotDatum) },
    { name: 'Status', wert: <AngebotsstatusChip status={angebot.status} /> },
  ];
  return (
    <Box
      component="dl"
      data-testid="angebot-angaben"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
        alignItems: 'center',
        gap: '10px 20px',
        margin: 0,
        fontSize: 13.5,
      }}
    >
      {zeilen.map((zeile) => (
        <Fragment key={zeile.name}>
          <Box
            component="dt"
            sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {zeile.name}
          </Box>
          <Box component="dd" sx={{ margin: 0, fontWeight: 500 }}>
            {zeile.wert}
          </Box>
        </Fragment>
      ))}
    </Box>
  );
}

/** Ueber jeder Angebotsansicht stehen die Firmen und — sobald bekannt — die Firma (E6). */
const ZU_FIRMEN: PfadVerweis = { titel: 'Firmen', ziel: '/firmen' };

export default function AngebotPage() {
  const { angebotId } = useParams();
  const kennung = kennungAus(angebotId);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  // Solange das Angebot nicht gelesen ist, traegt die Endstufe das Wort „Angebot"; die Firma kommt
  // mit dem Angebot, denn erst das Angebot weiss, an wen es geht.
  useKopfPfad(
    stand.art === 'daten'
      ? [
          ZU_FIRMEN,
          {
            titel: stand.angebot.firmaName,
            ziel: `/firmen/${String(stand.angebot.firmaId)}`,
          },
        ]
      : [ZU_FIRMEN],
    stand.art === 'daten' ? ueberschriftZu(stand.angebot) : 'Angebot',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void angebotLesen(kennung)
      .then((angebot) => {
        setzeStand({ art: 'daten', angebot });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  /** Ein Statuswechsel: Der neue Status kommt aus der Antwort, nicht aus der Oberflaeche. */
  const schalten = async (weg: (id: number) => Promise<Angebot>, angebot: Angebot) => {
    setzeMeldung(null);
    setzeLaeuft(true);
    try {
      setzeStand({ art: 'daten', angebot: await weg(angebot.id) });
    } catch {
      setzeMeldung(AUSFALL_AKTION);
    } finally {
      setzeLaeuft(false);
    }
  };

  /** Die Aktionen neben der Ueberschrift; an den Enden der Reihe fehlt die jeweilige Taste. */
  function aktionenZu(angebot: Angebot): ReactNode {
    return (
      <Box
        data-testid="angebot-aktionen"
        sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
      >
        <WeicheTaste
          to={`/angebote/${String(angebot.id)}/bearbeiten`}
          symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
        >
          Bearbeiten
        </WeicheTaste>
        {angebot.status === 'ANGELEGT' ? null : (
          <WeicheTaste
            onClick={() => {
              void schalten(angebotStatusZurueck, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowLeft size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Status zurück
          </WeicheTaste>
        )}
        {angebot.status === 'ABGERECHNET' ? null : (
          <KupferTaste
            onClick={() => {
              void schalten(angebotStatusWeiter, angebot);
            }}
            disabled={laeuft}
            symbol={<IconArrowRight size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Status weiter
          </KupferTaste>
        )}
      </Box>
    );
  }

  /** Die Karte mit Ueberschrift, Angaben und Aktionen, darunter Beschreibung und Positionen. */
  function inhaltZu(angebot: Angebot): ReactNode {
    return (
      <>
        <Karte titel={ueberschriftZu(angebot)} titelEbene={1} werkzeug={aktionenZu(angebot)}>
          <Angaben angebot={angebot} />
        </Karte>
        {angebot.beschreibung === null ? null : (
          <Karte titel="Beschreibung">
            <Typography sx={{ fontSize: 13.5, whiteSpace: 'pre-wrap' }}>
              {angebot.beschreibung}
            </Typography>
          </Karte>
        )}
        <Karte titel="Positionen" anzahl={angebot.positionen.length}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {angebot.positionen.length === 0 ? (
              <Typography
                role="status"
                sx={(theme) => ({
                  fontSize: 12.5,
                  color: theme.vars.palette.kupferwolke.textMatt,
                })}
              >
                {OHNE_POSITION}
              </Typography>
            ) : (
              <Tafel beschriftung="Positionen" spalten={[...SPALTEN]}>
                {angebot.positionen.map((position) => (
                  // Die Kennung ist der Schluessel: Seit Issue #171 traegt jede Position eine
                  // eigene und bleibt ueber ein Speichern hinweg dieselbe. Die Reihenfolge der
                  // Liste bleibt die gezeigte (E24) — sie ordnet, sie benennt nicht mehr.
                  <Positionszeile key={position.id} position={position} />
                ))}
              </Tafel>
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
              <Typography sx={{ fontSize: 13.5, fontWeight: 600 }}>Summe</Typography>
              <Typography
                data-testid="angebot-summe"
                className={ZAHLEN_KLASSE}
                sx={{ fontSize: 17, fontWeight: 800 }}
              >
                {euro(angebot.summeInCent)}
              </Typography>
              <Typography
                sx={(theme) => ({
                  fontSize: 12.5,
                  marginLeft: 'auto',
                  color: theme.vars.palette.kupferwolke.textSchwach,
                })}
              >
                {NETTO}
              </Typography>
            </Box>
          </Box>
        </Karte>
        <Anlagen angebotId={angebot.id} />
        <Kommentare angebotId={angebot.id} />
      </>
    );
  }

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    inhalt = inhaltZu(stand.angebot);
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Karte>
        <Typography
          sx={(theme) => ({
            fontSize: 12.5,
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {LAEDT}
        </Typography>
      </Karte>
    );
  } else {
    inhalt = (
      <Karte>
        <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
      </Karte>
    );
  }

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        // Aussenabstand und Spalt bringt der Rahmen mit; hier bleibt nur der Abstand zwischen
        // den Bereichen der Buehne (CLAUDE-design.md, „Rahmen").
        gap: '22px',
      }}
    >
      {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
      {inhalt}
    </Box>
  );
}
