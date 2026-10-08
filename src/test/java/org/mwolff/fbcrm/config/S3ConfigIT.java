package org.mwolff.fbcrm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mwolff.fbcrm.AbstractIntegrationTest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Bucket;

/**
 * Die Eimeranlage beim Start gegen eine echte MinIO-Instanz.
 *
 * <p>Gegenstand ist die Zusage, die {@link S3Config} ueber das Zusammenbauen des Clients hinaus
 * gibt: Der Eimer der Anhaenge ist nach dem Start da. Ohne ihn beantwortete MinIO jeden Zugriff mit
 * {@code NoSuchBucket} statt mit einer Auskunft, und der Fehler zeigte sich erst beim ersten
 * Hochladen — lange nach dem Handgriff, der ihn verursacht hat.
 *
 * <p>Der Test baut die Bean selbst, statt die der Anwendung zu nehmen: Nur mit einem Eimernamen,
 * den es in dieser Instanz noch nicht gibt, ist das Anlegen nachweisbar. Der Eimer der Suite
 * existiert, sobald der Kontext steht.
 */
class S3ConfigIT extends AbstractIntegrationTest {

  private static MinioProperties zugangMitEimer(final String eimer) {
    return new MinioProperties(
        objektspeicher().getS3URL(),
        objektspeicher().getUserName(),
        objektspeicher().getPassword(),
        eimer);
  }

  private static boolean eimerVorhanden(final S3Client s3, final String eimer) {
    return s3.listBuckets().buckets().stream().map(Bucket::name).anyMatch(eimer::equals);
  }

  @Test
  void s3Client_givenABucketThatDoesNotExistYet_thenCreatesItAtStartup() {
    // Given
    final String eimer = "frisch-" + UUID.randomUUID();

    // When
    final S3Client s3 = new S3Config().s3Client(zugangMitEimer(eimer));

    // Then
    assertThat(eimerVorhanden(s3, eimer)).isTrue();
  }

  @Test
  void s3Client_givenAnExistingBucket_thenLeavesItAloneAndStartsAnyway() {
    // Given — derselbe Eimer ein zweites Mal: der Fall jedes Neustarts mit Bestand.
    final String eimer = "frisch-" + UUID.randomUUID();
    new S3Config().s3Client(zugangMitEimer(eimer));

    // When / Then — ein zweites createBucket waere ein BucketAlreadyOwnedByYou und liesse den
    // Start scheitern.
    assertThatCode(() -> new S3Config().s3Client(zugangMitEimer(eimer))).doesNotThrowAnyException();
  }
}
