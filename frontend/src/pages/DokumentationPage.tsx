import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import { Navigate, NavLink, useParams } from 'react-router-dom';

import Dokumenttext from '../components/Dokumenttext';
import Karte from '../components/Karte';
import Kopfkarte from '../components/Kopfkarte';
import { useKopfPfad } from '../components/KopfPfad';
import type { PfadVerweis } from '../components/KopfPfad';
import { weichSx } from '../components/WeicheTaste';
import { TEILE, teilNach, type Kennung, type Teil } from '../lib/dokumentation';
import type { Block } from '../lib/markdown';
import { ABSTAND_BUEHNE } from '../theme';

/**
 * Die Dokumentation unter `/dokumentation` (Issue #387; Plan #378, E6, E8, E11, E15, E17).
 *
 * **Eine Seite fuer beide Teile** (E15): Bedienung und Betrieb unterscheiden sich nur im Text, den
 * `lib/dokumentation.ts` liefert. Oben die {@link Kopfkarte} mit dem Titel aus der Datei und dem
 * Umschalter der Teile, darunter eine Karte „Inhalt" und eine mit dem {@link Dokumenttext}.
 *
 * **Jeder Teil hat genau eine Adresse** (E6): „Bedienung" steht unter `/dokumentation`, „Betrieb"
 * unter `/dokumentation/betrieb`. Jede andere Adresse darunter — auch `/dokumentation/bedienung` —
 * fuehrt mit `replace` auf `/dokumentation`. So passt der Umschalter immer zur Adresse: Er sind zwei
 * `NavLink` mit `end`, und `aria-current="page"` setzt `NavLink` selbst, wie in der Schiene (E17).
 * Seine Gestalt ist die der weichen Taste ({@link weichSx}); der aktive Teil liegt auf Pfirsich wie
 * der aktive Eintrag der Schiene.
 *
 * **Das Inhaltsverzeichnis** fuehrt nur die `##`-Ueberschriften als gewoehnliche Ankerverweise
 * (Issue #474, F2; hebt die eingerueckten `###`-Eintraege aus E8 auf — mit den Zwischentiteln
 * waeren es sonst rund 60 Eintraege). Den Sprung macht der Browser; der Titel der Abschnittskarte
 * traegt dafuer `id` und `tabIndex={-1}` aus dem {@link Dokumenttext}. Die Gestalt folgt der
 * Angabenliste: 13,5 px, Zeilenabstand 10 px.
 *
 * **Die mitlaufende Spalte** (F3): Ab dem Umbruchpunkt `lg` (1200 px) steht die Karte „Inhalt"
 * links neben den Abschnittskarten und klebt beim Rollen oben (`position: sticky`), um den
 * Aussenabstand des Rahmens versetzt wie die Schiene; was nicht in die Hoehe passt, rollt in ihr.
 * Darunter steht sie wie zuvor als Karte ueber dem Text.
 */

const KEIN_WEG: readonly PfadVerweis[] = [];

const TITEL = 'Dokumentation';

/** Der Teil, den der Aufruf zeigt (Kriterium 1) — der erste in {@link TEILE}. */
const VORGABE: Kennung = 'bedienung';

const WURZEL = '/dokumentation';

/** Die eine Adresse eines Teils. */
function adresse(teil: Teil): string {
  return teil.kennung === VORGABE ? WURZEL : `${WURZEL}/${teil.kennung}`;
}

/** Der Teil zur Adresse; `null`, wo die Adresse keinen Teil benennt. */
function kennungZu(teil: string | undefined): Kennung | null {
  if (teil === undefined) {
    return VORGABE;
  }
  return TEILE.find((t) => t.kennung !== VORGABE && t.kennung === teil)?.kennung ?? null;
}

/** Ein Eintrag im Inhalt: eine `##`-Ueberschrift (F2). */
interface Eintrag {
  readonly text: string;
  readonly anker: string;
}

/** Die Gliederung aus den `##`-Ueberschriften; die `###` stehen nur im Text (F2). */
function gliederung(bloecke: readonly Block[]): Eintrag[] {
  return bloecke.flatMap((block) =>
    block.art === 'ueberschrift' && block.ebene === 2 ? [{ text: block.text, anker: block.anker }] : [],
  );
}

const LISTE = { listStyle: 'none', margin: 0, padding: 0, display: 'grid', gap: '10px' } as const;

/** Der Abstand der mitlaufenden Spalte zum oberen Rand, wie bei der Schiene. */
const SPALTE_OBEN = 20;

function Inhalt({ bloecke }: { readonly bloecke: readonly Block[] }) {
  return (
    <Box component="nav" aria-label="Inhalt" sx={{ fontSize: 13.5 }}>
      <Box component="ul" sx={LISTE}>
        {gliederung(bloecke).map((eintrag) => (
          <li key={eintrag.anker}>
            <Link href={`#${eintrag.anker}`} underline="hover" sx={{ fontWeight: 500 }}>
              {eintrag.text}
            </Link>
          </li>
        ))}
      </Box>
    </Box>
  );
}

function Umschalter() {
  return (
    <Box
      component="nav"
      aria-label="Teile der Dokumentation"
      sx={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}
    >
      {TEILE.map((teil) => (
        <Box
          key={teil.kennung}
          component={NavLink}
          to={adresse(teil)}
          end
          sx={(theme) => ({
            ...weichSx(theme),
            ...theme.typography.button,
            display: 'inline-flex',
            alignItems: 'center',
            textDecoration: 'none',
            transition: 'transform .15s ease',
            '&[aria-current="page"]': {
              background: theme.vars.palette.kupferwolke.toenung.pfirsich.flaeche,
              color: theme.vars.palette.kupferwolke.toenung.pfirsich.schrift,
              fontWeight: 700,
              boxShadow: 'none',
            },
          })}
        >
          {teil.name}
        </Box>
      ))}
    </Box>
  );
}

export default function DokumentationPage() {
  const { teil } = useParams();
  useKopfPfad(KEIN_WEG, TITEL);

  const kennung = kennungZu(teil);
  if (kennung === null) {
    return <Navigate to={WURZEL} replace />;
  }
  const { dokument } = teilNach(kennung);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE }}>
      <Kopfkarte malName={dokument.titel} titel={dokument.titel} aktionen={<Umschalter />} />
      <Box
        sx={(theme) => ({
          display: 'flex',
          flexDirection: 'column',
          gap: ABSTAND_BUEHNE,
          [theme.breakpoints.up('lg')]: {
            display: 'grid',
            gridTemplateColumns: '240px minmax(0, 1fr)',
            alignItems: 'start',
          },
        })}
      >
        <Box
          sx={(theme) => ({
            [theme.breakpoints.up('lg')]: {
              position: 'sticky',
              top: `${String(SPALTE_OBEN)}px`,
              maxHeight: `calc(100vh - ${String(2 * SPALTE_OBEN)}px)`,
              overflowY: 'auto',
            },
          })}
        >
          <Karte titel="Inhalt">
            <Inhalt bloecke={dokument.bloecke} />
          </Karte>
        </Box>
        <Dokumenttext bloecke={dokument.bloecke} />
      </Box>
    </Box>
  );
}
