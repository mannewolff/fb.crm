import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import {
  IconCircleCheck,
  IconCircleX,
  IconDownload,
  IconPencil,
  IconRotate2,
  IconTrash,
} from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import {
  nachtragDokumentPfad,
  nachtragLesen,
  nachtragLoeschen,
  setzeNachtragszustand,
} from '../api/nachtraege';
import type { Nachtrag } from '../api/nachtraege';
import AktionsMenue from '../components/AktionsMenue';
import type { AktionsEintrag } from '../components/AktionsMenue';
import Karte from '../components/Karte';
import Kopfkarte from '../components/Kopfkarte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import { kupferSx } from '../components/KupferTaste';
import NachgetragenChip from '../components/NachgetragenChip';
import RechnungszustandChip from '../components/RechnungszustandChip';
import TastenSymbol from '../components/TastenSymbol';
import WeicheTaste from '../components/WeicheTaste';
import { nichtGefunden, serverMeldung } from '../lib/apifehler';
import { euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import type { Rechnungszustand } from '../lib/rechnungszustand';
import { tagWort } from '../lib/tag';
import { ABSTAND_BUEHNE, ZAHLEN_KLASSE } from '../theme';

/**
 * Die Einzelansicht der nachgetragenen Rechnung unter `/rechnungen/nachgetragen/:id` (#254,
 * Kriterien 6, 7, 8, 10, 11; Plan #259, E21, E22, E24).
 *
 * Oben die {@link Kopfkarte} nach der Heldenkarte der Vorlage: Nummer als Titel, die Firma als
 * Verweis auf ihre Seite (Kriterium 11), darunter der Zustands-Chip und **daneben** der Chip
 * „nachgetragen" (Kriterium 6, E22). Rechts die Aktionen in der Reihenfolge der Vorlage — weiche
 * Tasten, die eine Kupfertaste, das ⋯-Menue. Darunter Rechnungsdatum, Netto und Brutto, so wie sie
 * eingegeben wurden (Kriterium 3).
 *
 * <b>„Herunterladen" gibt es nur mit hinterlegtem Dokument</b> (Kriterium 7, E17): ein `<a download>`
 * auf genau dieses Original. Ohne Dokument steht an seiner Stelle der Satz „Kein Dokument
 * hinterlegt" — ein Verweis, der mit 404 antwortet, waere eine Taste, die nichts tut.
 *
 * <b>Der Zustand geht ueber den eigenen Weg</b> der nachgetragenen Rechnung (E24), mit denselben
 * Regeln wie bei der gestellten Rechnung (#253): Eine gestellte bietet „Als bezahlt markieren" und
 * „Abschreiben" an, eine bezahlte oder abgeschriebene den Weg zurueck. Welcher Uebergang zulaessig
 * ist, entscheidet der Server; ein abgewiesener erscheint als Meldung, die Seite bleibt stehen.
 *
 * <b>„Abschreiben" und „Löschen" stehen im ⋯-Menue</b> mit Rueckfrage (CLAUDE-design.md,
 * „Tasten"): Beides ist folgenreich, und die Rueckfrage des Menues ist der vorhandene Baustein dafuer.
 * Nach dem Loeschen fuehrt die Ansicht auf die Rechnungsliste, den fachlichen Ort der Rechnung (E21).
 *
 * Laden, „gibt es nicht" (404) und Ausfall wie in {@link RechnungPage}: Eine Kennung, die keine ist,
 * geht gar nicht erst ans Netz (`lib/kennung.ts`).
 */

const NICHT_GEFUNDEN = 'Diese nachgetragene Rechnung gibt es nicht.';
const AUSFALL = 'Die Rechnung ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_ZUSTAND = 'Der Zustand wurde nicht geändert. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Die Rechnung wurde nicht gelöscht. Bitte später erneut versuchen.';
const LAEDT = 'Die Rechnung wird geladen …';
const KEIN_DOKUMENT = 'Kein Dokument hinterlegt';
const BEZAHLT = 'Als bezahlt markieren';
const ABSCHREIBEN = 'Abschreiben';
const ZURUECK = 'Zurück auf gestellt';
const ABSCHREIBEN_FRAGE =
  'Die Forderung gilt damit als uneinbringlich. Die Rechnung bleibt mit Nummer und Dokument bestehen, und der Schritt lässt sich zurücknehmen.';
const LOESCHEN_FRAGE =
  'Die nachgetragene Rechnung wird samt ihrem hinterlegten Dokument gelöscht. Das lässt sich nicht zurücknehmen.';

/** Die Symbolgroesse in den Tasten (wie in {@link RechnungPage}). */
const SYMBOL_TASTE = 16;

/** Ueber jeder Rechnungsansicht steht die Liste der Rechnungen. */
const ZU_RECHNUNGEN: PfadVerweis = { titel: 'Rechnungen', ziel: '/rechnungen' };

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly nachtrag: Nachtrag }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die Ueberschrift: die frei eingegebene Nummer (Kriterium 2). */
function ueberschriftZu(nachtrag: Nachtrag): string {
  return `Rechnung ${nachtrag.nummer}`;
}

/** Die Angaben unter der Kopfkarte (CLAUDE-design.md, „Stammdaten"). */
function Angaben({ nachtrag }: { readonly nachtrag: Nachtrag }) {
  const eintraege: readonly { readonly name: string; readonly wert: string }[] = [
    { name: 'Rechnungsdatum', wert: tagWort(nachtrag.rechnungDatum) },
    { name: 'Nettobetrag', wert: euro(nachtrag.nettoInCent) },
    { name: 'Bruttobetrag', wert: euro(nachtrag.bruttoInCent) },
  ];
  return (
    <Box
      component="dl"
      data-testid="nachtrag-angaben"
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
        alignItems: 'center',
        gap: '10px 20px',
        margin: 0,
        fontSize: 13.5,
      }}
    >
      {eintraege.map((eintrag) => (
        <Fragment key={eintrag.name}>
          <Box
            component="dt"
            sx={(theme) => ({ color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {eintrag.name}
          </Box>
          <Box component="dd" className={ZAHLEN_KLASSE} sx={{ margin: 0, fontWeight: 500 }}>
            {eintrag.wert}
          </Box>
        </Fragment>
      ))}
    </Box>
  );
}

export default function NachgetrageneRechnungPage() {
  const { id } = useParams();
  const kennung = kennungAus(id);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [fehler, setzeFehler] = useState<string | null>(null);
  const [laeuft, setzeLaeuft] = useState(false);
  useKopfPfad(
    [ZU_RECHNUNGEN],
    stand.art === 'daten' ? ueberschriftZu(stand.nachtrag) : 'Rechnung',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void nachtragLesen(kennung)
      .then((nachtrag) => {
        setzeStand({ art: 'daten', nachtrag });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  /** Stellt den Zustand ueber den eigenen Weg um (E24); die Antwort ist der neue Stand. */
  const umstellen = async (nachtrag: Nachtrag, ziel: Rechnungszustand) => {
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      setzeStand({ art: 'daten', nachtrag: await setzeNachtragszustand(nachtrag.id, ziel) });
    } catch (ursache) {
      setzeFehler(serverMeldung(ursache, AUSFALL_ZUSTAND));
    } finally {
      setzeLaeuft(false);
    }
  };

  const loeschen = async (nachtrag: Nachtrag) => {
    setzeFehler(null);
    setzeLaeuft(true);
    try {
      await nachtragLoeschen(nachtrag.id);
      navigate('/rechnungen');
    } catch {
      setzeFehler(AUSFALL_LOESCHEN);
      setzeLaeuft(false);
    }
  };

  /** Die Eintraege des ⋯-Menues: „Abschreiben" nur an einer gestellten, „Löschen" immer. */
  function menueZu(nachtrag: Nachtrag): readonly AktionsEintrag[] {
    const loeschEintrag: AktionsEintrag = {
      titel: 'Löschen',
      symbol: <IconTrash size={SYMBOL_TASTE} stroke={1.8} />,
      rueckfrage: LOESCHEN_FRAGE,
      onAuswahl: () => {
        void loeschen(nachtrag);
      },
    };
    if (nachtrag.zustand !== 'GESTELLT') {
      return [loeschEintrag];
    }
    return [
      {
        titel: ABSCHREIBEN,
        symbol: <IconCircleX size={SYMBOL_TASTE} stroke={1.8} />,
        rueckfrage: ABSCHREIBEN_FRAGE,
        onAuswahl: () => {
          void umstellen(nachtrag, 'ABGESCHRIEBEN');
        },
      },
      loeschEintrag,
    ];
  }

  /** Die Aktionen der Kopfkarte: weich, dann Kupfer, dann das ⋯-Menue (Vorlage Z. 144–148). */
  function aktionenZu(nachtrag: Nachtrag): ReactNode {
    const gestellt = nachtrag.zustand === 'GESTELLT';
    return (
      <>
        <WeicheTaste
          to={`/rechnungen/nachgetragen/${String(nachtrag.id)}/bearbeiten`}
          symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
        >
          Bearbeiten
        </WeicheTaste>
        <WeicheTaste
          onClick={() => {
            void umstellen(nachtrag, gestellt ? 'BEZAHLT' : 'GESTELLT');
          }}
          disabled={laeuft}
          symbol={
            gestellt ? (
              <IconCircleCheck size={SYMBOL_TASTE} stroke={1.8} />
            ) : (
              <IconRotate2 size={SYMBOL_TASTE} stroke={1.8} />
            )
          }
        >
          {gestellt ? BEZAHLT : ZURUECK}
        </WeicheTaste>
        {nachtrag.dokument ? (
          <Button
            component="a"
            href={nachtragDokumentPfad(nachtrag.id)}
            download
            sx={kupferSx}
          >
            <TastenSymbol>
              <IconDownload size={SYMBOL_TASTE} stroke={1.8} />
            </TastenSymbol>
            Herunterladen
          </Button>
        ) : (
          <Typography
            sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
          >
            {KEIN_DOKUMENT}
          </Typography>
        )}
        <AktionsMenue
          name="Aktionen für diese Rechnung"
          objekt={ueberschriftZu(nachtrag)}
          eintraege={menueZu(nachtrag)}
        />
      </>
    );
  }

  /** Der Rahmen jeder Bereitschaft: der Abstand zwischen den Karten der Buehne. */
  const spalten = { display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE } as const;

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

  if (stand.art !== 'daten') {
    return (
      <Box sx={spalten}>
        <Karte>
          <Alert severity="error">{stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}</Alert>
        </Karte>
      </Box>
    );
  }

  const { nachtrag } = stand;
  return (
    <Box sx={spalten}>
      {fehler === null ? null : <Alert severity="error">{fehler}</Alert>}
      <Kopfkarte
        malName={nachtrag.firmaName}
        titel={ueberschriftZu(nachtrag)}
        zeile={
          <Link
            component={RouterLink}
            to={`/firmen/${String(nachtrag.firmaId)}`}
            underline="hover"
            sx={{ fontWeight: 500 }}
          >
            {nachtrag.firmaName}
          </Link>
        }
        chips={
          <>
            <RechnungszustandChip zustand={nachtrag.zustand} />
            <NachgetragenChip />
          </>
        }
        aktionen={aktionenZu(nachtrag)}
      />
      <Karte titel="Angaben zur Rechnung">
        <Angaben nachtrag={nachtrag} />
      </Karte>
    </Box>
  );
}
