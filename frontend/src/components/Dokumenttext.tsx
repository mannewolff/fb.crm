import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import Typography from '@mui/material/Typography';

import type { Block, Inline } from '../lib/markdown';
import { RADIUS_KLEIN } from '../theme';

/**
 * Der Dokumenttext: die Bloecke des Markdown-Lesers als Oberflaeche (Plan-Entscheidungen E5, E8,
 * E18).
 *
 * Der Baustein baut React-Elemente aus Daten und setzt nie rohes HTML in die Seite
 * (CLAUDE-security.md): Ein `<script>` in der Quelle bleibt Text.
 *
 * - **Lesebreite** (E18): Die Textspalte laeuft hoechstens `70ch` breit, rund 75 Zeichen je Zeile;
 *   die Karte um sie herum bleibt auf Buehnenbreite.
 * - **Ueberschriften** tragen ihren Anker als `id` und `tabIndex={-1}` (E8): Der Browser rollt bei
 *   einem Ankerverweis selbst, und der Tastaturnutzer landet hinter der Ueberschrift.
 * - **Befehlsbloecke** tragen die eine Schrift der Anwendung (E5, CLAUDE-design.md, „Eine
 *   Schrift"); ihre Gestalt ist die der Innenkarte — Toenung „Flaeche weich", Radius klein —, die
 *   Zeilenumbrueche bleiben stehen.
 */

/** Die Lesebreite der Textspalte (E18). */
const LESEBREITE = '70ch';

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

function Baustein({ block }: { readonly block: Block }) {
  switch (block.art) {
    case 'ueberschrift':
      return (
        <Typography
          variant={block.ebene === 2 ? 'h2' : 'h3'}
          id={block.anker}
          tabIndex={-1}
          sx={{ margin: '24px 0 8px' }}
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
    <Box data-testid="dokumenttext" sx={{ maxWidth: LESEBREITE }}>
      {bloecke.map((block, index) => (
        <Baustein key={index} block={block} />
      ))}
    </Box>
  );
}
