package uk.gov.justice.hmpps.offenderevents.services;

import com.microsoft.applicationinsights.TelemetryClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.hmpps.offenderevents.services.MergeRecordDiscriminator.MergeOutcome;
import uk.gov.justice.hmpps.offenderevents.services.ReleasePrisonerReasonCalculator.Reason;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MergeRecordDiscriminatorTest {
    public static final long BOOKING_ID = 22223333L;
    @Mock
    private PrisonApiService prisonApiService;

    @Mock
    private TelemetryClient telemetryClient;

    private MergeRecordDiscriminator discriminator;

    @BeforeEach
    void setUp() {
        discriminator = new MergeRecordDiscriminator(prisonApiService, telemetryClient);
    }

    @Test
    @DisplayName("when booking not found")
    void whenBookingRecordNotFound() {
        when(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.empty());

        final var outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID);

        assertThat(outcomes).isEmpty();
    }

    @Test
    @DisplayName("when no merge records have been found")
    void whenNoMergeRecordsHaveBeenFound() {
        when(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.of("ORIGINAL-NO"));
        when(prisonApiService.getIdentifiersByBookingId(BOOKING_ID)).thenReturn(List.of());

        final var outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID);

        assertThat(outcomes).isEmpty();
    }

    @Test
    @DisplayName("when 1 merge record has been found")
    void when1MergeRecordHasBeenFound() {
        when(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.of("ORIGINAL-NO"));
        when(prisonApiService.getIdentifiersByBookingId(BOOKING_ID)).thenReturn(List.of(new BookingIdentifier("MERGED-NO")));

        final var outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID);

        assertThat(outcomes).isNotEmpty();
        assertThat(outcomes).containsExactly(new MergeOutcome("MERGED-NO", "ORIGINAL-NO"));
    }


    @Test
    @DisplayName("when 2 merge records have been found")
    void when2MergeRecordsHaveBeenFound() {
        when(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.of("ORIGINAL-NO"));
        when(prisonApiService.getIdentifiersByBookingId(BOOKING_ID)).thenReturn(
            List.of(
                new BookingIdentifier("MERGED-NO1"),
                new BookingIdentifier("MERGED-NO2")
            )
        );

        final var outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID);

        assertThat(outcomes).isNotEmpty();
        assertThat(outcomes).containsExactly(
            new MergeOutcome("MERGED-NO1", "ORIGINAL-NO"),
            new MergeOutcome("MERGED-NO2", "ORIGINAL-NO")
        );
    }
}
