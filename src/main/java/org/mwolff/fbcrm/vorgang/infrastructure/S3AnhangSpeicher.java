package org.mwolff.fbcrm.vorgang.infrastructure;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.vorgang.domain.AnhangSpeicher;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Setzt den Port {@link AnhangSpeicher} auf MinIO um (E7).
 *
 * <p>Pfadstil-Adressierung statt der virtuellen Hosts von AWS: MinIO laeuft unter einem festen
 * Hostnamen im Docker-Netz, und {@code <bucket>.minio} liesse sich dort nicht aufloesen. Die Region
 * ist ein Pflichtfeld des SDK ohne Bedeutung fuer MinIO; sie steht fest, damit kein Wert aus der
 * Umgebung des Prozesses einwandert.
 *
 * <p>Der Eimer wird beim Start angelegt, wenn er fehlt. Die Alternative waere ein Handgriff des
 * Betreibers vor dem ersten Anhang — er wuerde vergessen, und der Fehler zeigte sich erst beim
 * Hochladen. Geprueft wird ueber die Liste der Eimer und nicht ueber einen Fehlschlag von {@code
 * headBucket}: Ob ein fehlender Eimer dort als {@code NoSuchBucket} oder als nackter 404 ankommt,
 * haengt an der SDK-Version.
 *
 * <p>{@code final}, weil der Konstruktor mit dem Anlegen des Eimers eine Ausnahme werfen kann:
 * Ueber eine ableitbare Klasse liesse sich aus einem halb gebauten Objekt per Finalizer noch etwas
 * machen (SpotBugs {@code CT_CONSTRUCTOR_THROW}). Abgeleitet wird hier ohnehin nichts — der
 * Austauschpunkt ist der Port {@link AnhangSpeicher}, nicht diese Klasse.
 */
@Component
final class S3AnhangSpeicher implements AnhangSpeicher {

  private final S3Client s3;
  private final String bucket;

  S3AnhangSpeicher(final MinioProperties zugang) {
    this.bucket = zugang.bucket();
    this.s3 =
        S3Client.builder()
            .endpointOverride(URI.create(zugang.endpoint()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(zugang.accessKey(), zugang.secretKey())))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build();
    legeDenEimerAnWennErFehlt();
  }

  private void legeDenEimerAnWennErFehlt() {
    final boolean vorhanden =
        s3.listBuckets().buckets().stream().map(Bucket::name).anyMatch(bucket::equals);
    if (!vorhanden) {
      s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    }
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
