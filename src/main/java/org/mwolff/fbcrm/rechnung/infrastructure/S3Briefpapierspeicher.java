package org.mwolff.fbcrm.rechnung.infrastructure;

import java.util.UUID;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.rechnung.domain.Briefpapierspeicher;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Setzt den Port {@link Briefpapierspeicher} auf MinIO um (Plan #363, E6), nach dem Muster von
 * {@link S3DokumentSpeicher}.
 *
 * <p>Den Client und den angelegten Eimer liefert {@code config.S3Config}; aus {@link
 * MinioProperties} kennt dieser Adapter nur den Namen des Eimers.
 *
 * <p>Der Schluessel entsteht hier: {@code briefpapier/<uuid>.pdf}. Das Praefix trennt das
 * Briefpapier von Belegen und nachgetragenen Originalen im selben Eimer, der Zufallsname sorgt
 * dafuer, dass ein ersetztes Briefpapier das vorige nicht ueberschreibt.
 *
 * <p>Die Ausnahmen des SDK bleiben in diesem Adapter und kommen als {@link DokumentSpeicherAusfall}
 * heraus (CLAUDE-java.md §6.1).
 */
@Component
class S3Briefpapierspeicher implements Briefpapierspeicher {

  private final S3Client s3;
  private final String bucket;

  S3Briefpapierspeicher(final S3Client s3, final MinioProperties zugang) {
    this.s3 = s3;
    this.bucket = zugang.bucket();
  }

  @Override
  public String lege(final byte[] inhalt) {
    final String schluessel = "briefpapier/" + UUID.randomUUID() + ".pdf";
    try {
      s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(schluessel).build(),
          RequestBody.fromBytes(inhalt));
    } catch (final SdkException ausfall) {
      throw new DokumentSpeicherAusfall(
          "Der Objektspeicher hat das Briefpapier nicht angenommen.", ausfall);
    }
    return schluessel;
  }

  @Override
  public byte[] lies(final String schluessel) {
    try {
      return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(schluessel).build())
          .asByteArray();
    } catch (final SdkException ausfall) {
      throw new DokumentSpeicherAusfall(
          "Der Objektspeicher hat das Briefpapier nicht geliefert.", ausfall);
    }
  }

  @Override
  public void loesche(final String schluessel) {
    // S3 meldet das Loeschen eines unbekannten Schluessels als Erfolg; der Port sagt genau das zu.
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(schluessel).build());
    } catch (final SdkException ausfall) {
      throw new DokumentSpeicherAusfall(
          "Der Objektspeicher hat das Briefpapier nicht entfernt.", ausfall);
    }
  }
}
