package org.mwolff.fbcrm.vorgang.infrastructure;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.vorgang.domain.AnhangSpeicher;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Setzt den Port {@link AnhangSpeicher} auf MinIO um (E7).
 *
 * <p>Den Client baut nicht dieser Adapter, sondern {@code config.S3Config} — er ist der eine Zugang
 * der Anwendung, den auch andere Module brauchen (E9). Von dort kommt auch der angelegte Eimer;
 * dieser Adapter setzt ihn voraus und kennt aus {@link MinioProperties} nur seinen Namen.
 */
@Component
class S3AnhangSpeicher implements AnhangSpeicher {

  private final S3Client s3;
  private final String bucket;

  S3AnhangSpeicher(final S3Client s3, final MinioProperties zugang) {
    this.s3 = s3;
    this.bucket = zugang.bucket();
  }

  @Override
  public String ablegen(final long vorgangId, final InputStream inhalt, final long groesse) {
    final String objektSchluessel = "vorgang/" + vorgangId + "/" + UUID.randomUUID();
    s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(objektSchluessel).build(),
        RequestBody.fromInputStream(inhalt, groesse));
    return objektSchluessel;
  }

  @Override
  public Optional<InputStream> lesen(final String objektSchluessel) {
    try {
      return Optional.of(
          s3.getObject(GetObjectRequest.builder().bucket(bucket).key(objektSchluessel).build()));
    } catch (final NoSuchKeyException unbekannt) {
      return Optional.empty();
    }
  }
}
