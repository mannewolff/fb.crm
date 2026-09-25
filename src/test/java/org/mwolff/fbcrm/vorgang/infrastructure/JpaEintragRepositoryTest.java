package org.mwolff.fbcrm.vorgang.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.vorgang.domain.Eintrag;
import org.mwolff.fbcrm.vorgang.domain.Eintragsart;
import org.mwolff.fbcrm.vorgang.domain.Herkunft;

/**
 * Die Uebersetzung zwischen Eintrag und Zeile — in beide Richtungen und fuer beide Arten.
 *
 * <p>Der Adapter steht hier gegen ein gemocktes Spring-Data-Repository, damit die Abbildung selbst
 * geprueft ist und nicht nur ihr Zusammenspiel mit der Datenbank ({@code JpaEintragRepositoryIT}).
 */
@ExtendWith(MockitoExtension.class)
class JpaEintragRepositoryTest {

  private static final Instant GESCHEHEN = Instant.parse("2026-09-10T09:30:00Z");
  private static final Instant ANGELEGT = Instant.parse("2026-09-11T08:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-09-18T12:00:00Z");

  @Mock private SpringDataEintragRepository jpa;

  @Captor private ArgumentCaptor<VorgangEintragEntity> gespeicherte;

  @InjectMocks private JpaEintragRepository repository;

  private static VorgangEintragEntity kommentarZeile(final Long id) {
    return new VorgangEintragEntity(
        id,
        4L,
        Eintragsart.KOMMENTAR,
        "Angerufen",
        GESCHEHEN,
        Herkunft.VON_HAND,
        null,
        null,
        null,
        ANGELEGT,
        GEAENDERT);
  }

  private static Eintrag kommentar(final Long id) {
    return new Eintrag(
        id,
        4L,
        Eintragsart.KOMMENTAR,
        "Angerufen",
        GESCHEHEN,
        Herkunft.VON_HAND,
        null,
        null,
        null,
        ANGELEGT,
        GEAENDERT);
  }

  private static VorgangEintragEntity anhangZeile(final Long id) {
    return new VorgangEintragEntity(
        id,
        4L,
        Eintragsart.ANHANG,
        "Das Angebot",
        GESCHEHEN,
        Herkunft.VON_HAND,
        "Angebot.pdf",
        4096L,
        "vorgang/4/abc",
        ANGELEGT,
        null);
  }

  private static Eintrag anhang(final Long id) {
    return new Eintrag(
        id,
        4L,
        Eintragsart.ANHANG,
        "Das Angebot",
        GESCHEHEN,
        Herkunft.VON_HAND,
        "Angebot.pdf",
        4096L,
        "vorgang/4/abc",
        ANGELEGT,
        null);
  }

  @Test
  void save_givenAKommentar_thenWritesEveryFieldIntoTheRow() {
    // Given
    when(jpa.save(any(VorgangEintragEntity.class))).thenReturn(kommentarZeile(21L));

    // When
    repository.save(kommentar(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getId()).isNull(),
            zeile -> assertThat(zeile.getVorgangId()).isEqualTo(4L),
            zeile -> assertThat(zeile.getArt()).isEqualTo(Eintragsart.KOMMENTAR),
            zeile -> assertThat(zeile.getText()).isEqualTo("Angerufen"),
            zeile -> assertThat(zeile.getGeschehenAm()).isEqualTo(GESCHEHEN),
            zeile -> assertThat(zeile.getHerkunft()).isEqualTo(Herkunft.VON_HAND),
            zeile -> assertThat(zeile.getDateiName()).isNull(),
            zeile -> assertThat(zeile.getDateiGroesse()).isNull(),
            zeile -> assertThat(zeile.getObjektSchluessel()).isNull(),
            zeile -> assertThat(zeile.getCreatedAt()).isEqualTo(ANGELEGT),
            zeile -> assertThat(zeile.getGeaendertAm()).isEqualTo(GEAENDERT));
  }

  @Test
  void save_givenAnAnhang_thenWritesTheThreeFileValuesIntoTheRow() {
    // Given
    when(jpa.save(any(VorgangEintragEntity.class))).thenReturn(anhangZeile(21L));

    // When
    repository.save(anhang(null));

    // Then
    verify(jpa).save(gespeicherte.capture());
    assertThat(gespeicherte.getValue())
        .satisfies(
            zeile -> assertThat(zeile.getArt()).isEqualTo(Eintragsart.ANHANG),
            zeile -> assertThat(zeile.getText()).isEqualTo("Das Angebot"),
            zeile -> assertThat(zeile.getDateiName()).isEqualTo("Angebot.pdf"),
            zeile -> assertThat(zeile.getDateiGroesse()).isEqualTo(4096L),
            zeile -> assertThat(zeile.getObjektSchluessel()).isEqualTo("vorgang/4/abc"),
            zeile -> assertThat(zeile.getGeaendertAm()).isNull());
  }

  @Test
  void save_thenReturnsTheEintragWithTheGeneratedId() {
    // Given
    when(jpa.save(any(VorgangEintragEntity.class))).thenReturn(kommentarZeile(21L));

    // When
    final Eintrag gesichert = repository.save(kommentar(null));

    // Then
    assertThat(gesichert).isEqualTo(kommentar(21L));
  }

  @Test
  void findById_givenAKnownKommentar_thenTranslatesTheRow() {
    // Given
    when(jpa.findById(21L)).thenReturn(Optional.of(kommentarZeile(21L)));

    // When
    final Optional<Eintrag> gefunden = repository.findById(21L);

    // Then
    assertThat(gefunden).contains(kommentar(21L));
  }

  @Test
  void findById_givenAKnownAnhang_thenTranslatesTheFileValues() {
    // Given
    when(jpa.findById(21L)).thenReturn(Optional.of(anhangZeile(21L)));

    // When
    final Optional<Eintrag> gefunden = repository.findById(21L);

    // Then
    assertThat(gefunden).contains(anhang(21L));
  }

  @Test
  void findById_givenAnUnknownId_thenEmpty() {
    // Given
    when(jpa.findById(21L)).thenReturn(Optional.empty());

    // When
    final Optional<Eintrag> gefunden = repository.findById(21L);

    // Then
    assertThat(gefunden).isEmpty();
  }

  @Test
  void findByVorgang_thenTranslatesEveryRowOfTheHistory() {
    // Given
    when(jpa.findByVorgang(4L)).thenReturn(List.of(anhangZeile(22L), kommentarZeile(21L)));

    // When
    final List<Eintrag> historie = repository.findByVorgang(4L);

    // Then
    assertThat(historie).containsExactly(anhang(22L), kommentar(21L));
  }

  @Test
  void findByVorgang_givenAVorgangWithoutEntries_thenEmptyList() {
    // Given
    when(jpa.findByVorgang(4L)).thenReturn(List.of());

    // When
    final List<Eintrag> historie = repository.findByVorgang(4L);

    // Then
    assertThat(historie).isEmpty();
  }

  private static SpringDataEintragRepository.JuengstesGeschehen zeile(
      final long vorgangId, final Instant geschehenAm) {
    final SpringDataEintragRepository.JuengstesGeschehen zeile =
        mock(SpringDataEintragRepository.JuengstesGeschehen.class);
    when(zeile.getVorgangId()).thenReturn(vorgangId);
    when(zeile.getGeschehenAm()).thenReturn(geschehenAm);
    return zeile;
  }

  @Test
  void juengstesGeschehenJeVorgang_thenTranslatesEveryRowIntoTheMap() {
    // Given
    final List<SpringDataEintragRepository.JuengstesGeschehen> zeilen =
        List.of(zeile(4L, GESCHEHEN), zeile(5L, ANGELEGT));
    when(jpa.juengstesGeschehenJeVorgang(List.of(4L, 5L))).thenReturn(zeilen);

    // When
    final Map<Long, Instant> juengste = repository.juengstesGeschehenJeVorgang(List.of(4L, 5L));

    // Then
    assertThat(juengste).containsExactly(entry(4L, GESCHEHEN), entry(5L, ANGELEGT));
  }

  @Test
  void juengstesGeschehenJeVorgang_givenAVorgangWithoutEntries_thenLeavesItOut() {
    // Given — ohne Eintrag gibt es keinen Zeitpunkt; einen Ersatzwert kennt der Adapter nicht.
    final List<SpringDataEintragRepository.JuengstesGeschehen> zeilen =
        List.of(zeile(4L, GESCHEHEN));
    when(jpa.juengstesGeschehenJeVorgang(List.of(4L, 5L))).thenReturn(zeilen);

    // When
    final Map<Long, Instant> juengste = repository.juengstesGeschehenJeVorgang(List.of(4L, 5L));

    // Then
    assertThat(juengste).containsOnlyKeys(4L);
  }

  @Test
  void juengstesGeschehenJeVorgang_givenNoVorgaenge_thenEmptyMap() {
    // Given
    when(jpa.juengstesGeschehenJeVorgang(List.of())).thenReturn(List.of());

    // When
    final Map<Long, Instant> juengste = repository.juengstesGeschehenJeVorgang(List.of());

    // Then
    assertThat(juengste).isEmpty();
  }
}
