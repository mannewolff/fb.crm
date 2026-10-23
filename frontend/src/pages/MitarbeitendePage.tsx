import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import {
  IconArchive,
  IconArchiveOff,
  IconCircleCheck,
  IconPencil,
  IconUserPlus,
} from '@tabler/icons-react';
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

import {
  mitarbeiterAktivieren,
  mitarbeiterStilllegen,
  mitarbeitendeListe,
} from '../api/mitarbeitende';
import type { Mitarbeiter } from '../api/mitarbeitende';
import { rollenname } from '../auth/rechte';
import AktionsMenue from '../components/AktionsMenue';
import type { AktionsEintrag } from '../components/AktionsMenue';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import Tafel from '../components/Tafel';
import ZustandsChip from '../components/ZustandsChip';
import { konflikt, serverMeldung } from '../lib/apifehler';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Ansicht „Mitarbeitende": jedes Konto mit Namen, Adresse, Rolle und Zustand (Plan #414, A12;
 * fachliche Quelle #409, Kriterien 3, 7 und 8).
 *
 * Gebaut wie die Uebersicht der Firmen — {@link Tafel} in einer {@link Karte}, die Hauptaktion
 * „Neues Konto" rechts im Kartenkopf. Je Zeile ein ⋯-Menue mit „Bearbeiten" und „Stilllegen"
 * beziehungsweise „Wieder aktivieren"; nur das Stilllegen fragt vorher nach, Wiederaktivieren
 * nimmt nichts weg (CLAUDE-design.md, „Tasten").
 *
 * Nach dem Schalten liest die Ansicht die Liste neu, statt den Stand selbst umzuschalten: Der
 * Server ist die Quelle der Wahrheit. Weist er ab — etwa beim letzten aktiven C-Level —, steht sein
 * Satz als Meldung ueber der Tafel; den Grund kennt nur er (Kriterium 8).
 *
 * Die Rolle steht mit ihrem Anzeigenamen da, aus {@link rollenname} (E8) — nie als Wert der API.
 */

/** Die Ansicht ist die erste Stufe des Pfades — ueber ihr steht nichts. */
const KEIN_WEG: readonly PfadVerweis[] = [];

const AUSFALL = 'Die Konten sind gerade nicht zu erreichen. Bitte später erneut versuchen.';
const SCHALTEN_FEHLT = 'Der Stand des Kontos wurde nicht geändert. Bitte später erneut versuchen.';

/** Der Satz der Rueckfrage — er erklaert die Folge, nicht die Aktion (Kriterium 7). */
const STILLLEGEN_FRAGE =
  'Die Person ist sofort abgemeldet und kann sich nicht mehr anmelden. Ihre Kommentare bleiben stehen.';

const SPALTEN: readonly string[] = ['Name', 'E-Mail-Adresse', 'Rolle', 'Zustand', 'Aktionen'];

/** 13 px im Chip, 16 px in Tasten und Menueeintraegen — wie in der Detailansicht der Firma. */
const SYMBOL_KLEIN = 13;
const SYMBOL_TASTE = 16;

type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly konten: readonly Mitarbeiter[] }
  | { readonly art: 'fehler' };

/** Der Zustand als Wort, nicht als zweite Farbe (CLAUDE-design.md, „Zustandsformen"). */
function zustandsChip(aktiv: boolean) {
  return aktiv ? (
    <ZustandsChip
      wort="Aktiv"
      toenung="salbei"
      symbol={<IconCircleCheck size={SYMBOL_KLEIN} stroke={1.8} />}
    />
  ) : (
    <ZustandsChip
      wort="Stillgelegt"
      toenung="rose"
      symbol={<IconArchive size={SYMBOL_KLEIN} stroke={1.8} />}
    />
  );
}

/** Stilllegen mit Rueckfrage, Wiederaktivieren ohne. */
function schaltEintrag(aktiv: boolean, onAuswahl: () => void): AktionsEintrag {
  return aktiv
    ? {
        titel: 'Stilllegen',
        symbol: <IconArchive size={SYMBOL_TASTE} stroke={1.8} />,
        rueckfrage: STILLLEGEN_FRAGE,
        onAuswahl,
      }
    : {
        titel: 'Wieder aktivieren',
        symbol: <IconArchiveOff size={SYMBOL_TASTE} stroke={1.8} />,
        onAuswahl,
      };
}

/**
 * Eine Zeile der Tafel. Das ⋯-Menue traegt den Namen in seiner Benennung — eine Reihe gleich
 * benannter Tasten „Aktionen" waere mit dem Screenreader nicht zu unterscheiden.
 */
function Zeile({
  konto,
  schalte,
}: {
  readonly konto: Mitarbeiter;
  readonly schalte: (konto: Mitarbeiter) => void;
}) {
  const navigate = useNavigate();
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {konto.displayName}
      </Box>
      <Box component="td" sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textMatt })}>
        {konto.email}
      </Box>
      <Box component="td">{rollenname(konto.role)}</Box>
      <Box component="td">{zustandsChip(konto.aktiv)}</Box>
      <Box component="td" sx={{ textAlign: 'right' }}>
        <AktionsMenue
          name={`Aktionen für ${konto.displayName}`}
          objekt={konto.displayName}
          eintraege={[
            {
              titel: 'Bearbeiten',
              symbol: <IconPencil size={SYMBOL_TASTE} stroke={1.8} />,
              onAuswahl: () => {
                navigate(`/mitarbeitende/${String(konto.id)}/bearbeiten`);
              },
            },
            schaltEintrag(konto.aktiv, () => {
              schalte(konto);
            }),
          ]}
        />
      </Box>
    </Box>
  );
}

function inhaltZu(stand: Stand, schalte: (konto: Mitarbeiter) => void): ReactNode {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
      >
        Konten werden geladen …
      </Typography>
    );
  }
  if (stand.art === 'fehler') {
    return <Alert severity="error">{AUSFALL}</Alert>;
  }
  return (
    <Tafel beschriftung="Mitarbeitende" spalten={SPALTEN}>
      {stand.konten.map((konto) => (
        <Zeile key={konto.id} konto={konto} schalte={schalte} />
      ))}
    </Tafel>
  );
}

export default function MitarbeitendePage() {
  useKopfPfad(KEIN_WEG, 'Mitarbeitende');
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [meldung, setzeMeldung] = useState<string | null>(null);

  // Ein Neulesen nach dem Schalten laesst die Tafel stehen, bis die Antwort da ist — sonst
  // sprange die Ansicht fuer einen Augenblick auf den Ladehinweis und der Fokus ginge verloren.
  const lesen = useCallback(async () => {
    try {
      setzeStand({ art: 'daten', konten: await mitarbeitendeListe() });
    } catch {
      setzeStand({ art: 'fehler' });
    }
  }, []);

  useEffect(() => {
    void lesen();
  }, [lesen]);

  const schalte = (konto: Mitarbeiter) => {
    setzeMeldung(null);
    const aufruf = konto.aktiv ? mitarbeiterStilllegen : mitarbeiterAktivieren;
    aufruf(konto.id)
      .then(lesen)
      .catch((ursache: unknown) => {
        setzeMeldung(konflikt(ursache) ? serverMeldung(ursache, SCHALTEN_FEHLT) : SCHALTEN_FEHLT);
      });
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE }}>
      <Karte
        titel="Mitarbeitende"
        titelEbene={1}
        werkzeug={
          <KupferTaste to="/mitarbeitende/neu" symbol={<IconUserPlus size={16} stroke={1.8} />}>
            Neues Konto
          </KupferTaste>
        }
      >
        {meldung === null ? null : (
          <Alert severity="error" sx={{ marginBottom: 2 }}>
            {meldung}
          </Alert>
        )}
        {inhaltZu(stand, schalte)}
      </Karte>
    </Box>
  );
}
