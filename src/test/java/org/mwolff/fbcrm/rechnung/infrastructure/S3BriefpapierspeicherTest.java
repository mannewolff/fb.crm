package org.mwolff.fbcrm.rechnung.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.config.MinioProperties;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicherAusfall;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Der Adapter des Briefpapiers ohne Objektspeicher (Plan #363, E6).
 *
 * <p>Gegenstand ist, was {@link S3BriefpapierspeicherIT} gegen MinIO nicht herbeifuehren kann: Ein
 * ausgefallener Speicher kommt bei jeder der drei Methoden als {@link DokumentSpeicherAusfall}
 * heraus, und keine Ausnahme des SDK dringt nach aussen. Dazu, an welchen Eimer und welchen
 * Schluessel der Adapter seine Aufrufe richtet.
 */
@ExtendWith(MockitoExtension.class)
class S3BriefpapierspeicherTest {

  private static final String EIMER = "fbcrm-briefe";
  private static final String SCHLUESSEL = "briefpapier/8f3c.pdf";
  private static final byte[] BRIEFPAPIER = "%PDF-1.7 Briefpapier".getBytes(UTF_8);

  @Mock private S3Client s3;

  private S3Briefpapierspeicher speicher;

  @BeforeEach
  void richteDenAdapterEin() {
    speicher =
        new S3Briefpapierspeicher(
            s3, new MinioProperties("http://minio:9000", "zugang", "geheim", EIMER));
  }

  @Test
  void lege_thenPutsUnderTheReturnedKeyIntoTheBucket() {
    // When
    final String schluessel = speicher.lege(BRIEFPAPIER);

    // Then
    final ArgumentCaptor<PutObjectRequest> anfrage =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(anfrage.capture(), any(RequestBody.class));
    assertThat(anfrage.getValue().bucket()).isEqualTo(EIMER);
    assertThat(anfrage.getValue().key()).isEqualTo(schluessel).startsWith("briefpapier/");
  }

  @Test
  void lies_thenReturnsTheBytesOfTheObject() {
    // Given
    final GetObjectRequest erwartet =
        GetObjectRequest.builder().bucket(EIMER).key(SCHLUESSEL).build();
    when(s3.getObjectAsBytes(erwartet))
        .thenReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), BRIEFPAPIER));

    // When / Then
    assertThat(speicher.lies(SCHLUESSEL)).containsExactly(BRIEFPAPIER);
  }

  @Test
  void loesche_thenDeletesTheKeyFromTheBucket() {
    // When
    speicher.loesche(SCHLUESSEL);

    // Then
    verify(s3).deleteObject(DeleteObjectRequest.builder().bucket(EIMER).key(SCHLUESSEL).build());
  }

  @Test
  void lege_givenTheStoreFails_thenThrowsDokumentSpeicherAusfall() {
    // Given
    final SdkClientException ausfall = SdkClientException.create("nicht erreichbar");
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(ausfall);

    // When / Then
    assertThatThrownBy(() -> speicher.lege(BRIEFPAPIER))
        .isInstanceOf(DokumentSpeicherAusfall.class)
        .hasCause(ausfall);
  }

  @Test
  void lies_givenTheStoreFails_thenThrowsDokumentSpeicherAusfall() {
    // Given
    final SdkClientException ausfall = SdkClientException.create("nicht erreichbar");
    when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(ausfall);

    // When / Then
    assertThatThrownBy(() -> speicher.lies(SCHLUESSEL))
        .isInstanceOf(DokumentSpeicherAusfall.class)
        .hasCause(ausfall);
  }

  @Test
  void loesche_givenTheStoreFails_thenThrowsDokumentSpeicherAusfall() {
    // Given
    final SdkClientException ausfall = SdkClientException.create("nicht erreichbar");
    when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(ausfall);

    // When / Then
    assertThatThrownBy(() -> speicher.loesche(SCHLUESSEL))
        .isInstanceOf(DokumentSpeicherAusfall.class)
        .hasCause(ausfall);
  }
}
