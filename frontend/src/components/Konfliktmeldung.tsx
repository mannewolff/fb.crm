import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import { IconAlertTriangle } from '@tabler/icons-react';

import { konfliktAngaben } from '../lib/apifehler';
import WeicheTaste from './WeicheTaste';

/**
 * Die Abweisung einer Aenderung, weil sich der Stand seit dem Oeffnen geaendert hat (#411,
 * Kriterien 2, 3 und 6).
 *
 * Eine Komponente fuer alle Ansichten und keine Abschrift je Maske (Plan #415, E12): Satz, Toenung
 * und Weg weiter sind ueberall dieselben, und eine Kopie liefe beim ersten Nachziehen auseinander.
 *
 * <b>Wort und Symbol, nicht Farbe allein</b> (CLAUDE-design.md, „Zustandsformen"): Die Toenung
 * Bernstein stuetzt die Aussage, das Wort im Titel und das Warndreieck tragen sie.
 *
 * Der Weg weiter haengt an der Art: Bei einem geaenderten Eintrag holt „Neu laden" den Stand des
 * Servers, bei einem geloeschten gibt es nichts mehr zu laden, und „Zur Übersicht" fuehrt zurueck.
 * Die eigenen Eingaben zeigt daneben {@link EigeneEingaben}, nicht diese Meldung.
 *
 * Ist die Ursache kein Bearbeitungskonflikt, zeigt die Komponente nichts — die Ansicht meldet
 * jeden anderen Fehlschlag auf ihrem gewohnten Weg.
 */

/** Die Symbolgroesse im Titel der Meldung. */
const SYMBOL_MELDUNG = 20;

export interface KonfliktmeldungProps {
  /** Der Fehlschlag der Anfrage, ungeprueft. */
  readonly ursache: unknown;
  /** Holt den Stand des Servers samt neuer Version in die Maske. */
  readonly onNeuLaden: () => void;
  /** Der Weg zur Uebersicht, wenn der Eintrag geloescht ist. */
  readonly uebersicht: string;
  /** Der Augenblick, an dem „heute" gemessen wird; ohne Angabe die Uhr des Rechners. */
  readonly jetzt?: Date;
}

export default function Konfliktmeldung({
  ursache,
  onNeuLaden,
  uebersicht,
  jetzt,
}: KonfliktmeldungProps) {
  const angaben = konfliktAngaben(ursache, jetzt);
  if (angaben === null) {
    return null;
  }
  const aktion =
    angaben.art === 'gelöscht' ? (
      <WeicheTaste to={uebersicht}>Zur Übersicht</WeicheTaste>
    ) : (
      <WeicheTaste onClick={onNeuLaden}>Neu laden</WeicheTaste>
    );
  return (
    <Alert
      severity="warning"
      icon={<IconAlertTriangle
          data-testid="konflikt-symbol"
          size={SYMBOL_MELDUNG}
          stroke={1.8}
          aria-hidden
        />}
      action={aktion}
      sx={(theme) => ({
        alignItems: 'center',
        background: theme.vars.palette.kupferwolke.toenung.bernstein.flaeche,
        color: theme.vars.palette.kupferwolke.toenung.bernstein.schrift,
        '& .MuiAlert-icon': { color: theme.vars.palette.kupferwolke.toenung.bernstein.schrift },
      })}
    >
      <AlertTitle>Nicht ausgeführt</AlertTitle>
      {angaben.satz}
    </Alert>
  );
}
