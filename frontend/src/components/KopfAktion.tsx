import { createContext, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { createPortal } from 'react-dom';

/**
 * Die Hauptaktion einer Ansicht steht im Kopf, rechts vor dem Nutzer-Mal (E11, Entscheid Manne
 * 2026-09-24). Der Kopf steht aber im Rahmen und nicht in der Ansicht — dieses Modul ist die
 * Bruecke: {@link AppShell} spannt den Kontext auf, {@link TopBar} meldet den Platz an, und eine
 * Seite legt ihre Aktion mit {@link KopfAktion} dort hinein.
 *
 * **Ein Portal und kein Zustand.** Die Aktion bleibt ein Kind der Seite und wird nur woanders
 * gezeichnet. Damit raeumt sich der Platz beim Seitenwechsel von selbst — die Seite geht, ihr
 * Portal geht mit. Liefe die Aktion stattdessen als `ReactNode` durch einen Zustand, muesste jede
 * Seite ihr Element merken: Ein frisch erzeugtes JSX-Element ist bei jedem Rendern ein neues, und
 * der Effekt, der es ablegt, liefe endlos.
 */

interface KopfAktionWert {
  /** Der angemeldete Platz im Kopf, oder `null`, solange der Kopf noch nicht steht. */
  readonly platz: HTMLElement | null;
  /** Ref-Rueckruf des Kopfes. */
  readonly meldePlatz: (element: HTMLElement | null) => void;
}

const Kontext = createContext<KopfAktionWert | null>(null);

function useKontext(): KopfAktionWert {
  const wert = useContext(Kontext);
  if (wert === null) {
    throw new Error('KopfAktion steht nur innerhalb von AppShell zur Verfügung.');
  }
  return wert;
}

/** Spannt den Kontext auf; gehoert um Kopf und Inhalt herum. */
export function KopfAktionProvider({ children }: { readonly children: ReactNode }) {
  const [platz, meldePlatz] = useState<HTMLElement | null>(null);
  const wert = useMemo(() => ({ platz, meldePlatz }), [platz]);
  return <Kontext.Provider value={wert}>{children}</Kontext.Provider>;
}

/** Der Ref-Rueckruf, mit dem der Kopf seinen Platz anmeldet. */
export function useKopfAktionPlatz(): (element: HTMLElement | null) => void {
  return useKontext().meldePlatz;
}

/** Legt die Hauptaktion der Seite in den Kopf. */
export default function KopfAktion({ children }: { readonly children: ReactNode }) {
  const { platz } = useKontext();
  return platz === null ? null : createPortal(children, platz);
}
