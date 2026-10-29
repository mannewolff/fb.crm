import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';

import type { Block, Inline } from '../lib/markdown';
import { ABSTAND_BUEHNE, RADIUS_KLEIN } from '../theme';
import Karte from './Karte';

/**
 * Der Dokumenttext: die Bloecke des Markdown-Lesers als Oberflaeche (Plan-Entscheidungen E5, E8,
 * E18).
 *
 * Der Baustein baut React-Elemente aus Daten und setzt nie rohes HTML in die Seite
 * (CLAUDE-security.md): Ein `<script>` in der Quelle bleibt Text.
 *
 * - **Abschnittskarten** (Issue #474, F1): Jeder `##`-Abschnitt steht in einer eigenen
 *   {@link Karte}, deren Titel der Abschnittsname ist — „Karten statt Linien" (CLAUDE-design.md).
 *   Die Karten trennt {@link ABSTAND_BUEHNE}. Bloecke vor dem ersten `##` stehen in einer Karte
 *   ohne Titel. Jede Karte ist ein `section`, benannt nach ihrer Ueberschrift.
 * - **Zwischentitel**: `###` ist eine `h3` in der Gestalt „Name in Liste" (`typography.h4`,
 *   15 px / 700), mit deutlich mehr Abstand oben als unten. Nur die Gestalt folgt der Rolle, die
 *   Ebene im HTML bleibt richtig: Kartentitel `h2`, Zwischentitel `h3`.
 * - **Lesebreite** (E18): Die Textspalte jeder Karte laeuft hoechstens `70ch` breit, rund 75
 *   Zeichen je Zeile; die Karte um sie herum bleibt auf Buehnenbreite.
 * - **Ueberschriften** tragen ihren Anker als `id` und `tabIndex={-1}` (E8): Der Browser rollt bei
 *   einem Ankerverweis selbst, und der Tastaturnutzer landet hinter der Ueberschrift. Den Titel der
 *   Abschnittskarte macht `titelId` der {@link Karte} dazu.
 * - **Befehlsbloecke** tragen die eine Schrift der Anwendung (E5, CLAUDE-design.md, „Eine
 *   Schrift"); ihre Gestalt ist die der Innenkarte — Toenung „Flaeche weich", Radius klein —, die
 *   Zeilenumbrueche bleiben stehen.
 */

/** Die Lesebreite der Textspalte (E18). */
const LESEBREITE = '70ch';

/** Ein `##`-Abschnitt: seine Ueberschrift — vor dem ersten `##` keine — und die Bloecke darunter. */
interface Abschnitt {
  readonly kopf: { readonly text: string; readonly anker: string } | null;
  readonly bloecke: Block[];
}

/** Die Bloecke nach `##` gruppiert; eine leere Gruppe vor dem ersten `##` entfaellt. */
function abschnitte(bloecke: readonly Block[]): Abschnitt[] {
  const gruppen: Abschnitt[] = [{ kopf: null, bloecke: [] }];
  for (const block of bloecke) {
    if (block.art === 'ueberschrift' && block.ebene === 2) {
      gruppen.push({ kopf: { text: block.text, anker: block.anker }, bloecke: [] });
    } else {
      gruppen[gruppen.length - 1].bloecke.push(block);
    }
  }
  return gruppen.filter((gruppe) => gruppe.kopf !== null || gruppe.bloecke.length > 0);
}

export interface DokumenttextProps {
  readonly bloecke: readonly Block[];
}

function Zeile({ inhalt }: { readonly inhalt: readonly Inline[] }) {
  return (
    <>
      {inhalt.map((teil, index) => {
        switch (teil.art) {
          case 'klartext':
            return teil.text;
          case 'betont':
            return <strong key={index}>{teil.text}</strong>;
          case 'schnipsel':
            return (
              <Box
                key={index}
                component="code"
                sx={(theme) => ({
                  // `code` bringt im Browser eine Monoschrift mit; hier gilt die eine Schrift (E5).
                  fontFamily: 'inherit',
                  fontSize: 'inherit',
                  padding: '1px 6px',
                  borderRadius: `${RADIUS_KLEIN}px`,
                  background: theme.vars.palette.kupferwolke.flaecheWeich,
                })}
              >
                {teil.text}
              </Box>
            );
          case 'verweis':
            return (
              <Link key={index} href={teil.ziel} underline="hover">
                {teil.text}
              </Link>
            );
        }
      })}
    </>
  );
}

/** `erster`: der Block steht oben in seiner Karte; ein Zwischentitel dort braucht keinen Abstand. */
function Baustein({ block, erster }: { readonly block: Block; readonly erster: boolean }) {
  switch (block.art) {
    case 'ueberschrift':
      // Ein `##` steht hier nie — es ist der Titel seiner Abschnittskarte. Die Gestalt ist die
      // Rolle „Name in Liste" (`h4`), die Ebene im HTML `h3`.
      return (
        <Typography
          variant="h4"
          component="h3"
          id={block.anker}
          tabIndex={-1}
          sx={{ margin: `${erster ? '0' : '28px'} 0 6px` }}
        >
          {block.text}
        </Typography>
      );
    case 'text':
      return (
        <Typography variant="body1" sx={{ margin: '0 0 12px' }}>
          <Zeile inhalt={block.inhalt} />
        </Typography>
      );
    case 'liste':
      return (
        <Box
          component={block.numeriert ? 'ol' : 'ul'}
          sx={(theme) => ({ ...theme.typography.body1, margin: '0 0 12px', paddingLeft: '22px' })}
        >
          {block.eintraege.map((eintrag, index) => (
            <li key={index}>
              <Zeile inhalt={eintrag} />
            </li>
          ))}
        </Box>
      );
    case 'befehl':
      return (
        <Box
          component="pre"
          data-testid="dokumenttext-befehl"
          sx={(theme) => ({
            ...theme.typography.body1,
            // `pre` bringt im Browser eine Monoschrift mit; hier gilt die eine Schrift (E5).
            fontFamily: 'inherit',
            whiteSpace: 'pre-wrap',
            margin: '0 0 12px',
            padding: '14px 18px',
            borderRadius: `${RADIUS_KLEIN}px`,
            background: theme.vars.palette.kupferwolke.flaecheWeich,
          })}
        >
          {block.inhalt}
        </Box>
      );
  }
}

export default function Dokumenttext({ bloecke }: DokumenttextProps) {
  return (
    <Box
      data-testid="dokumenttext"
      sx={{ display: 'flex', flexDirection: 'column', gap: ABSTAND_BUEHNE, minWidth: 0 }}
    >
      {abschnitte(bloecke).map((abschnitt, index) => (
        <Box
          key={abschnitt.kopf?.anker ?? `vorab-${String(index)}`}
          component="section"
          data-testid="dokumenttext-abschnitt"
          aria-labelledby={abschnitt.kopf?.anker}
        >
          <Karte titel={abschnitt.kopf?.text} titelId={abschnitt.kopf?.anker}>
            <Box
              data-testid="dokumenttext-lesespalte"
              sx={{ maxWidth: LESEBREITE, '& > :last-child': { marginBottom: 0 } }}
            >
              {abschnitt.bloecke.map((block, nummer) => (
                <Baustein key={nummer} block={block} erster={nummer === 0} />
              ))}
            </Box>
          </Karte>
        </Box>
      ))}
    </Box>
  );
}
