package org.mwolff.fbcrm.vorgang.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.mwolff.fbcrm.common.Uploadgrenze;
import org.mwolff.fbcrm.vorgang.domain.AnhangSpeicher;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;
import org.mwolff.fbcrm.vorgang.domain.VorgangRepository;
import org.mwolff.fbcrm.vorgang.domain.Zeitpunktgrenze;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Hinzufuegen eines Kommentars oder eines Anhangs zur Historie (Kriterien 13, 14, 18).
 *
 * <p>Die Reihenfolge der Pruefungen ist Absicht. Zuerst der Zeitpunkt, dann der Vorgang, dann die
 * Groesse, erst danach der Objektspeicher: Jede Eingabe, die ohnehin abgewiesen wird, soll weder
 * eine Abfrage noch — schlimmer — ein abgelegtes Objekt kosten.
 *
 * <p>Beim Anhang steht das Objekt <b>vor</b> der Zeile (E8). Ein Rollback laesst damit hoechstens
 * eine Waise im Speicher liegen; die umgekehrte Reihenfolge hinterliesse eine Zeile, deren Datei es
 * nicht gibt, und das braeche Kriterium 17 sichtbar.
 *
 * <p>Was Text und Dateiangaben verlangen, steht in {@link Eintrag} und wird hier nicht wiederholt.
 * Der Anwendungsfall engt die Eingabe nur so weit ein, dass die Fabriken der Domaene sie annehmen
 * koennen — die Meldung am Feld entsteht in der Bean Validation der Anfrage, nicht hier.
 */
@Service
@Transactional
public class EintragHinzufuegenUseCase {

  private final VorgangRepository vorgaenge;
  private final EintragRepository eintraege;
  private final AnhangSpeicher speicher;
  private final Clock clock;

  public EintragHinzufuegenUseCase(
      final VorgangRepository vorgaenge,
      final EintragRepository eintraege,
      final AnhangSpeicher speicher,
      final Clock clock) {
    this.vorgaenge = vorgaenge;
    this.eintraege = eintraege;
    this.speicher = speicher;
    this.clock = clock;
  }

  /**
   * Legt den Eintrag an.
   *
   * @param vorgangId Kennung des Vorgangs, an dessen Historie der Eintrag haengt
   * @param daten die eingereichten Angaben
   * @return der gespeicherte Eintrag mit vergebener Kennung
   * @throws ZeitpunktInDerZukunft wenn der Zeitpunkt ueber der Toleranz aus E15 liegt
   * @throws VorgangNichtGefunden wenn es den Vorgang nicht gibt
   * @throws AnhangZuGross wenn die Datei die Grenze aus {@link Uploadgrenze} ueberschreitet
   * @throws IllegalArgumentException wenn einem Kommentar der Text oder einem Anhang die Datei
   *     fehlt
   */
  public Eintrag hinzufuegen(final long vorgangId, final EintragDaten daten) {
    if (Zeitpunktgrenze.inDerZukunft(daten.geschehenAm(), clock.instant())) {
      throw new ZeitpunktInDerZukunft();
    }
    if (vorgaenge.findById(vorgangId).isEmpty()) {
      throw new VorgangNichtGefunden();
    }
    final Instant jetzt = clock.instant();
    return eintraege.save(
        daten.art() == Eintragsart.ANHANG
            ? anhang(vorgangId, daten, jetzt)
            : kommentar(vorgangId, daten, jetzt));
  }

  /*
   * Ein fehlender Text kommt hier als null an; die Fabrik der Domaene kennt nur "leer". Der
   * Leerstring uebersetzt das eine in das andere, damit die Regel und ihre Meldung an genau einer
   * Stelle stehen — in Eintrag.kommentar.
   */
  private static Eintrag kommentar(
      final long vorgangId, final EintragDaten daten, final Instant jetzt) {
    return Eintrag.kommentar(
        vorgangId,
        Objects.requireNonNullElse(daten.text(), ""),
        daten.geschehenAm(),
        Herkunft.VON_HAND,
        jetzt);
  }

  private Eintrag anhang(final long vorgangId, final EintragDaten daten, final Instant jetzt) {
    final InputStream inhalt = daten.inhalt();
    final String dateiName = daten.dateiName();
    if (inhalt == null || dateiName == null) {
      throw new IllegalArgumentException("Ein Anhang braucht eine Datei.");
    }
    if (daten.dateiGroesse() > Uploadgrenze.MAX_BYTE) {
      throw new AnhangZuGross();
    }
    return Eintrag.anhang(
        vorgangId,
        daten.text(),
        daten.geschehenAm(),
        Herkunft.VON_HAND,
        dateiName,
        daten.dateiGroesse(),
        ablegen(vorgangId, inhalt, daten.dateiGroesse()),
        jetzt);
  }

  /*
   * Der Port sagt zu, dass der Aufrufer den Datenstrom schliesst — und der Aufrufer ist diese
   * Klasse. Ein Fehler beim Schliessen wird nicht verschluckt: Er hinterliesse ein offenes Handle
   * ohne jede Spur (CLAUDE-java.md §6.5).
   */
  private String ablegen(final long vorgangId, final InputStream inhalt, final long groesse) {
    try (inhalt) {
      return speicher.ablegen(vorgangId, inhalt, groesse);
    } catch (final IOException fehler) {
      throw new UncheckedIOException(
          "Der Datenstrom des Anhangs liess sich nicht schliessen.", fehler);
    }
  }
}
