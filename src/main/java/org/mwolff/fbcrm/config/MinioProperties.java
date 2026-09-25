package org.mwolff.fbcrm.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Der Zugang zum Objektspeicher der Anhaenge, gelesen aus {@code fbcrm.minio.*} (E7).
 *
 * <p>Keiner der vier Werte hat einen brauchbaren Default: {@code application.yml} reicht die
 * Umgebungsvariablen leer durch, und ein leerer Wert laesst {@code @NotBlank} scheitern — die
 * Anwendung startet dann gar nicht erst. Ein Vorgabezugang im Repository waere ein bekannter Zugang
 * und damit keiner (CLAUDE-security.md); {@code docker-compose.yml} Z. 69 ff. setzt die Werte fuer
 * den lokalen Stack.
 *
 * <p>Die Klasse liegt in {@code config} und nicht im Modul {@code vorgang}: Schalter, die kein
 * Fachmodul besitzt, stehen hier ({@link OperationsProperties}). Der Adapter im Modul {@code
 * vorgang} liest sie nur.
 *
 * @param endpoint Adresse des S3-Dienstes, etwa {@code http://minio:9000}
 * @param accessKey Kennung des Zugangs
 * @param secretKey Geheimnis des Zugangs
 * @param bucket Eimer, in dem die Anhaenge liegen
 */
@ConfigurationProperties(prefix = "fbcrm.minio")
@Validated
public record MinioProperties(
    @NotBlank String endpoint,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket) {}
