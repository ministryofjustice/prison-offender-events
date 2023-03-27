package uk.gov.justice.hmpps.offenderevents.services

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.hmpps.offenderevents.services.MergeRecordDiscriminator.MergeOutcome
import java.util.Optional

internal class MergeRecordDiscriminatorTest {
  private val prisonApiService: PrisonApiService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val discriminator: MergeRecordDiscriminator = MergeRecordDiscriminator(prisonApiService, telemetryClient)

  @Test
  @DisplayName("when booking not found")
  fun whenBookingRecordNotFound() {
    whenever(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.empty())
    val outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID)
    assertThat(outcomes).isEmpty()
  }

  @Test
  @DisplayName("when no merge records have been found")
  fun whenNoMergeRecordsHaveBeenFound() {
    whenever(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.of("ORIGINAL-NO"))
    whenever(prisonApiService.getIdentifiersByBookingId(BOOKING_ID)).thenReturn(listOf())
    val outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID)
    assertThat(outcomes).isEmpty()
  }

  @Test
  @DisplayName("when 1 merge record has been found")
  fun when1MergeRecordHasBeenFound() {
    whenever(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.of("ORIGINAL-NO"))
    whenever(prisonApiService.getIdentifiersByBookingId(BOOKING_ID))
      .thenReturn(listOf(BookingIdentifier("MERGED-NO")))
    val outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID)
    assertThat(outcomes).isNotEmpty
    assertThat(outcomes).containsExactly(MergeOutcome("MERGED-NO", "ORIGINAL-NO"))
  }

  @Test
  @DisplayName("when 2 merge records have been found")
  fun when2MergeRecordsHaveBeenFound() {
    whenever(prisonApiService.getPrisonerNumberForBookingId(BOOKING_ID)).thenReturn(Optional.of("ORIGINAL-NO"))
    whenever(prisonApiService.getIdentifiersByBookingId(BOOKING_ID)).thenReturn(
      listOf(
        BookingIdentifier("MERGED-NO1"),
        BookingIdentifier("MERGED-NO2"),
      ),
    )
    val outcomes = discriminator.identifyMergedPrisoner(BOOKING_ID)
    assertThat(outcomes).isNotEmpty
    assertThat(outcomes).containsExactly(
      MergeOutcome("MERGED-NO1", "ORIGINAL-NO"),
      MergeOutcome("MERGED-NO2", "ORIGINAL-NO"),
    )
  }

  companion object {
    const val BOOKING_ID = 22223333L
  }
}
