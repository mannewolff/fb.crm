package org.mwolff.fbcrm.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.mail.MailProperties;
import org.mwolff.fbcrm.mail.application.EnqueueMailUseCase;

/**
 * Die Begruessungsmail an ein neu angelegtes Konto (Plan #414, A13; fachliche Quelle #409,
 * Kriterium 4).
 *
 * <p>Zwei Lagen: Mit eingeschaltetem Versand entsteht ein Zustellauftrag, der Name und Adresse der
 * Instanz nennt, und der Anwendungsfall meldet {@code true}. Mit ausgeschaltetem entsteht keiner,
 * und er meldet {@code false} — die Oberflaeche sagt dann, dass keine Mail hinausging.
 */
@ExtendWith(MockitoExtension.class)
class BegruessungsmailUseCaseTest {

  private static final String MAIL = "anna@firma.de";
  private static final String NAME = "Anna Berg";
  private static final String INSTANZ = "https://crm.example.org";

  @Mock private EnqueueMailUseCase postausgang;
  @Captor private ArgumentCaptor<String> text;

  private BegruessungsmailUseCase mitVersand() {
    return new BegruessungsmailUseCase(
        postausgang, new MailProperties(true, "fbcrm@example.org", INSTANZ));
  }

  @Test
  void sende_givenMailEnabled_thenEnqueuesTheGreetingAndReportsIt() {
    // When
    final boolean versandt = mitVersand().sende(MAIL, NAME);

    // Then
    assertThat(versandt).isTrue();
    verify(postausgang).enqueue(eq(MAIL), eq(BegruessungsmailUseCase.BETREFF), text.capture());
    assertThat(text.getValue())
        .startsWith("Hallo " + NAME + ",")
        .contains("\n" + INSTANZ + "\n")
        .contains("E-Mail-Adresse");
  }

  @Test
  void sende_givenMailEnabled_thenTheSubjectNamesTheInstanceProduct() {
    // Then — der Betreff sagt, worum es geht, ohne den Namen der Person.
    assertThat(BegruessungsmailUseCase.BETREFF).startsWith("fb.crm: ").doesNotContain(NAME);
  }

  @Test
  void sende_givenANameWithAPlaceholder_thenTheNameStaysAsTyped() {
    // When — der Name kommt vom Menschen und wird zuletzt eingesetzt.
    mitVersand().sende(MAIL, "{instanz}");

    // Then
    verify(postausgang).enqueue(anyString(), anyString(), text.capture());
    assertThat(text.getValue()).startsWith("Hallo {instanz},");
  }

  @Test
  void sende_givenMailDisabled_thenEnqueuesNothingAndReportsIt() {
    // Given
    final BegruessungsmailUseCase ohneVersand =
        new BegruessungsmailUseCase(
            postausgang, new MailProperties(false, "fbcrm@example.org", INSTANZ));

    // When
    final boolean versandt = ohneVersand.sende(MAIL, NAME);

    // Then
    assertThat(versandt).isFalse();
    verifyNoInteractions(postausgang);
  }
}
