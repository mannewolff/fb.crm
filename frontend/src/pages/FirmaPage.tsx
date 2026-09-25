import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import type { Theme } from '@mui/material/styles';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';

import { firmaAktivieren, firmaLesen, firmaStilllegen } from '../api/firmen';
import type { Ansprechpartner, Firma } from '../api/firmen';
import KopfAktion from '../components/KopfAktion';
import KupferTaste from '../components/KupferTaste';
import Platte from '../components/Platte';
import { nichtGefunden } from '../lib/apifehler';
import { kennungAus } from '../lib/kennung';
import { emailZiel, telefonZiel } from '../lib/telefonlink';
import { CARD_RADIUS } from '../theme';

/**
 * Die Detailansicht einer Firma (Kriterien 5, 10, 13, 14).
 *
 * Oben die Angaben als Feldpaare der Vorlage (`.feld`/`.feld-name`, HTML Z. 2171–2183, CSS
 * Z. 1062–1065), darunter die Ansprechpartner. Drei Zusagen tragen die Ansicht:
 *
 * <ul>
 *   <li><b>Kein Platzhalter fuer eine fehlende Angabe</b> (Kriterium 5) — ein Feldpaar entsteht
 *       nur, wo ein Wert steht. Ein „—" waere eine Zeile, die der Screenreader vorliest, ohne
 *       dass sie etwas sagt.</li>
 *   <li><b>Aktive vor stillgelegten</b> (Kriterium 10, E15), und die stillgelegten unter einer
 *       eigenen Ueberschrift statt nur in einer anderen Farbe.</li>
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
const OHNE_ANSPRECHPARTNER = 'Noch kein Ansprechpartner angelegt.';

/** Was die Ansicht gerade weiss. */
type Stand =
  | { readonly art: 'laedt' }
  | { readonly art: 'daten'; readonly firma: Firma }
  | { readonly art: 'unbekannt' }
  | { readonly art: 'ausfall' };

/** Die flache Taste der Vorlage (`.taste` CSS Z. 322–334) — fuer die Nebenwege im Plattenkopf. */
function flacheTasteSx(theme: Theme) {
  return {
    fontSize: 12.5,
    fontWeight: 600,
    textTransform: 'none',
    padding: '5px 13px',
    borderRadius: `${CARD_RADIUS}px`,
    color: theme.vars.palette.kupferwarte.text,
    background: `linear-gradient(180deg, ${theme.vars.palette.kupferwarte.platteHoch}, ${theme.vars.palette.kupferwarte.platteFuss})`,
    border: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
    boxShadow: theme.vars.palette.kupferwarte.schatten.taste,
    '&:hover': { boxShadow: theme.vars.palette.kupferwarte.schatten.platte },
    '&:active': { transform: 'translateY(1px)' },
  } as const;
}

/**
 * Das Schild der Vorlage (`.schild` CSS Z. 780–790): der Stand steht als Wort da, nicht nur als
 * Farbe — Farbe allein traegt keine Information (CLAUDE-react.md, Accessibility).
 */
function Schild() {
  return (
    <Box
      component="span"
      sx={(theme) => ({
        fontSize: 10,
        fontWeight: 500,
        padding: '1px 6px',
        borderRadius: '5px',
        color: theme.vars.palette.kupferwarte.grau,
        border: '1px solid currentColor',
        background: 'color-mix(in srgb, currentColor 13%, transparent)',
      })}
    >
      stillgelegt
    </Box>
  );
}

/** Ein Feldpaar — oder nichts, wo keine Angabe hinterlegt ist. */
function Feld({ name, wert }: { readonly name: string; readonly wert: string | null }) {
  if (wert === null) {
    return null;
  }
  return (
    <Box
      sx={(theme) => ({
        display: 'grid',
        gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: '200px minmax(0, 1fr)' },
        gap: '10px',
        alignItems: 'center',
        padding: '9px 16px',
        fontSize: 12.5,
        borderBottom: `1px solid color-mix(in srgb, ${theme.vars.palette.kupferwarte.rand} 50%, transparent)`,
        '&:last-of-type': { borderBottom: 0 },
      })}
    >
      <Box
        component="span"
        sx={(theme) => ({ fontSize: 11, color: theme.vars.palette.kupferwarte.textSchwach })}
      >
        {name}
      </Box>
      <Box component="span">{wert}</Box>
    </Box>
  );
}

/**
 * Eine Kontaktangabe des Ansprechpartners (E16).
 *
 * Wo `telefonlink.ts` kein Ziel bildet — ein Wert, der keine waehlbare Nummer und keine Adresse
 * ist —, bleibt die Angabe als Text stehen. Ein Link, der nirgendwohin fuehrt, waere nur ein
 * weiteres Ziel fuer den Tabulator.
 */
function Kontakt({
  wert,
  ziel,
}: {
  readonly wert: string | null;
  readonly ziel: (wert: string) => string | null;
}) {
  if (wert === null) {
    return null;
  }
  const adresse = ziel(wert);
  if (adresse === null) {
    return (
      <Typography
        component="span"
        sx={(theme) => ({ fontSize: 11.5, color: theme.vars.palette.kupferwarte.textMatt })}
      >
        {wert}
      </Typography>
    );
  }
  return (
    <Link href={adresse} underline="hover" sx={{ fontSize: 11.5 }}>
      {wert}
    </Link>
  );
}

/** „Anna Berg" oder, ohne Vornamen, „Clausen" — nie ein fuehrendes Leerzeichen. */
function namensZug(partner: Ansprechpartner): string {
  return partner.vorname === null ? partner.nachname : `${partner.vorname} ${partner.nachname}`;
}

/** Eine Zeile der Liste (Vorlage `.vorgang` CSS Z. 580–605). */
function Zeile({ partner }: { readonly partner: Ansprechpartner }) {
  return (
    <Box
      component="li"
      sx={(theme) => ({
        padding: '11px 16px',
        borderBottom: `1px solid color-mix(in srgb, ${theme.vars.palette.kupferwarte.rand} 55%, transparent)`,
        'li:last-of-type&': { borderBottom: 0 },
      })}
    >
      <Typography sx={{ fontSize: 13.5, fontWeight: 500 }}>{namensZug(partner)}</Typography>
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          marginTop: '3px',
          flexWrap: 'wrap',
        }}
      >
        {partner.rolle === null ? null : (
          <Typography
            component="span"
            sx={(theme) => ({ fontSize: 11.5, color: theme.vars.palette.kupferwarte.textMatt })}
          >
            {partner.rolle}
          </Typography>
        )}
        <Kontakt wert={partner.email} ziel={emailZiel} />
        <Kontakt wert={partner.telefonFestnetz} ziel={telefonZiel} />
        <Kontakt wert={partner.telefonMobil} ziel={telefonZiel} />
      </Box>
    </Box>
  );
}

/** Eine der beiden Listen; ohne Eintraege entsteht sie gar nicht. */
function Liste({
  partner,
  bezeichnung,
}: {
  readonly partner: readonly Ansprechpartner[];
  readonly bezeichnung: string;
}) {
  if (partner.length === 0) {
    return null;
  }
  return (
    <Box
      component="ul"
      aria-label={bezeichnung}
      sx={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column' }}
    >
      {partner.map((einer) => (
        <Zeile key={einer.id} partner={einer} />
      ))}
    </Box>
  );
}

/** Die Ansprechpartner der Firma: aktive zuerst, die stillgelegten darunter (Kriterium 10). */
function Ansprechpartnerliste({ firma }: { readonly firma: Firma }) {
  const aktive = firma.ansprechpartner.filter((einer) => einer.aktiv);
  const ruhende = firma.ansprechpartner.filter((einer) => !einer.aktiv);
  if (firma.ansprechpartner.length === 0) {
    return (
      <Typography
        role="status"
        sx={(theme) => ({
          padding: '18px 16px',
          fontSize: 12.5,
          color: theme.vars.palette.kupferwarte.textMatt,
        })}
      >
        {OHNE_ANSPRECHPARTNER}
      </Typography>
    );
  }
  return (
    <>
      <Liste partner={aktive} bezeichnung="Aktive Ansprechpartner" />
      {ruhende.length === 0 ? null : (
        <Typography
          variant="h3"
          sx={(theme) => ({
            padding: '12px 16px 4px',
            fontSize: 11.5,
            fontWeight: 600,
            color: theme.vars.palette.kupferwarte.textSchwach,
            borderTop: `1px solid ${theme.vars.palette.kupferwarte.rand}`,
          })}
        >
          Stillgelegt
        </Typography>
      )}
      <Liste partner={ruhende} bezeichnung="Stillgelegte Ansprechpartner" />
    </>
  );
}

export default function FirmaPage() {
  const { id } = useParams();
  const kennung = kennungAus(id);
  const [stand, setzeStand] = useState<Stand>({ art: 'laedt' });
  const [schaltFehler, setzeSchaltFehler] = useState<string | null>(null);

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

  let inhalt: ReactNode;
  if (stand.art === 'daten') {
    const firma = stand.firma;
    inhalt = (
      <>
        <Platte
          titel={firma.name}
          werkzeug={
            <>
              {firma.aktiv ? null : <Schild />}
              <Button
                component={RouterLink}
                to={`/firmen/${String(firma.id)}/bearbeiten`}
                sx={flacheTasteSx}
              >
                Bearbeiten
              </Button>
              <Button
                onClick={() => {
                  void schalten(firma);
                }}
                sx={flacheTasteSx}
              >
                {firma.aktiv ? 'Stilllegen' : 'Wieder aktivieren'}
              </Button>
            </>
          }
        >
          <Box sx={{ display: 'flex', flexDirection: 'column' }}>
            <Feld name="Straße und Hausnummer" wert={firma.strasse} />
            <Feld name="Postleitzahl" wert={firma.plz} />
            <Feld name="Ort" wert={firma.ort} />
            <Feld name="Land" wert={firma.land} />
            <Feld name="Steuernummer" wert={firma.steuernummer} />
            <Feld name="Umsatzsteuer-Identifikationsnummer" wert={firma.umsatzsteuerId} />
          </Box>
        </Platte>
        <Platte titel="Ansprechpartner">
          <Ansprechpartnerliste firma={firma} />
        </Platte>
      </>
    );
  } else if (stand.art === 'laedt') {
    inhalt = (
      <Platte>
        <Typography
          sx={(theme) => ({
            padding: '18px 16px',
            fontSize: 12.5,
            color: theme.vars.palette.kupferwarte.textSchwach,
          })}
        >
          Die Firma wird geladen …
        </Typography>
      </Platte>
    );
  } else {
    inhalt = (
      <Platte>
        <Alert severity="error" sx={{ borderRadius: 0 }}>
          {stand.art === 'unbekannt' ? NICHT_GEFUNDEN : AUSFALL}
        </Alert>
      </Platte>
    );
  }

  return (
    <Box
      sx={{
        padding: { xs: 2, sm: '22px 26px 44px' },
        display: 'flex',
        flexDirection: 'column',
        gap: '20px',
      }}
    >
      {kennung === null ? null : (
        <KopfAktion>
          <KupferTaste to={`/firmen/${String(kennung)}/ansprechpartner/neu`}>
            Neuer Ansprechpartner
          </KupferTaste>
        </KopfAktion>
      )}
      {schaltFehler === null ? null : <Alert severity="error">{schaltFehler}</Alert>}
      {inhalt}
    </Box>
  );
}
