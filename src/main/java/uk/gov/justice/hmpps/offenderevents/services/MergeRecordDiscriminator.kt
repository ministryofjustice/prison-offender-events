package uk.gov.justice.hmpps.offenderevents.services

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class MergeRecordDiscriminator(
  private val prisonApiService: PrisonApiService,
  private val telemetryClient: TelemetryClient,
) {
  fun identifyMergedPrisoner(bookingId: Long): List<MergeOutcome> =
    prisonApiService.getPrisonerNumberForBookingId(bookingId)
      .map { prisonerNumber: String ->
        log.debug(
          "Check for merges for booking ID {}, prisoner Number {}",
          bookingId,
          prisonerNumber,
        )
        val mergedOffenders = prisonApiService.getIdentifiersByBookingId(bookingId)
        mergedOffenders.forEach { mergedNumber: BookingIdentifier ->
          val trackingAttributes = mapOf(
            "bookingId" to bookingId.toString(),
            "mergedFrom" to mergedNumber.value,
            "mergedTo" to prisonerNumber,
          )
          telemetryClient.trackEvent("POEMergeEvent", trackingAttributes, null)
          log.debug("Prisoner record merged {} --> {}", mergedNumber.value, prisonerNumber)
        }
        mergedOffenders
          .map { mergedNumber: BookingIdentifier -> MergeOutcome(mergedNumber.value, prisonerNumber) }
      }
      .orElse(emptyList())

  @JvmRecord
  data class MergeOutcome(val mergedNumber: String, val remainingNumber: String)

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
