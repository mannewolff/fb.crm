package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.UUID;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
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
}
