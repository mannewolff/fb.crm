package org.mwolff.fbcrm.vorgang.application;

import java.util.Objects;
import org.mwolff.fbcrm.vorgang.domain.AnhangSpeicher;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.EintragRepository;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Das Herausgeben eines Anhangs (Kriterium 17).
 *
 * <p><b>Ein Zugriff auf den Bestand, nicht zwei.</b> Wie beim {@link EintragAendernUseCase} wird
 * der Eintrag geprueft und nicht zusaetzlich der Vorgang: Passt seine Vorgangskennung nicht zum
 * Pfad, ist er unter dieser Adresse nicht vorhanden — und das gilt auch dann, wenn es den Vorgang
 * im Pfad gar nicht gibt. Ein Kommentar traegt keine Datei und ist unter dem Dateiweg aus demselben
 * Grund nicht vorhanden.
 *
 * <p>Der Datenstrom geht offen an den Aufrufer (siehe {@link AnhangInhalt}). Er wird bewusst
 * innerhalb der Transaktion geoeffnet, aber ausserhalb gelesen: Der Objektspeicher hat mit der
 * Datenbanktransaktion nichts zu tun, und die Zeile ist zu diesem Zeitpunkt bereits gelesen.
 */
@Service
@Transactional(readOnly = true)
public class AnhangLesenUseCase {

  private final EintragRepository eintraege;
  private final AnhangSpeicher speicher;

  public AnhangLesenUseCase(final EintragRepository eintraege, final AnhangSpeicher speicher) {
    this.eintraege = eintraege;
    this.speicher = speicher;
  }

  /**
   * Der Anhang zu einer Adresse — Name, Groesse und der offene Datenstrom seiner Bytes.
   *
   * @param vorgangId Kennung des Vorgangs aus dem Pfad
   * @param eintragId Kennung des Eintrags
   * @throws EintragNichtGefunden wenn es unter dieser Adresse keinen Anhang gibt
   * @throws IllegalStateException wenn die Zeile steht, das Objekt im Speicher aber fehlt
   */
  public AnhangInhalt lese(final long vorgangId, final long eintragId) {
    final Eintrag anhang =
        eintraege
            .findById(eintragId)
            .filter(eintrag -> eintrag.vorgangId() == vorgangId)
            .filter(eintrag -> eintrag.art() == Eintragsart.ANHANG)
            .orElseThrow(EintragNichtGefunden::new);
    /*
     * Die drei Angaben sind bei einem Anhang gesetzt — die Fabrik Eintrag.anhang verlangt sie, und
     * die Checks der Migration sagen dasselbe noch einmal in der Datenbank. requireNonNull macht
     * das fuer den Typpruefer sichtbar, ohne eine Verzweigung zu bauen, die kein Test erreichen
     * koennte.
     */
    final String objektSchluessel = Objects.requireNonNull(anhang.objektSchluessel());
    return new AnhangInhalt(
        Objects.requireNonNull(anhang.dateiName()),
        Objects.requireNonNull(anhang.dateiGroesse()),
        speicher
            .lesen(objektSchluessel)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Zum Anhang mit der Kennung "
                            + eintragId
                            + " gibt es im Objektspeicher kein Objekt "
                            + objektSchluessel
                            + ".")));
  }
}
