package org.mwolff.fbcrm.rechnung.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mwolff.fbcrm.angebot.domain.Angebot;
import org.mwolff.fbcrm.angebot.domain.AngebotRepository;
import org.mwolff.fbcrm.eigeneangaben.domain.EigeneAngabenRepository;
import org.mwolff.fbcrm.firma.domain.FirmaRepository;
import org.mwolff.fbcrm.rechnung.domain.DokumentSpeicher;
import org.mwolff.fbcrm.rechnung.domain.Nummernkreis;
import org.mwolff.fbcrm.rechnung.domain.Nummernmuster;
import org.mwolff.fbcrm.rechnung.domain.Rechnung;
import org.mwolff.fbcrm.rechnung.domain.RechnungRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungseinstellungen;
import org.mwolff.fbcrm.rechnung.domain.RechnungseinstellungenRepository;
import org.mwolff.fbcrm.rechnung.domain.Rechnungszustand;

/**
 * Die Grenze der Abrechenbarkeit am stornierten Angebot (Plan #332, E8).
 *
 * <p>Die Regel „am stornierten Angebot nur die nicht abgerechneten Stunden" greift am Anlegen und
 * an der Taste — <b>nicht</b> am Aendern und nicht am Stellen. Ein Entwurf, der vor dem Storno
 * angelegt wurde, bleibt, wie er ist: Er laesst sich weiter aendern und stellen, auch wenn er mehr
 * abrechnet, als am stornierten Angebot noch abrechenbar waere. Ihn zu loeschen ist die Sache des
 * Anwenders. Das Angebot ist ein Merkzettel und kein Vertrag (#160, Frage 4).
 *
 * <p>Gegenstand ist das Angebot aus dem Szenario „Rest eines Auftrags wird nicht abgerufen": 8
 * Stunden angeboten, aus „bestellt" storniert. Vor dem Storno entstand ein Entwurf ueber alle 8
 * Stunden — abrechenbar waeren jetzt hoechstens die erfassten.
 */
@ExtendWith(MockitoExtension.class)
class EntwurfVorDemStornoTest {

  private static final long ENTWURF = 2L;
  private static final Instant JETZT = Instant.parse("2026-10-05T09:00:00Z");
  private static final byte[] PDF = "%PDF-1.7 Rechnung".getBytes(StandardCharsets.UTF_8);

  private final Clock uhr = Clock.fixed(JETZT, ZoneOffset.UTC);

  @Mock private RechnungRepository rechnungen;
  @Mock private AngebotRepository angebote;
  @Mock private FirmaRepository firmen;
  @Mock private EigeneAngabenRepository eigeneAngaben;
  @Mock private RechnungseinstellungenRepository einstellungen;
  @Mock private Nummernkreis nummernkreis;
  @Mock private Rechnungsnummern rechnungsnummern;
  @Mock private Belegdrucker drucker;
  @Mock private DokumentSpeicher dokumente;

  private static Angebot storniertesAngebot() {
    return Rechnungsdoppel.storniert(List.of(Rechnungsdoppel.beratungUeber("8.00")));
  }

  private static Rechnung entwurfUeberAlleAchtStunden() {
    return Rechnungsdoppel.entwurf(ENTWURF, List.of(Rechnungsdoppel.beratung("8.00")));
  }

  @Test
  void aendere_aEntwurfFromBeforeTheStorno_thenItKeepsMoreThanIsAbrechenbar() {
    // Given
    when(rechnungen.findById(ENTWURF)).thenReturn(Optional.of(entwurfUeberAlleAchtStunden()));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.of(storniertesAngebot()));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));

    // When — der Entwurf bleibt bei 8 Stunden, obwohl keine einzige erfasst ist.
    final Rechnung geaendert =
        new RechnungAendernUseCase(rechnungen, angebote, uhr)
            .aendere(
                ENTWURF,
                new RechnungDaten(
                    LocalDate.of(2026, 10, 5),
                    "Oktober 2026",
                    List.of(
                        new Abrechnungsangabe(
                            Rechnungsdoppel.BERATUNG_ID, "Beratung", new BigDecimal("8.00")))));

    // Then
    assertThat(geaendert.positionen())
        .singleElement()
        .satisfies(position -> assertThat(position.menge()).isEqualByComparingTo("8.00"));
  }

  @Test
  void stelle_aEntwurfFromBeforeTheStorno_thenItIsGestelltWithMoreThanIsAbrechenbar() {
    // Given
    when(rechnungen.findByIdMitSperre(ENTWURF))
        .thenReturn(Optional.of(entwurfUeberAlleAchtStunden()));
    when(angebote.findById(Rechnungsdoppel.ANGEBOT)).thenReturn(Optional.of(storniertesAngebot()));
    when(firmen.findById(Rechnungsdoppel.FIRMA)).thenReturn(Optional.of(Rechnungsdoppel.firma()));
    when(eigeneAngaben.lies()).thenReturn(Rechnungsdoppel.eigeneAngaben());
    when(einstellungen.lies())
        .thenReturn(
            new Rechnungseinstellungen(
                new Nummernmuster("R{JJ}-{NNNN}"), new BigDecimal("19.00"), 10));
    when(nummernkreis.ziehe(anyInt())).thenReturn(1);
    when(rechnungen.saveAndFlush(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(rechnungen.save(any())).thenAnswer(aufruf -> aufruf.getArgument(0));
    when(drucker.drucke(any())).thenReturn(PDF);
    when(dokumente.lege(ENTWURF, PDF)).thenReturn("rechnungen/2.pdf");

    // When
    final Rechnung gestellt =
        new RechnungStellenUseCase(
                rechnungen,
                angebote,
                firmen,
                eigeneAngaben,
                einstellungen,
                nummernkreis,
                rechnungsnummern,
                drucker,
                dokumente,
                uhr)
            .stelle(ENTWURF);

    // Then — gestellt mit allen 8 Stunden; das stornierte Angebot bleibt unberuehrt.
    assertThat(gestellt.zustand()).isEqualTo(Rechnungszustand.GESTELLT);
    assertThat(gestellt.positionen())
        .singleElement()
        .satisfies(position -> assertThat(position.menge()).isEqualByComparingTo("8.00"));
    verify(angebote, never()).save(any());
  }
}
