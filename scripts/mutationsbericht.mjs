/**
 * Liest den XML-Bericht von PIT (`target/pit-reports/mutations.xml`) und wertet ihn aus
 * (Plan #348): Quote bilden, Quote anzeigen, Ueberlebende nach Datei gruppieren.
 *
 * Alles hier ist reine Funktion — kein Netz, kein Prozess, kein Dateisystem. Wer den Bericht
 * liest, reicht seinen Text herein; so sind die Szenarien des Fachplans #347 ohne PIT-Lauf
 * pruefbar.
 *
 * Laeuft mit nichts als Node: Der Bericht wird von einem eigenen kleinen Leser geprueft und
 * zerlegt, nicht von einer Bibliothek. Er kennt genau das, was ein wohlgeformtes XML-Dokument
 * ausmacht und PIT schreibt — Deklaration, Kommentare, CDATA, Elemente, Attribute in beiden
 * Anfuehrungszeichen, die fuenf vordefinierten Entitaeten und Zeichenreferenzen — und weist
 * alles andere ab.
 */

/** Die fuenf Entitaeten, die XML ohne DTD kennt. */
const ENTITAETEN = { lt: '<', gt: '>', amp: '&', quot: '"', apos: "'" };

const NAME = String.raw`[A-Za-z_:][\w.:-]*`;

const OEFFNEND = new RegExp(
  String.raw`^<(${NAME})((?:\s+${NAME}\s*=\s*(?:'[^'<]*'|"[^"<]*"))*)\s*(/?)>`,
);
const SCHLIESSEND = new RegExp(String.raw`^</(${NAME})\s*>`);
const ATTRIBUT = new RegExp(String.raw`(${NAME})\s*=\s*(?:'([^']*)'|"([^"]*)")`, 'g');

/** Ein Fehler, der sagt, warum der Bericht nicht ausgewertet wird. */
function ungueltig(grund) {
  return new Error(`Mutationsbericht ist kein gueltiges XML: ${grund}`);
}

function abgeschnitten(grund) {
  return new Error(`Mutationsbericht ist abgeschnitten: ${grund}`);
}

function entschluesselt(text) {
  return text.replace(/&([^;&\s<]*);?/g, (ganz, name) => {
    if (!ganz.endsWith(';')) {
      throw ungueltig(`'&' ohne abgeschlossene Entitaet: '${ganz}'`);
    }
    if (Object.hasOwn(ENTITAETEN, name)) return ENTITAETEN[name];
    const zahl = /^#(?:x([0-9a-fA-F]+)|(\d+))$/.exec(name);
    if (zahl === null) {
      throw ungueltig(`unbekannte Entitaet '&${name};'`);
    }
    return String.fromCodePoint(zahl[1] === undefined ? Number(zahl[2]) : parseInt(zahl[1], 16));
  });
}

/**
 * Zerlegt ein XML-Dokument in einen Baum aus `{ name, attribute, kinder, text }`.
 *
 * Abgeschnitten (E17) ist ein Dokument, dessen Text endet, bevor alles Geoeffnete geschlossen
 * ist — mitten in einem Tag, einem Kommentar oder mit offenem Wurzelelement. Jede andere
 * Verletzung der Wohlgeformtheit ist ungueltig.
 */
function zerlegt(xml) {
  let rest = xml;
  let wurzel = null;
  const offen = [];

  const bisZu = (ende, was) => {
    const stelle = rest.indexOf(ende);
    if (stelle < 0) throw abgeschnitten(`${was} endet nicht`);
    const inhalt = rest.slice(0, stelle);
    rest = rest.slice(stelle + ende.length);
    return inhalt;
  };

  while (rest.length > 0) {
    const aktuell = offen.at(-1);
    if (rest.startsWith('<?')) {
      bisZu('?>', 'Verarbeitungsanweisung');
    } else if (rest.startsWith('<!--')) {
      bisZu('-->', 'Kommentar');
    } else if (rest.startsWith('<![CDATA[')) {
      rest = rest.slice('<![CDATA['.length);
      const inhalt = bisZu(']]>', 'CDATA-Abschnitt');
      if (aktuell === undefined) throw ungueltig('Text ausserhalb des Wurzelelements');
      aktuell.text += inhalt;
    } else if (rest.startsWith('</')) {
      const treffer = SCHLIESSEND.exec(rest);
      if (treffer === null) {
        if (!rest.includes('>')) throw abgeschnitten('ein schliessendes Tag endet nicht');
        throw ungueltig(`kaputtes schliessendes Tag '${rest.slice(0, 40)}'`);
      }
      if (aktuell === undefined) {
        throw ungueltig(`</${treffer[1]}> ohne oeffnendes Tag`);
      }
      if (treffer[1] !== aktuell.name) {
        throw ungueltig(`</${treffer[1]}> schliesst, offen ist aber <${aktuell.name}>`);
      }
      offen.pop();
      rest = rest.slice(treffer[0].length);
    } else if (rest.startsWith('<')) {
      const treffer = OEFFNEND.exec(rest);
      if (treffer === null) {
        if (!rest.includes('>')) throw abgeschnitten('ein Tag endet nicht');
        throw ungueltig(`kaputtes Tag '${rest.slice(0, 40)}'`);
      }
      const attribute = {};
      for (const [, name, einfach, doppelt] of treffer[2].matchAll(ATTRIBUT)) {
        if (Object.hasOwn(attribute, name)) {
          throw ungueltig(`Attribut '${name}' steht an <${treffer[1]}> doppelt`);
        }
        attribute[name] = entschluesselt(einfach ?? doppelt);
      }
      const element = { name: treffer[1], attribute, kinder: [], text: '' };
      if (aktuell === undefined) {
        if (wurzel !== null) throw ungueltig(`zweites Wurzelelement <${element.name}>`);
        wurzel = element;
      } else {
        aktuell.kinder.push(element);
      }
      if (treffer[3] !== '/') offen.push(element);
      rest = rest.slice(treffer[0].length);
    } else {
      const ende = rest.indexOf('<');
      const text = ende < 0 ? rest : rest.slice(0, ende);
      rest = ende < 0 ? '' : rest.slice(ende);
      if (aktuell === undefined) {
        if (text.trim() !== '') throw ungueltig('Text ausserhalb des Wurzelelements');
      } else {
        aktuell.text += entschluesselt(text);
      }
    }
  }

  if (offen.length > 0) {
    throw abgeschnitten(`Wurzelelement <${offen[0].name}> ist nicht geschlossen`);
  }
  if (wurzel === null) throw ungueltig('kein Wurzelelement');
  return wurzel;
}

/** Der Text des Kindelements `name` einer Mutation; fehlt es, ist der Bericht unbrauchbar. */
function angabe(mutation, name) {
  const kind = mutation.kinder.find((element) => element.name === name);
  if (kind === undefined) {
    throw ungueltig(`einer Mutation fehlt <${name}>`);
  }
  return kind.text.trim();
}

/**
 * Der repository-relative Quellpfad einer Mutation (E7): das Paket von `mutatedClass` mit
 * Schraegstrichen, dahinter `sourceFile`. Der nackte Dateiname reicht nicht — `Angebot.java`
 * gibt es in mehreren Paketen.
 */
function quellpfad(mutation) {
  const klasse = angabe(mutation, 'mutatedClass');
  const punkt = klasse.lastIndexOf('.');
  const paket = punkt < 0 ? '' : `${klasse.slice(0, punkt).replaceAll('.', '/')}/`;
  return `src/main/java/${paket}${angabe(mutation, 'sourceFile')}`;
}

function stelle(mutation) {
  const zeile = angabe(mutation, 'lineNumber');
  if (!/^\d+$/.test(zeile)) {
    throw ungueltig(`<lineNumber> traegt keine Zeilennummer: '${zeile}'`);
  }
  return {
    datei: quellpfad(mutation),
    zeile: Number(zeile),
    methode: angabe(mutation, 'mutatedMethod'),
    mutator: angabe(mutation, 'mutator'),
  };
}

/**
 * Liest einen PIT-Bericht.
 *
 * Getoetet ist ein Mutant mit `detected="true"`; jeder andere — auch ein nicht abgedeckter —
 * gilt als ueberlebend (E4). Ausgewertet wird nur ein vollstaendiger Bericht (E17): Fehlt er,
 * ist er leer, nicht parsbar oder abgeschnitten, wirft die Funktion mit dem Grund. Eine
 * Auswertung „so weit lesbar" lieferte sonst eine erfundene Quote, wenn PIT nach dem ersten
 * geschriebenen Mutanten abbricht.
 *
 * @param {string|null|undefined} xml Inhalt von mutations.xml
 * @returns {{erzeugt: number, getoetet: number,
 *   ueberlebende: {datei: string, zeile: number, methode: string, mutator: string}[]}}
 */
export function berichtLesen(xml) {
  if (xml === undefined || xml === null) {
    throw new Error('Mutationsbericht fehlt');
  }
  if (xml.trim() === '') {
    throw new Error('Mutationsbericht ist leer');
  }
  const wurzel = zerlegt(xml);
  if (wurzel.name !== 'mutations') {
    throw ungueltig(`Wurzelelement <${wurzel.name}> statt <mutations>`);
  }
  const mutationen = wurzel.kinder.filter((element) => element.name === 'mutation');
  const getoetet = mutationen.filter((mutation) => mutation.attribute.detected === 'true');
  return {
    erzeugt: mutationen.length,
    getoetet: getoetet.length,
    ueberlebende: mutationen
      .filter((mutation) => mutation.attribute.detected !== 'true')
      .map(stelle),
  };
}

/**
 * Der Mutation Score: getoetete durch erzeugte Mutanten, ungerundet (E4).
 *
 * @param {{erzeugt: number, getoetet: number}} zaehlung
 * @returns {number|null} Wert in [0,1]; null, wenn nichts erzeugt wurde (nicht messbar)
 */
export function quote({ erzeugt, getoetet }) {
  return erzeugt === 0 ? null : getoetet / erzeugt;
}

/**
 * Die Quote zum Anzeigen: in Prozent, auf eine Nachkommastelle abgerundet, mit deutschem
 * Dezimalkomma (E4). Abgerundet, damit ein Wert knapp unter 80 % nie als „80,0 %" erscheint.
 *
 * Abgerundet wird erst nach einer Rundung auf neun Stellen: `0.7 * 1000` ergibt in Gleitkomma
 * `699.999…`, und ein blankes `Math.floor` zeigte daraus 69,9 %.
 *
 * @param {number|null} q die Quote aus {@link quote}
 * @returns {string} etwa „99,9 %"
 */
export function quoteText(q) {
  if (q === null) return 'keine Quote feststellbar';
  const promille = Math.floor(Number((q * 1000).toFixed(9)));
  return `${(promille / 10).toFixed(1).replace('.', ',')} %`;
}

function vergleich(a, b) {
  if (a.zeile !== b.zeile) return a.zeile - b.zeile;
  if (a.methode !== b.methode) return a.methode < b.methode ? -1 : 1;
  if (a.mutator !== b.mutator) return a.mutator < b.mutator ? -1 : 1;
  return 0;
}

/**
 * Gruppiert Ueberlebende nach ihrer Datei. Je Datei stehen die Stellen nach Zeile, Methode und
 * Mutator sortiert; zwei Stellen mit gleichem Tripel erscheinen nur einmal.
 *
 * @param {{datei: string, zeile: number, methode: string, mutator: string}[]} ueberlebende
 * @returns {Map<string, {datei: string, zeile: number, methode: string, mutator: string}[]>}
 */
export function nachDateiGruppieren(ueberlebende) {
  const gruppen = new Map();
  for (const stelle of ueberlebende) {
    const liste = gruppen.get(stelle.datei) ?? [];
    if (!liste.some((vorhanden) => vergleich(vorhanden, stelle) === 0)) {
      liste.push(stelle);
    }
    gruppen.set(stelle.datei, liste);
  }
  for (const liste of gruppen.values()) {
    liste.sort(vergleich);
  }
  return gruppen;
}
