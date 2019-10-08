package uk.gov.justice.hmpps.offenderevents.controllers;

import io.swagger.annotations.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;
import uk.gov.justice.hmpps.offenderevents.services.SnsService;

import javax.validation.constraints.NotNull;

@Api(tags = {"offender-events"})
@RestController
@RequestMapping(
        value="offender-events",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
public class OffenderEventController {

    private final SnsService snsService;

    public OffenderEventController(final SnsService snsService) {
        this.snsService = snsService;
    }

    @ApiOperation(value = "Raise Event")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "OK", response = OffenderEvent.class) } )
    @GetMapping(path = "/raise/{eventType}/{nomsId}")
    public OffenderEvent raiseEvents(
            @ApiParam(name = "eventType", value = "Event Type", example = "NOMS_ID_CHANGED", required = true) @NotNull @PathVariable(value = "eventType") String eventType,
            @ApiParam(name = "nomsId", value = "NOMS ID of the offender", example = "A1234AA", required = true) @NotNull @PathVariable(value = "nomsId") String nomsId) {

        final var payload = OffenderEvent.builder().eventType(eventType).nomsId(nomsId).build();
        snsService.sendEvent(payload);
        return payload;
    }
}
