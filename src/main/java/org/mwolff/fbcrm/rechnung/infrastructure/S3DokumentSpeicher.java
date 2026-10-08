package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.UUID;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Setzt den Port {@link DokumentSpeicher} auf MinIO um (E9).
 *
 * <p>Den Client baut nicht dieser Adapter, sondern {@code config.S3Config} — er ist der eine Zugang
 * der Anwendung, den jeder weitere Beleg teilt. Von dort kommt auch der angelegte Eimer; dieser
 * Adapter setzt ihn voraus und kennt aus {@link MinioProperties} nur seinen Namen.
 *
 * <p>Der Schluessel entsteht hier: {@code rechnung/<rechnungId>/<uuid>.pdf}. Das {@code rechnung/}
 * davor trennt die Rechnungen von den Anlagen am Angebot und von kuenftigen Belegarten, die Kennung
 * macht sie im Speicher zuordenbar, und der Zufallsname sorgt dafuer, dass eine zweite Ablage zur
 * selben Rechnung die erste nicht ueberschreibt.
 *
 * <p>Das hochgeladene Original einer nachgetragenen Rechnung liegt im selben Eimer unter {@code
 * rechnung-nachtrag/<nachtragId>/<uuid>.pdf} (Plan #259, E13). Fuer seine Ablage und fuer das
 * Loeschen bleiben die Ausnahmen des SDK in diesem Adapter: Sie kommen als {@link
 * DokumentSpeicherAusfall} heraus, den die Anwendungsschicht nach dem Commit fangen darf (E14),
 * ohne eine SDK-Klasse zu kennen (CLAUDE-java.md §6.1).
 */
@Component
class S3DokumentSpeicher implements DokumentSpeicher {

  private final S3Client s3;
  private final String bucket;

  S3DokumentSpeicher(final S3Client s3, final MinioProperties zugang) {
    this.s3 = s3;
    this.bucket = zugang.bucket();
  }

  @Override
  public String lege(final long rechnungId, final byte[] inhalt) {
    final String schluessel = "rechnung/" + rechnungId + "/" + UUID.randomUUID() + ".pdf";
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(schluessel).build(),
        RequestBody.fromBytes(inhalt));
    return schluessel;
  }

  @Override
  public byte[] lies(final String schluessel) {
    return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(schluessel).build())
        .asByteArray();
  }

  @Override
  public String legeHochgeladenes(final long nachtragId, final byte[] inhalt) {
    final String schluessel = "rechnung-nachtrag/" + nachtragId + "/" + UUID.randomUUID() + ".pdf";
    try {
      s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(schluessel).build(),
          RequestBody.fromBytes(inhalt));
    } catch (final SdkException ausfall) {
      throw new DokumentSpeicherAusfall(
          "Der Objektspeicher hat das Original nicht angenommen.", ausfall);
    }
    return schluessel;
  }

  @Override
  public void loesche(final String schluessel) {
    // S3 meldet das Loeschen eines unbekannten Schluessels als Erfolg; der Port sagt genau das zu.
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(schluessel).build());
    } catch (final SdkException ausfall) {
      throw new DokumentSpeicherAusfall(
          "Der Objektspeicher hat das Objekt nicht entfernt.", ausfall);
    }
  }
}
