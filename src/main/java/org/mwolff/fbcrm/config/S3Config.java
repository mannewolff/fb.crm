package org.mwolff.fbcrm.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Der eine Zugang zum Objektspeicher (E9).
 *
 * <p>Er steht hier und nicht in einem Fachmodul, weil ihn jeder archivierte Beleg braucht — heute
 * das Angebot, spaeter die Rechnung. Ein Client je Modul waere ein zweiter Verbindungspool auf
 * denselben Dienst und eine zweite Stelle, an der Endpunkt, Pfadstil und Region altern.
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
 */
@Configuration
public class S3Config {

  /**
   * Der Client auf den Objektspeicher, mit angelegtem Eimer.
   *
   * @param zugang Endpunkt, Zugangsdaten und Eimer aus {@code fbcrm.minio.*}
   * @return ein einsatzfaehiger Client — der Eimer aus {@code zugang} existiert danach
   */
  @Bean
  public S3Client s3Client(final MinioProperties zugang) {
    final S3Client s3 =
        S3Client.builder()
            .endpointOverride(URI.create(zugang.endpoint()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(zugang.accessKey(), zugang.secretKey())))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build();
    legeDenEimerAnWennErFehlt(s3, zugang.bucket());
    return s3;
  }

  private static void legeDenEimerAnWennErFehlt(final S3Client s3, final String eimer) {
    final boolean vorhanden =
        s3.listBuckets().buckets().stream().map(Bucket::name).anyMatch(eimer::equals);
    if (!vorhanden) {
      s3.createBucket(CreateBucketRequest.builder().bucket(eimer).build());
    }
  }
}
