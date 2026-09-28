import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { IconPencil, IconTrash } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import { auftragLesen, auftragLoeschen } from '../api/auftraege';
import type { Auftrag, AuftragPosition } from '../api/auftraege';
import AktionsMenue from '../components/AktionsMenue';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import { EINHEIT_WORT, MODUS_WORT } from '../components/Positionsmaske';
import Tafel from '../components/Tafel';
import ZustandsChip from '../components/ZustandsChip';
import { nichtGefunden } from '../lib/apifehler';
import { auftragsstatusBild } from '../lib/auftragsstatus';
import { dezimal, euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { tagWort, zeitraumWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Ansicht eines Auftrags (Kriterien 3, 6, 7, 15).
 *
 * Oben die Karte mit der Ueberschrift, dem Statuschip und den Aktionen; darunter im Raster links
 * die Positionen mit Summe, rechts die Karte „Felder" als Angabenliste (Vorlage `.karte` Z. 53,
 * `.kopfzeile` Z. 79–80, `.raster` Z. 76, `.stamm` Z. 100–102) — dieselben Stellen, an denen die
 * Angebotsansicht abgenommen wurde.
 *
 * <b>„Bearbeiten" ist die eine Kupfertaste</b>, „Löschen" liegt im ⋯-Menue mit Rueckfrage
 * (Plan E15; CLAUDE-design.md, Leitgedanke 2): Der Server prueft vor dem Loeschen nichts ausser
 * der Existenz, die Frage ist also die einzige Bremse.
 *
 * <b>Der Weg zum Angebot zeigt ein Wort</b> (Fund 11 der Plan-Pruefung): „Angebot A-2026-001" aus
 * `angebotNummer`, nicht die Kennung.
 *
 * <b>Stunden je Personentag</b> stehen nur an Aufwandspositionen (Kriterium 4); an einer
 * Festpreisposition steht ein Strich mit dem zugaenglichen Namen „keine Angabe".
 */

const NICHT_GEFUNDEN = 'Diesen Auftrag gibt es nicht.';
const AUSFALL = 'Der Auftrag ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_LOESCHEN = 'Der Auftrag wurde nicht gelöscht. Bitte später erneut versuchen.';
const LAEDT = 'Der Auftrag wird geladen …';
const OHNE_POSITION = 'Keine Position.';
const NETTO = 'Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer';
const KEINE = 'keine';
const LOESCHEN_FRAGE =
  'Der Auftrag verschwindet samt seinen Positionen, seine Nummer bleibt verbraucht. Das Angebot bleibt angenommen. Das lässt sich nicht zurücknehmen.';

const SYMBOL_CHIP = 13;
const SYMBOL_TASTE = 16;

const SPALTEN = [
  'Bezeichnung',
  'Abrechnung',
  'Menge',
  'Einheit',
  'Einzelpreis',
  'Std. je PT',
  'Betrag',
] as const;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly auftrag: Auftrag }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Eine Zeile der Positionstafel (Kriterien 2, 4, 5). */
function Positionszeile({ position }: { readonly position: AuftragPosition }) {
  const zahl = { textAlign: 'right', whiteSpace: 'nowrap' } as const;
  return (
    <Box component="tr">
      <Box component="td" sx={{ fontWeight: 500 }}>
        {position.bezeichnung}
      </Box>
      <Box component="td">{MODUS_WORT[position.abrechnungsmodus]}</Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={zahl}>
        {dezimal(position.mengeInHundertsteln, ',')}
      </Box>
      <Box component="td">{EINHEIT_WORT[position.einheit]}</Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={zahl}>
        {euro(position.einzelpreisInCent)}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={zahl}>
        {position.stundenJePersonentagInHundertsteln === null ? (
          <span aria-label="keine Angabe">—</span>
        ) : (
          dezimal(position.stundenJePersonentagInHundertsteln, ',')
        )}
      </Box>
      <Box component="td" className={ZAHLEN_KLASSE} sx={{ ...zahl, fontWeight: 600 }}>
        {euro(position.betragInCent)}
      </Box>
    </Box>
  );
}

/** Die Angaben des Auftrags als Stammdaten-Liste (Vorlage `.stamm` Z. 100–102). */
function Felderkarte({ auftrag }: { readonly auftrag: Auftrag }) {
  const zeilen: readonly { name: string; wert: ReactNode }[] = [
    { name: 'Auftragsdatum', wert: tagWort(auftrag.auftragDatum) },
    { name: 'Kundenbestellnummer', wert: auftrag.kundenbestellnummer ?? KEINE },
    { name: 'Leistungszeitraum', wert: zeitraumWort(auftrag.leistungAb, auftrag.leistungBis) },
    {
      name: 'Entstanden aus',
      wert: (
        <Link
          component={RouterLink}
          to={`/vorgaenge/${String(auftrag.vorgangId)}/angebote/${String(auftrag.angebotId)}`}
          underline="hover"
          sx={{ fontSize: 13.5, fontWeight: 500 }}
        >
          {auftrag.angebotNummer === null ? 'Angebot' : `Angebot ${auftrag.angebotNummer}`}
        </Link>
      ),
    },
  ];
  return (
    <Karte titel="Felder">
      <Box
        component="dl"
        data-testid="auftrag-felder"
        sx={{
          display: 'grid',
          gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'auto minmax(0, 1fr)' },
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
    </Karte>
  );
}

/** Ueber jeder Auftragsansicht stehen die Uebersicht und der Vorgang (Plan E16). */
const ZU_VORGAENGEN: PfadVerweis = { titel: 'Vorgänge', ziel: '/vorgaenge' };

export default function AuftragPage() {
  const { id, auftragId } = useParams();
  const vorgangKennung = kennungAus(id);
  const kennung = kennungAus(auftragId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [meldung, setzeMeldung] = useState<string | null>(null);
  // Solange der Auftrag nicht gelesen ist, traegt die Endstufe das Wort „Auftrag": Ein Pfad, der
  // erst spaeter erscheint, liesse den Kopf bei jedem Aufruf einmal springen.
  useKopfPfad(
    [ZU_VORGAENGEN, { titel: 'Vorgang', ziel: `/vorgaenge/${String(vorgangKennung)}` }],
    stand.art === 'daten' ? `Auftrag ${stand.auftrag.nummer}` : 'Auftrag',
  );

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    void auftragLesen(kennung)
      .then((auftrag) => {
        setzeStand({ art: 'daten', auftrag });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  const loeschen = async (auftrag: Auftrag) => {
    setzeMeldung(null);
    try {
      await auftragLoeschen(auftrag.id);
      // Der Auftrag ist weg — auf seiner Adresse gibt es nichts mehr zu sehen, also `replace`.
      navigate(`/vorgaenge/${String(auftrag.vorgangId)}`, { replace: true });
    } catch {
      setzeMeldung(AUSFALL_LOESCHEN);
    }
  };

  function inhaltZu(auftrag: Auftrag): ReactNode {
    const bild = auftragsstatusBild(auftrag.status);
    const Statussymbol = bild.symbol;
    const ueberschrift = `Auftrag ${auftrag.nummer}`;
    return (
      <>
        <Karte
          titel={ueberschrift}
          titelEbene={1}
          werkzeug={
            <Box
              data-testid="auftrag-aktionen"
              sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
            >
              <KupferTaste
                to={`/vorgaenge/${String(auftrag.vorgangId)}/auftraege/${String(auftrag.id)}/bearbeiten`}
                symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
              >
                Bearbeiten
              </KupferTaste>
              <AktionsMenue
                name="Weitere Aktionen"
                objekt={ueberschrift}
                eintraege={[
                  {
                    titel: 'Löschen',
                    rueckfrage: LOESCHEN_FRAGE,
                    symbol: <IconTrash size={SYMBOL_TASTE} stroke={1.8} />,
                    onAuswahl: () => {
                      void loeschen(auftrag);
                    },
                  },
                ]}
              />
            </Box>
          }
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <ZustandsChip
              wort={bild.wort}
              toenung={bild.toenung}
              symbol={<Statussymbol size={SYMBOL_CHIP} stroke={1.8} />}
            />
            <Typography
              sx={(theme) => ({ fontSize: 13, color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {`Auftrag vom ${tagWort(auftrag.auftragDatum)}`}
            </Typography>
          </Box>
        </Karte>
        <Box
          sx={{
            display: 'grid',
            // Die Positionen sind die breitere Seite; unter 1200 px liegen beide uebereinander.
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1.6fr) minmax(0, 1fr)' },
            gap: '22px',
            alignItems: 'start',
          }}
        >
          <Karte titel="Positionen" anzahl={auftrag.positionen.length}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
              {auftrag.positionen.length === 0 ? (
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
                  {auftrag.positionen.map((position, stelle) => (
                    // Die Reihenfolge ist der Schluessel: Eine Position traegt keine eigene
                    // Kennung, ihre Stelle in der Liste ist ihre Identitaet.
                    <Positionszeile key={stelle} position={position} />
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
                  data-testid="auftrag-summe"
                  className={ZAHLEN_KLASSE}
                  sx={{ fontSize: 17, fontWeight: 800 }}
                >
                  {euro(auftrag.summeInCent)}
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
          <Felderkarte auftrag={auftrag} />
        </Box>
      </>
    );
  }

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    inhalt = inhaltZu(stand.auftrag);
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Karte>
        <Typography
          sx={(theme) => ({ fontSize: 12.5, color: theme.vars.palette.kupferwolke.textSchwach })}
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
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: '22px' }}>
      {meldung === null ? null : <Alert severity="error">{meldung}</Alert>}
      {inhalt}
    </Box>
  );
}
