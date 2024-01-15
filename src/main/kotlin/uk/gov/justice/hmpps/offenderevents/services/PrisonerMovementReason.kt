package uk.gov.justice.hmpps.offenderevents.services

interface PrisonerMovementReason {
  val details: String?
  val currentLocation: CurrentLocation?
  val currentPrisonStatus: CurrentPrisonStatus?
  val prisonId: String
}

enum class CurrentLocation {
  IN_PRISON,
  OUTSIDE_PRISON,
  BEING_TRANSFERRED,
}

enum class CurrentPrisonStatus {
  UNDER_PRISON_CARE,
  NOT_UNDER_PRISON_CARE,
}
