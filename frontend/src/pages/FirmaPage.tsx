import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import {
  IconArchive,
  IconArchiveOff,
  IconCircleCheck,
  IconDeviceMobile,
  IconMail,
  IconMapPin,
  IconPencil,
  IconPhone,
  IconUserPlus,
} from '@tabler/icons-react';
import { Fragment, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';

import {
  ansprechpartnerAktivieren,
  ansprechpartnerStilllegen,
  firmaAktivieren,
  firmaLesen,
  firmaStilllegen,
} from '../api/firmen';
import type { Ansprechpartner, Firma } from '../api/firmen';
import { vorgaengeDerFirma } from '../api/vorgaenge';
import type { Phase, VorgaengeDerFirma, VorgangZeile } from '../api/vorgaenge';
import AktionsMenue from '../components/AktionsMenue';
import type { AktionsEintrag } from '../components/AktionsMenue';
import Innenkarte, { HinzufuegenKachel, InnenkartenRaster } from '../components/Innenkarte';
import Karte from '../components/Karte';
import Kopfkarte from '../components/Kopfkarte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import KupferTaste from '../components/KupferTaste';
import WeicheTaste from '../components/WeicheTaste';
import ZustandsChip from '../components/ZustandsChip';
import { nichtGefunden } from '../lib/apifehler';
import { kennungAus } from '../lib/kennung';
import { namensZug } from '../lib/namenszug';
import { emailZiel, telefonZiel } from '../lib/telefonlink';
import { ZAHLEN_KLASSE } from '../theme';

/**
 * Die Detailansicht einer Firma (Kriterien 5, 10, 13, 14) in der Kupferwolke.
 *
 * Oben die {@link Kopfkarte} mit Mal, Namen, Adresszeile und genau einem Zustands-Chip, rechts die
 * Aktionen; darunter die Ansprechpartner als {@link Innenkarte}n im Raster, danach die Stammdaten
 * und die Vorgaenge der Firma. Vier Zusagen tragen die Ansicht:
 *
 * <ul>
 *   <li><b>Kein Platzhalter fuer eine fehlende Angabe</b> (Kriterium 5) — ein Teil der Adresszeile
 *       und eine Stammdaten-Zeile entstehen nur, wo ein Wert steht. Ein „—" waere eine Zeile, die
 *       der Screenreader vorliest, ohne dass sie etwas sagt.</li>
 *   <li><b>Aktive vor stillgelegten</b> (Kriterium 10, E15). Die stillgelegte Kachel bleibt im
 *       Raster und traegt ihren Zustand als Chip mit Wort — die matte Schrift stuetzt ihn nur.</li>
 *   <li><b>Folgenreiches steht im ⋯-Menue</b> (CLAUDE-design.md, „Tasten") — Stilllegen fragt vor
 *       der Ausfuehrung nach, Wiederaktivieren laeuft ohne Zwischenschritt.</li>
 *   <li><b>Alle Wege bleiben offen, auch bei stillgelegter Firma</b> (Kriterium 14) — stillgelegt
 *       heisst „nicht mehr im Angebot", nicht „gesperrt".</li>
 * </ul>
 *
 * Nach dem Stilllegen oder Wiederaktivieren liest die Ansicht die Firma neu, statt den Stand
 * selbst umzuschalten: Der Server ist die Quelle der Wahrheit, und nur so sieht der Benutzer, was
 * dort tatsaechlich steht.
 */

const NICHT_GEFUNDEN = 'Diese Firma gibt es nicht.';
const AUSFALL = 'Die Firma ist gerade nicht zu erreichen. Bitte später erneut versuchen.';
const SCHALTEN_FEHLT = 'Der Stand der Firma wurde nicht geändert. Bitte später erneut versuchen.';
const PARTNER_SCHALTEN_FEHLT =
  'Der Stand des Ansprechpartners wurde nicht geändert. Bitte später erneut versuchen.';
const OHNE_ANSPRECHPARTNER = 'Noch kein Ansprechpartner angelegt.';
const OHNE_VORGANG = 'Noch kein Vorgang angelegt.';
const VORGAENGE_AUSFALL = 'Die Vorgänge sind gerade nicht zu erreichen. Bitte später erneut versuchen.';

/** Die Saetze der beiden Rueckfragen — sie erklaeren die Folge, nicht die Aktion. */
const FIRMA_FRAGE =
  'Eine stillgelegte Firma steht nicht mehr zur Auswahl. Ihre Vorgänge bleiben erhalten.';
const PARTNER_FRAGE =
  'Ein stillgelegter Ansprechpartner steht nicht mehr zur Auswahl. Seine Angaben bleiben erhalten.';

/** Die Symbolgroessen: 14 px in Chip und Kontaktzeile, 16 px in Tasten und Menueeintraegen. */
const SYMBOL_KLEIN = 14;
const SYMBOL_TASTE = 16;

/**
 * Die Phase als Wort; heute kennt das Backend genau eine (`Phase` in `api/vorgaenge.ts`).
 *
 * Dieselbe Zuordnung steht in `VorgaengePage`. Sie hier zu wiederholen, statt sie aus der anderen
 * Ansicht zu holen, haelt die beiden Seiten voneinander unabhaengig — ein gemeinsamer Ort entsteht,
 * wenn es mehr als eine Phase und mehr als zwei Leser gibt.
 */
const PHASE_TEXT: Readonly<Record<Phase, string>> = { ANBAHNUNG: 'Anbahnung' };

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly firma: Firma }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/**
 * Was die Ansicht ueber die Vorgaenge der Firma weiss (Kriterium 12).
 *
 * Ein eigener Stand neben dem der Firma, weil es ein eigener Leseweg ist (E2): Faellt er aus,
 * bleiben die Angaben der Firma und ihre Ansprechpartner sichtbar — nur die eine Karte meldet.
 */
type VorgangStand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly vorgaenge: VorgaengeDerFirma }
  | { readonly art: 'ausfall' };

/**
 * Das Schild eines abgeschlossenen Vorgangs in der Vorgangsliste (Paket #71).
 *
 * Kein {@link ZustandsChip}: Das Wort steht **innerhalb** des Weges zum Vorgang, damit sein
 * zugaenglicher Name den Abschlussstand mittraegt — ein Chip daneben waere eine zweite,
 * stille Angabe.
 */
function Schild({ wort }: { readonly wort: string }) {
  return (
    <Box
      component="span"
      sx={(theme) => ({
        flex: 'none',
        fontSize: 10,
        fontWeight: 500,
        padding: '1px 6px',
        borderRadius: '5px',
        color: theme.vars.palette.kupferwolke.melder.grau,
        border: '1px solid currentColor',
        background: 'color-mix(in srgb, currentColor 13%, transparent)',
      })}
    >
      {wort}
    </Box>
  );
}

/** Ein dekoratives Symbol in einer Zeile — es stuetzt das Wort daneben und wird nicht vorgelesen. */
function StuetzSymbol({ children }: { readonly children: ReactNode }) {
  return (
    <Box component="span" aria-hidden sx={{ display: 'flex', flex: 'none' }}>
      {children}
    </Box>
  );
}

/**
 * Die Adresszeile der Kopfkarte: „Straße · PLZ Ort · Land", leere Teile entfallen.
 *
 * `undefined`, wo keine einzige Angabe steht — die Kopfkarte laesst ihre Zeile dann ganz weg,
 * statt eine leere Zeile mit Symbol zu zeigen (Kriterium 5).
 */
function adressZeile(firma: Firma): ReactNode {
  const ortsteil = [firma.plz, firma.ort].filter((teil): teil is string => teil !== null).join(' ');
  const teile = [firma.strasse, ortsteil === '' ? null : ortsteil, firma.land].filter(
    (teil): teil is string => teil !== null,
  );
  if (teile.length === 0) {
    return undefined;
  }
  return (
    <>
      <StuetzSymbol>
        <IconMapPin size={SYMBOL_KLEIN} stroke={1.8} />
      </StuetzSymbol>
      {teile.join(' · ')}
    </>
  );
}

/** Der Zustands-Chip eines Objekts: „Aktiv" auf Salbei, „Stillgelegt" auf Rose. */
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

/**
 * Der Eintrag, der ein Objekt stilllegt beziehungsweise wieder aktiviert.
 *
 * Nur das Stilllegen traegt eine `rueckfrage` — Wiederaktivieren nimmt nichts weg und laeuft ohne
 * Zwischenschritt (CLAUDE-design.md, „Tasten").
 */
function schaltEintrag(aktiv: boolean, frage: string, onAuswahl: () => void): AktionsEintrag {
  return aktiv
    ? {
        titel: 'Stilllegen',
        symbol: <IconArchive size={SYMBOL_TASTE} stroke={1.8} />,
        rueckfrage: frage,
        onAuswahl,
      }
    : {
        titel: 'Wieder aktivieren',
        symbol: <IconArchiveOff size={SYMBOL_TASTE} stroke={1.8} />,
        onAuswahl,
      };
}

/**
 * Eine Kontaktangabe des Ansprechpartners (E16).
 *
 * Wo `telefonlink.ts` kein Ziel bildet — ein Wert, der keine waehlbare Nummer und keine Adresse
 * ist —, bleibt die Angabe als Text stehen. Ein Link, der nirgendwohin fuehrt, waere nur ein
 * weiteres Ziel fuer den Tabulator. Gestalt und Farbe kommen aus der {@link Innenkarte}; hier
 * steht ein schlichtes `a` beziehungsweise `span`.
 */
interface KontaktAngabe {
  readonly art: string;
  readonly wert: string;
  readonly ziel: (wert: string) => string | null;
  readonly symbol: ReactNode;
}

/** Derselbe Weg, solange noch offen ist, ob eine Angabe hinterlegt ist. */
type MoeglicheAngabe = Omit<KontaktAngabe, 'wert'> & { readonly wert: string | null };

function Kontakt({ angabe }: { readonly angabe: KontaktAngabe }) {
  const adresse = angabe.ziel(angabe.wert);
  const inhalt = (
    <>
      <StuetzSymbol>{angabe.symbol}</StuetzSymbol>
      {angabe.wert}
    </>
  );
  if (adresse === null) {
    return <span>{inhalt}</span>;
  }
  return <a href={adresse}>{inhalt}</a>;
}

/** Die belegten Kontaktwege eines Ansprechpartners, in fester Reihenfolge. */
function kontakteVon(partner: Ansprechpartner): readonly KontaktAngabe[] {
  const moeglich: readonly MoeglicheAngabe[] = [
    {
      art: 'email',
      wert: partner.email,
      ziel: emailZiel,
      symbol: <IconMail size={SYMBOL_KLEIN} stroke={1.8} />,
    },
    {
      art: 'festnetz',
      wert: partner.telefonFestnetz,
      ziel: telefonZiel,
      symbol: <IconPhone size={SYMBOL_KLEIN} stroke={1.8} />,
    },
    {
      art: 'mobil',
      wert: partner.telefonMobil,
      ziel: telefonZiel,
      symbol: <IconDeviceMobile size={SYMBOL_KLEIN} stroke={1.8} />,
    },
  ];
  return moeglich.filter((angabe): angabe is KontaktAngabe => angabe.wert !== null);
}

/**
 * Die Kachel eines Ansprechpartners (Kriterium 15).
 *
 * Das ⋯-Menue traegt den Namen des Ansprechpartners in seiner Benennung. Eine Reihe von Kacheln
 * mit lauter Tasten „Aktionen" waere mit dem Screenreader nicht zu unterscheiden — und auch nicht
 * von dem Menue der Firma in der Kopfkarte.
 */
function Partnerkachel({
  partner,
  firmaId,
  schalte,
}: {
  readonly partner: Ansprechpartner;
  readonly firmaId: number;
  readonly schalte: (partner: Ansprechpartner) => void;
}) {
  const navigate = useNavigate();
  const name = namensZug(partner);
  const kontakte = kontakteVon(partner);
  return (
    <Innenkarte
      name={name}
      matt={!partner.aktiv}
      zustand={partner.aktiv ? undefined : zustandsChip(false)}
      unterzeile={partner.rolle ?? undefined}
      aktion={
        <AktionsMenue
          name={`Aktionen für ${name}`}
          objekt={name}
          eintraege={[
            {
              titel: 'Bearbeiten',
              symbol: <IconPencil size={SYMBOL_TASTE} stroke={1.8} />,
              onAuswahl: () => {
                navigate(
                  `/firmen/${String(firmaId)}/ansprechpartner/${String(partner.id)}/bearbeiten`,
                );
              },
            },
            schaltEintrag(partner.aktiv, PARTNER_FRAGE, () => {
              schalte(partner);
            }),
          ]}
        />
      }
    >
      {kontakte.length === 0
        ? undefined
        : kontakte.map((angabe) => <Kontakt key={angabe.art} angabe={angabe} />)}
    </Innenkarte>
  );
}

/**
 * Die Ansprechpartner der Firma: aktive zuerst, die stillgelegten danach (Kriterium 10), und als
 * letzte Kachel die Hinzufuegen-Kachel (Vorlage `.neu` Z. 91–92).
 */
function Ansprechpartnerkarte({
  firma,
  schalte,
}: {
  readonly firma: Firma;
  readonly schalte: (partner: Ansprechpartner) => void;
}) {
  const geordnet = [
    ...firma.ansprechpartner.filter((einer) => einer.aktiv),
    ...firma.ansprechpartner.filter((einer) => !einer.aktiv),
  ];
  return (
    <Karte titel="Ansprechpartner" anzahl={firma.ansprechpartner.length}>
      {geordnet.length === 0 ? (
        <Typography
          role="status"
          sx={(theme) => ({
            marginBottom: '14px',
            fontSize: 12.5,
            color: theme.vars.palette.kupferwolke.textMatt,
          })}
        >
          {OHNE_ANSPRECHPARTNER}
        </Typography>
      ) : null}
      <InnenkartenRaster>
        {geordnet.map((partner) => (
          <Partnerkachel
            key={partner.id}
            partner={partner}
            firmaId={firma.id}
            schalte={schalte}
          />
        ))}
        <HinzufuegenKachel
          to={`/firmen/${String(firma.id)}/ansprechpartner/neu`}
          symbol={<IconUserPlus size={18} stroke={1.8} />}
        >
          Ansprechpartner hinzufügen
        </HinzufuegenKachel>
      </InnenkartenRaster>
    </Karte>
  );
}

/**
 * Die Stammdaten, die nicht in die Adresszeile der Kopfkarte gehen (CLAUDE-design.md,
 * „Bausteine": Beschriftung–Wert-Liste, Beschriftung in Text schwach).
 *
 * Ohne eine einzige Angabe entsteht die Karte nicht — eine leere Karte mit Kopf waere eine
 * Ueberschrift ohne Inhalt.
 */
function Stammdatenkarte({ firma }: { readonly firma: Firma }) {
  const zeilen = [
    { name: 'Steuernummer', wert: firma.steuernummer },
    { name: 'Umsatzsteuer-Identifikationsnummer', wert: firma.umsatzsteuerId },
  ].filter((zeile): zeile is { name: string; wert: string } => zeile.wert !== null);
  if (zeilen.length === 0) {
    return null;
  }
  return (
    <Karte titel="Stammdaten">
      <Box
        component="dl"
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

/**
 * Eine Zeile der Vorgangsliste (Paket #71).
 *
 * Nummer und Titel bilden zusammen den Weg zum Vorgang, das Schild eines abgeschlossenen steht
 * darin: So traegt der Name des Weges den Abschlussstand, statt ihn nur nebenher zu zeigen — wer
 * die Liste mit dem Screenreader Weg fuer Weg durchgeht, hoert ihn mit. Die Phase steht daneben,
 * ausserhalb des Weges, weil sie kein Teil seines Ziels ist.
 */
function VorgangZeileAnsicht({ vorgang }: { readonly vorgang: VorgangZeile }) {
  return (
    <Box
      component="li"
      sx={(theme) => ({
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        flexWrap: 'wrap',
        padding: '11px 16px',
        borderBottom: `1px solid color-mix(in srgb, ${theme.vars.palette.kupferwolke.linie} 55%, transparent)`,
        'li:last-of-type&': { borderBottom: 0 },
      })}
    >
      <Box
        component={RouterLink}
        to={`/vorgaenge/${String(vorgang.id)}`}
        sx={(theme) => ({
          display: 'inline-flex',
          alignItems: 'center',
          gap: 1.5,
          minWidth: 0,
          color: 'inherit',
          textDecoration: 'none',
          '&:hover': { color: theme.vars.palette.kupferwolke.kupfer },
        })}
      >
        <Box
          component="span"
          className={ZAHLEN_KLASSE}
          sx={(theme) => ({
            flex: 'none',
            fontSize: 12,
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          {`#${String(vorgang.nummer)}`}
        </Box>
        <Box component="span" sx={{ fontSize: 13.5, fontWeight: 500, minWidth: 0 }}>
          {vorgang.titel}
        </Box>
        {vorgang.abgeschlossen ? <Schild wort="abgeschlossen" /> : null}
      </Box>
      <Typography
        component="span"
        sx={(theme) => ({
          marginLeft: 'auto',
          fontSize: 11.5,
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {PHASE_TEXT[vorgang.phase]}
      </Typography>
    </Box>
  );
}

/** Eine der beiden Vorgangslisten; ohne Eintraege entsteht sie gar nicht. */
function Vorgangsliste({
  vorgaenge,
  bezeichnung,
}: {
  readonly vorgaenge: readonly VorgangZeile[];
  readonly bezeichnung: string;
}) {
  if (vorgaenge.length === 0) {
    return null;
  }
  return (
    <Box
      component="ul"
      aria-label={bezeichnung}
      sx={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column' }}
    >
      {vorgaenge.map((vorgang) => (
        <VorgangZeileAnsicht key={vorgang.id} vorgang={vorgang} />
      ))}
    </Box>
  );
}

/**
 * Der Inhalt der Karte „Vorgaenge" (Kriterium 12).
 *
 * Offene zuerst in der Reihenfolge der Antwort, die abgeschlossenen abgesetzt unter eigener
 * Ueberschrift. Ohne Vorgang sagt die Karte das, statt leer zu bleiben.
 */
function Vorgangskarte({ stand }: { readonly stand: VorgangStand }) {
  if (stand.art === 'laedt') {
    return (
      <Typography
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textSchwach,
        })}
      >
        Vorgänge werden geladen …
      </Typography>
    );
  }
  if (stand.art === 'ausfall') {
    return (
      <Alert severity="error" sx={{ borderRadius: 0 }}>
        {VORGAENGE_AUSFALL}
      </Alert>
    );
  }
  const { offene, abgeschlossene } = stand.vorgaenge;
  if (offene.length === 0 && abgeschlossene.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwolke.textMatt,
        })}
      >
        {OHNE_VORGANG}
      </Typography>
    );
  }
  return (
    <>
      <Vorgangsliste vorgaenge={offene} bezeichnung="Offene Vorgänge" />
      {abgeschlossene.length === 0 ? null : (
        <Typography
          variant="h3"
          sx={(theme) => ({
            padding: '12px 16px 4px',
            fontSize: 11.5,
            fontWeight: 600,
            color: theme.vars.palette.kupferwolke.textSchwach,
            borderTop: `1px solid ${theme.vars.palette.kupferwolke.linie}`,
          })}
        >
          Abgeschlossen
        </Typography>
      )}
      <Vorgangsliste vorgaenge={abgeschlossene} bezeichnung="Abgeschlossene Vorgänge" />
    </>
  );
}

/** Ueber jeder Firmen-Ansicht steht die Uebersicht (E6). */
const ZU_FIRMEN: readonly PfadVerweis[] = [{ titel: 'Firmen', ziel: '/firmen' }];

export default function FirmaPage() {
  const { id } = useParams();
  const kennung = kennungAus(id);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [schaltFehler, setzeSchaltFehler] = useState<string | null>(null);
  const [vorgangStand, setzeVorgangStand] = useState<VorgangStand>({ art: 'laedt' });
  // Solange die Firma nicht gelesen ist, traegt die Endstufe das Wort „Firma": Ein Pfad, der
  // erst spaeter erscheint, liesse den Kopf bei jedem Aufruf einmal springen.
  useKopfPfad(ZU_FIRMEN, stand.art === 'daten' ? stand.firma.name : 'Firma');

  // Der zweite Leseweg laeuft neben dem ersten und mit eigenem Stand (E2): Er haengt nicht an der
  // Antwort der Firma, und sein Ausfall nimmt der Ansicht nicht die Angaben.
  useEffect(() => {
    if (kennung === null) {
      return;
    }
    vorgaengeDerFirma(kennung)
      .then((vorgaenge) => {
        setzeVorgangStand({ art: 'daten', vorgaenge });
      })
      .catch(() => {
        setzeVorgangStand({ art: 'ausfall' });
      });
  }, [kennung]);

  useEffect(() => {
    if (kennung === null) {
      // Eine Kennung, die keine ist, geht gar nicht erst ans Netz (kennung.ts).
      setzeStand({ art: 'unbekannt' });
      return;
    }
    firmaLesen(kennung)
      .then((firma) => {
        setzeStand({ art: 'daten', firma });
      })
      .catch((ursache: unknown) => {
        setzeStand(nichtGefunden(ursache) ? { art: 'unbekannt' } : { art: 'ausfall' });
      });
  }, [kennung]);

  const schalten = async (firma: Firma) => {
    setzeSchaltFehler(null);
    try {
      await (firma.aktiv ? firmaStilllegen(firma.id) : firmaAktivieren(firma.id));
      setzeStand({ art: 'daten', firma: await firmaLesen(firma.id) });
    } catch {
      // Welcher Grund es war, hilft dem Benutzer nicht (CLAUDE-react.md).
      setzeSchaltFehler(SCHALTEN_FEHLT);
    }
  };

  /**
   * Stilllegen und Wiederaktivieren einer Kachel (Kriterium 15).
   *
   * Wie bei der Firma liest die Ansicht danach neu: Erst die Antwort des Servers entscheidet, an
   * welcher Stelle des Rasters die Kachel steht.
   */
  const schaltePartner = async (firma: Firma, partner: Ansprechpartner) => {
    setzeSchaltFehler(null);
    try {
      await (partner.aktiv
        ? ansprechpartnerStilllegen(firma.id, partner.id)
        : ansprechpartnerAktivieren(firma.id, partner.id));
      setzeStand({ art: 'daten', firma: await firmaLesen(firma.id) });
    } catch {
      setzeSchaltFehler(PARTNER_SCHALTEN_FEHLT);
    }
  };

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    const firma = stand.firma;
    inhalt = (
      <>
        <Kopfkarte
          malName={firma.name}
          titel={firma.name}
          zeile={adressZeile(firma)}
          chips={zustandsChip(firma.aktiv)}
          aktionen={
            <>
              <WeicheTaste
                to={`/firmen/${String(firma.id)}/bearbeiten`}
                symbol={<IconPencil size={SYMBOL_TASTE} stroke={1.8} />}
              >
                Bearbeiten
              </WeicheTaste>
              <KupferTaste
                to={`/firmen/${String(firma.id)}/ansprechpartner/neu`}
                symbol={<IconUserPlus size={SYMBOL_TASTE} stroke={1.8} />}
              >
                Neuer Ansprechpartner
              </KupferTaste>
              <AktionsMenue
                name={`Aktionen für ${firma.name}`}
                objekt={firma.name}
                eintraege={[
                  schaltEintrag(firma.aktiv, FIRMA_FRAGE, () => {
                    void schalten(firma);
                  }),
                ]}
              />
            </>
          }
        />
        <Ansprechpartnerkarte
          firma={firma}
          schalte={(partner) => {
            void schaltePartner(firma, partner);
          }}
        />
        <Stammdatenkarte firma={firma} />
        <Karte titel="Vorgänge">
          <Vorgangskarte stand={vorgangStand} />
        </Karte>
      </>
    );
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Karte>
        <Typography
          sx={(theme) => ({
            fontSize: 12.5,
            color: theme.vars.palette.kupferwolke.textSchwach,
          })}
        >
          Die Firma wird geladen …
        </Typography>
      </Karte>
    );
  } else {
    inhalt = (
      <Karte>
        <Alert severity="error">
          {stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}
        </Alert>
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
      {schaltFehler === null ? null : <Alert severity="error">{schaltFehler}</Alert>}
      {inhalt}
    </Box>
  );
}
