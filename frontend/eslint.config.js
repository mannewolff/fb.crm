import js from '@eslint/js';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import testingLibrary from 'eslint-plugin-testing-library';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['dist', 'coverage', 'node_modules'],
  },
  js.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    // Typisierte Regeln: ohne projectService greift @typescript-eslint/no-deprecated nicht
    // (CLAUDE-react.md, ESLint-/A11y-Gate). Sie haengen nur an TypeScript-Dateien, weil
    // eslint.config.js selbst in keinem tsconfig-Projekt liegt.
    extends: [
      ...tseslint.configs.recommendedTypeChecked,
      react.configs.flat.recommended,
      react.configs.flat['jsx-runtime'],
    ],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      'react-hooks': reactHooks,
      'jsx-a11y': jsxA11y,
    },
    settings: {
      react: { version: 'detect' },
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,
      // Ein veraltetes Idiom soll im Gate scheitern, nicht in einer Bitte in der Doku
      // (CLAUDE-react.md, „Leitplanke im Gate statt Doku, die bittet").
      '@typescript-eslint/no-deprecated': 'error',
    },
  },
  {
    // Test-Anti-Muster (etwa waitFor statt findBy) nur an Testdateien pruefen.
    ...testingLibrary.configs['flat/react'],
    files: ['**/*.test.{ts,tsx}'],
  },
  {
    // Die Vite-Konfiguration und diese Datei laufen in Node.
    files: ['vite.config.ts', 'eslint.config.js'],
    languageOptions: {
      globals: globals.node,
    },
  },
);
