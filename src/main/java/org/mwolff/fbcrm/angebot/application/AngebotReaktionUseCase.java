package org.mwolff.fbcrm.angebot.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.angebot.domain.Angebotszustand;
import org.mwolff.fbcrm.vorgang.application.EreignisVermerkenUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Reaktion des Kunden auf ein Angebot (Kriterien 17, 18, 19).
 *
 * <p><b>Aus welchen Zustaenden reagiert werden darf, entscheidet die Domaene.</b> {@link
 * Angebot#angenommen} und {@link Angebot#abgelehnt} lassen {@code VERSENDET} und {@code ABGELOEST}
 * zu — jeweils unabhaengig vom Datum, also auch am abgelaufenen Angebot (Kriterium 18, E4) — und
 * werfen sonst {@link AngebotNichtAenderbar}. Weil der Uebergang <b>vor</b> dem ersten Schreiben
 * steht, hinterlaesst ein abgewiesener Versuch nichts: keine Zeile, keinen Historieneintrag.
 *
 * <p>Der abgeloeste Fall ist der, den F13 begruendet: Auf A-2026-001 folgt nach einer
 * Nachverhandlung A-2026-002, das erste wechselt dabei nach {@code ABGELOEST} — und dann sagt der
 * Kunde „wir nehmen das erste".
 *
 * <p><b>Die Annahme beendet die uebrigen offenen Angebote</b> (Kriterium 17): Wer zugesagt hat,
 * bekommt keine zweite Zusage. „Offen" heisst {@link Angebot#offen} und damit unabhaengig von der
 * Gueltigkeit. Eine Ablehnung loest nichts ab — sie sagt nur etwas ueber dieses eine Angebot.
 *
 * <p><b>Der Vorgang wird nicht gefragt.</b> Die Schreibsperre am abgeschlossenen Vorgang zieht
 * Kriterium 9 um das Anlegen und das Versenden, nicht um die Reaktion des Kunden; eine Ausnahme
 * bleibt so klein wie ihr Anlass (E13). Dass dieser Klasse der Vorgang nur als Kennung bekannt ist,
 * ist die einfachste Form dieser Zusage.
 *
 * <p><b>Alles in einer Transaktion:</b> die Reaktion, die Abloesungen und jeder Historieneintrag.
 * Bleibt eines davon aus, ist keines davon geschehen.
 */
@Service
@Transactional
public class AngebotReaktionUseCase {

  private final AngebotRepository angebote;
  private final EreignisVermerkenUseCase ereignisse;
  private final Clock clock;

  public AngebotReaktionUseCase(
      final AngebotRepository angebote,
      final EreignisVermerkenUseCase ereignisse,
      final Clock clock) {
    this.angebote = angebote;
    this.ereignisse = ereignisse;
    this.clock = clock;
  }

  /**
   * Markiert das Angebot als angenommen und loest die uebrigen offenen Angebote des Vorgangs ab.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet oder abgeloest ist
   */
  public AngebotAnsicht nimmAn(final long angebotId) {
    return reagiere(angebotId, Angebotszustand.ANGENOMMEN);
  }

  /**
   * Markiert das Angebot als abgelehnt; die uebrigen Angebote des Vorgangs bleiben, wie sie sind.
   *
   * @param angebotId Kennung des Angebots
   * @throws AngebotNichtGefunden wenn es das Angebot nicht gibt
   * @throws AngebotNichtAenderbar wenn das Angebot nicht versendet oder abgeloest ist
   */
  public AngebotAnsicht lehneAb(final long angebotId) {
    return reagiere(angebotId, Angebotszustand.ABGELEHNT);
  }

  /*
   * Beide Wege unterscheiden sich allein im Zielzustand — und darin, dass nur die Annahme abloest.
   * Der Zielzustand kommt als Parameter und nicht als Rumpf eines dritten Wegs: Der Uebergang steht
   * im Pfad und ist damit aus dem Zugriffsprotokoll lesbar.
   */
  private AngebotAnsicht reagiere(final long angebotId, final Angebotszustand ziel) {
    final Angebot angebot = angebote.findById(angebotId).orElseThrow(AngebotNichtGefunden::new);
    final Instant jetzt = clock.instant();
    final boolean annahme = ziel == Angebotszustand.ANGENOMMEN;
    final Angebot reagiert =
        angebote.save(annahme ? angebot.angenommen(jetzt) : angebot.abgelehnt(jetzt));
    ereignisse.vermerken(
        angebot.vorgangId(),
        "Angebot %s %s".formatted(nummer(angebot), annahme ? "angenommen" : "abgelehnt"));
    if (annahme) {
      loeseDieUebrigenAb(angebot.vorgangId(), angebotId, jetzt);
    }
    return AngebotAnsicht.of(reagiert, clock);
  }

  /*
   * Kriterium 17: Eine Zusage beendet die uebrigen offenen Angebote desselben Vorgangs — dieselbe
   * Bewegung wie beim zweiten Versand (Kriterium 25), nur mit einem anderen Anlass. Abgeloest wird
   * nur, was offen ist; die verstrichene Gueltigkeit schliesst ein Angebot dabei nicht
   * (Kriterium 18).
   */
  private void loeseDieUebrigenAb(final long vorgangId, final long angebotId, final Instant jetzt) {
    final LocalDate heute = AngebotAnsicht.heute(clock);
    for (final Angebot anderes : angebote.findByVorgang(vorgangId)) {
      if (anderes.requireId() != angebotId && anderes.offen(heute)) {
        angebote.save(anderes.abgeloest(jetzt));
        ereignisse.vermerken(vorgangId, "Angebot %s abgeloest".formatted(nummer(anderes)));
      }
    }
  }

  /* Wer reagiert oder abgeloest wird, ist festgeschrieben und traegt darum eine Nummer. */
  private static String nummer(final Angebot angebot) {
    return Objects.requireNonNull(angebot.nummer());
  }
}
