import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';
import { IconPencil, IconSend, IconThumbDown, IconThumbUp, IconTrash } from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import {
  angebotAblehnen,
  angebotAnnehmen,
  angebotLesen,
  angebotPdfPfad,
  angebotVersenden,
  angebotVerwerfen,
} from '../api/angebote';
import type { Angebot, AngebotPosition } from '../api/angebote';
import type { FieldErrors } from '../api/client';
import AktionsMenue from '../components/AktionsMenue';
import Karte from '../components/Karte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import { EINHEIT_WORT, MODUS_WORT } from '../components/Positionsmaske';
import Tafel from '../components/Tafel';
import WeicheTaste from '../components/WeicheTaste';
import ZustandsChip from '../components/ZustandsChip';
import { feldMeldungen, nichtGefunden } from '../lib/apifehler';
import { angebotsstandBild } from '../lib/angebotsstand';
import type { Angebotsstand } from '../lib/angebotsstand';
import { dezimal, euro } from '../lib/geld';
import { kennungAus } from '../lib/kennung';
import { tagWort } from '../lib/tag';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Ansicht eines Angebots (Kriterien 5, 6, 7, 10, 12, 13, 14, 17, 18).
 *
 * Oben die Karte mit der Ueberschrift, dem Chip des Standes und den Aktionen; darunter die
 * Positionen mit Summe, zuletzt Texte und Angaben im Raster (Vorlage `.karte` Z. 53, `.kopfzeile`
 * Z. 79–80, `.raster` Z. 76, `.stamm` Z. 100–102).
 *
 * <b>Welche Aktionen daneben stehen, sagt der Stand</b> (Kriterien 13, 17, F13): Der Entwurf traegt
 * „Versenden" als die eine Kupfertaste, „Bearbeiten" daneben und „Verwerfen" im ⋯-Menue mit
 * Rueckfrage — die folgenreiche, seltene Aktion steht nicht gleichrangig neben der taeglichen
 * (CLAUDE-design.md, Leitgedanke 2). Ein versendetes, abgelaufenes oder abgeloestes Angebot traegt
 * „Annehmen" als Kupfertaste und „Ablehnen" als weiche; am angenommenen und am abgelehnten steht
 * keine Aktion mehr, weil beide Zustaende endgueltig sind.
 *
 * <b>Nach jeder Aktion gilt die Antwort</b>, nicht ein selbst umgeschalteter Stand: Alle drei Wege
 * geben das geaenderte Angebot zurueck, und „abgelaufen" entsteht ohnehin gerechnet (E4) — was der
 * Server fuehrt, ist die Wahrheit.
 *
 * <b>Der abgewiesene Versand nennt alle fehlenden Angaben auf einmal</b> (Kriterium 12, E22). Die
 * vier Schluessel `positionen`, `gueltigBis`, `firma` und `eigeneAngaben` werden nicht an
 * Eingabefelder gebunden: Diese Ansicht ist kein Formular mit diesen Feldern — zwei der vier
 * Maengel liegen ausserhalb des Angebots. Sie stehen darum als Liste in der Rueckmeldung, in der
 * Reihenfolge der Antwort.
 *
 * <b>Der Beleg ist ein Verweis, kein Aufruf</b> (E17): `target="_blank" rel="noopener"` auf den
 * Pfad. Kriterium 14 sagt „oeffnen", und ausgeliefert wird das beim Versenden abgelegte Dokument.
 */

const NICHT_GEFUNDEN = 'Dieses Angebot gibt es nicht.';
const AUSFALL = 'Das Angebot ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const AUSFALL_AKTION = 'Die Aktion wurde nicht ausgeführt. Bitte später erneut versuchen.';
const VERSAND_FEHLT = 'Zum Versenden dieses Angebots fehlen Angaben:';
const LAEDT = 'Das Angebot wird geladen …';
const OHNE_POSITION = 'Noch keine Position.';
const NETTO = 'Alle Beträge netto, zzgl. gesetzlicher Umsatzsteuer';
const VERWERFEN_FRAGE =
  'Der Entwurf verschwindet dann vollständig, samt seinen Positionen. Das lässt sich nicht zurücknehmen.';

/** Die Symbolgroessen: 13 px im Chip, 16 px in den Tasten (wie in {@link VorgangPage}). */
const SYMBOL_CHIP = 13;
const SYMBOL_TASTE = 16;

const SPALTEN = ['Bezeichnung', 'Abrechnung', 'Menge', 'Einheit', 'Einzelpreis', 'Betrag'] as const;

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly angebot: Angebot }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die Ueberschrift: die Nummer, oder das Wort fuer den Entwurf, der noch keine hat. */
function ueberschriftZu(angebot: Angebot): string {
  return angebot.nummer === null ? 'Angebotsentwurf' : `Angebot ${angebot.nummer}`;
}

/**
 * Wahr, solange der Kunde noch reagieren kann (Kriterium 17, F5, F13).
 *
 * „Abgelaufen" und „abgeloest" sind keine Endzustaende: Ein Kunde sagt auch eine Woche nach Ablauf
 * noch zu, und wer nachverhandelt hat, darf sich fuer das erste Angebot entscheiden.
 */
function reaktionOffen(stand: Angebotsstand): boolean {
  return stand === 'VERSENDET' || stand === 'ABGELAUFEN' || stand === 'ABGELOEST';
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
function Angabenkarte({ angebot }: { readonly angebot: Angebot }) {
  const zeilen: readonly { name: string; wert: ReactNode }[] = [
    { name: 'Nummer', wert: angebot.nummer ?? 'Entwurf' },
    { name: 'Angebotsdatum', wert: tagWort(angebot.angebotDatum) },
    { name: 'Gültig bis', wert: tagWort(angebot.gueltigBis) },
    {
      name: 'Dokument',
      wert:
        angebot.nummer === null ? (
          'noch keines'
        ) : (
          <Link
            href={angebotPdfPfad(angebot.id)}
            target="_blank"
            rel="noopener"
            underline="hover"
            sx={{ fontSize: 13.5, fontWeight: 500 }}
          >
            PDF öffnen
          </Link>
        ),
    },
  ];
  return (
    <Karte titel="Angaben">
      <Box
        component="dl"
        data-testid="angebot-angaben"
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

/** Ueber jeder Angebotsansicht stehen die Uebersicht und der Vorgang (E6, E16). */
const ZU_VORGAENGEN: PfadVerweis = { titel: 'Vorgänge', ziel: '/vorgaenge' };

export default function AngebotPage() {
  const { id, angebotId } = useParams();
  const vorgangKennung = kennungAus(id);
  const kennung = kennungAus(angebotId);
  const navigate = useNavigate();
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [meldung, setzeMeldung] = useState<string | null>(null);
  const [felder, setzeFelder] = useState<FieldErrors>({});
  const [laeuft, setzeLaeuft] = useState(false);
  // Solange das Angebot nicht gelesen ist, traegt die Endstufe das Wort „Angebot": Ein Pfad, der
  // erst spaeter erscheint, liesse den Kopf bei jedem Aufruf einmal springen.
  useKopfPfad(
    [ZU_VORGAENGEN, { titel: 'Vorgang', ziel: `/vorgaenge/${String(vorgangKennung)}` }],
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

  /** Ein Zustandswechsel: Der neue Stand kommt aus der Antwort, nicht aus der Oberflaeche. */
  const schalten = async (weg: (id: number) => Promise<Angebot>, angebot: Angebot) => {
    setzeMeldung(null);
    setzeFelder({});
    setzeLaeuft(true);
    try {
      setzeStand({ art: 'daten', angebot: await weg(angebot.id) });
    } catch (ursache) {
      const gemeldet = feldMeldungen(ursache);
      if (Object.keys(gemeldet).length > 0) {
        // Der abgewiesene Versand traegt alle fehlenden Angaben auf einmal (Kriterium 12).
        setzeFelder(gemeldet);
      } else {
        setzeMeldung(AUSFALL_AKTION);
      }
    } finally {
      setzeLaeuft(false);
    }
  };

  const verwerfen = async (angebot: Angebot) => {
    setzeMeldung(null);
    setzeFelder({});
    try {
      await angebotVerwerfen(angebot.id);
      // Der Entwurf ist weg — auf seiner Adresse gibt es nichts mehr zu sehen, also `replace`.
      navigate(`/vorgaenge/${String(angebot.vorgangId)}`, { replace: true });
    } catch {
      setzeMeldung(AUSFALL_AKTION);
    }
  };

  /** Die Aktionen neben dem Inhalt — `undefined`, wo der Stand endgueltig ist (Kriterium 17). */
  function aktionenZu(angebot: Angebot): ReactNode {
    if (angebot.stand === 'ENTWURF') {
      return (
        <Box
          data-testid="angebot-aktionen"
          sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
        >
          <WeicheTaste
            to={`/vorgaenge/${String(angebot.vorgangId)}/angebote/${String(angebot.id)}/bearbeiten`}
            symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Bearbeiten
          </WeicheTaste>
          <KupferTaste
            onClick={() => {
              void schalten(angebotVersenden, angebot);
            }}
            disabled={laeuft}
            symbol={<IconSend size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Versenden
          </KupferTaste>
          <AktionsMenue
            name="Weitere Aktionen"
            objekt={ueberschriftZu(angebot)}
            eintraege={[
              {
                titel: 'Verwerfen',
                rueckfrage: VERWERFEN_FRAGE,
                symbol: <IconTrash size={SYMBOL_TASTE} stroke={1.8} />,
                onAuswahl: () => {
                  void verwerfen(angebot);
                },
              },
            ]}
          />
        </Box>
      );
    }
    if (reaktionOffen(angebot.stand)) {
      return (
        <Box
          data-testid="angebot-aktionen"
          sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}
        >
          <WeicheTaste
            onClick={() => {
              void schalten(angebotAblehnen, angebot);
            }}
            disabled={laeuft}
            symbol={<IconThumbDown size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Ablehnen
          </WeicheTaste>
          <KupferTaste
            onClick={() => {
              void schalten(angebotAnnehmen, angebot);
            }}
            disabled={laeuft}
            symbol={<IconThumbUp size={SYMBOL_TASTE} stroke={1.8} />}
          >
            Annehmen
          </KupferTaste>
        </Box>
      );
    }
    return undefined;
  }

  /** Die Karte mit Ueberschrift, Chip und Aktionen samt der Tafel und den Texten darunter. */
  function inhaltZu(angebot: Angebot): ReactNode {
    const bild = angebotsstandBild(angebot.stand);
    const Standsymbol = bild.symbol;
    const texte: readonly { name: string; wert: string }[] = [
      ...(angebot.leistungsbeschreibung === null
        ? []
        : [{ name: 'Leistungsbeschreibung', wert: angebot.leistungsbeschreibung }]),
      ...(angebot.zahlungsbedingungen === null
        ? []
        : [{ name: 'Zahlungsbedingungen', wert: angebot.zahlungsbedingungen }]),
    ];
    return (
      <>
        <Karte titel={ueberschriftZu(angebot)} titelEbene={1} werkzeug={aktionenZu(angebot)}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: '10px', flexWrap: 'wrap' }}>
            <ZustandsChip
              wort={bild.wort}
              toenung={bild.toenung}
              symbol={<Standsymbol size={SYMBOL_CHIP} stroke={1.8} />}
            />
            <Typography
              sx={(theme) => ({ fontSize: 13, color: theme.vars.palette.kupferwolke.textMatt })}
            >
              {`Angebot vom ${tagWort(angebot.angebotDatum)}, gültig bis ${tagWort(angebot.gueltigBis)}`}
            </Typography>
          </Box>
        </Karte>
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
                {angebot.positionen.map((position, stelle) => (
                  // Die Reihenfolge ist der Schluessel: Eine Position traegt keine eigene
                  // Kennung, ihre Stelle in der Liste ist ihre Identitaet (E24).
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
        <Box
          sx={{
            display: 'grid',
            // Die Texte sind die breitere Seite; unter 1200 px liegen beide uebereinander.
            gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1.6fr) minmax(0, 1fr)' },
            gap: '22px',
            alignItems: 'start',
          }}
        >
          {texte.length === 0 ? null : (
            <Karte titel="Texte">
              <Box
                component="dl"
                sx={{ display: 'flex', flexDirection: 'column', gap: '14px', margin: 0 }}
              >
                {texte.map((text) => (
                  <Fragment key={text.name}>
                    <Box
                      component="dt"
                      sx={(theme) => ({
                        fontSize: 12.5,
                        fontWeight: 600,
                        color: theme.vars.palette.kupferwolke.textSchwach,
                      })}
                    >
                      {text.name}
                    </Box>
                    <Box
                      component="dd"
                      sx={{ margin: 0, fontSize: 13.5, whiteSpace: 'pre-wrap' }}
                    >
                      {text.wert}
                    </Box>
                  </Fragment>
                ))}
              </Box>
            </Karte>
          )}
          <Angabenkarte angebot={angebot} />
        </Box>
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
      {Object.keys(felder).length === 0 ? null : (
        <Alert severity="error">
          <Box component="p" sx={{ margin: '0 0 6px' }}>
            {VERSAND_FEHLT}
          </Box>
          <Box component="ul" sx={{ margin: 0, paddingLeft: '20px' }}>
            {Object.values(felder)
              .flat()
              .map((satz) => (
                <Box component="li" key={satz}>
                  {satz}
                </Box>
              ))}
          </Box>
        </Alert>
      )}
      {inhalt}
    </Box>
  );
}
