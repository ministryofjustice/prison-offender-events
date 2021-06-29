package uk.gov.justice.hmpps.offenderevents.services;

public interface PrisonerMovementReason {
    String details();

    CurrentLocation currentLocation();

    CurrentPrisonStatus currentPrisonStatus();

    String prisonId();
}
