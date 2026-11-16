import Box from '@mui/material/Box';
import type { FormEvent, ReactNode } from 'react';

import Maskenmeldungen from './Maskenmeldungen';
import type { MaskenmeldungenProps } from './Maskenmeldungen';

/**
 * Das Formular einer Maske (Issue #491): die Formular-Box ohne Pruefung des Browsers, oben die
 * {@link Maskenmeldungen}, darunter die Eingabefelder als `children`.
 *
 * Eine Komponente fuer alle Masken statt einer Abschrift je Maske — dieselbe Begruendung wie bei
 * den Maskenmeldungen selbst.
 */
export interface MaskenformularProps extends MaskenmeldungenProps {
  /** Das Absenden des Formulars; das Ereignis geht unveraendert weiter. */
  readonly onSubmit: (ereignis: FormEvent<HTMLFormElement>) => void;
  /** Die Eingabefelder und Schaltflaechen der Maske. */
  readonly children: ReactNode;
}

export default function Maskenformular({ onSubmit, children, ...meldungen }: MaskenformularProps) {
  return (
    <Box
      component="form"
      noValidate
      onSubmit={onSubmit}
      sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}
    >
      <Maskenmeldungen {...meldungen} />
      {children}
    </Box>
  );
}
