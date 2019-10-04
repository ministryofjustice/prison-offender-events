package uk.gov.justice.hmpps.offenderevents.controllers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;


@RestControllerAdvice(
        basePackages = {"uk.gov.justice.hmpps.offenderevents.controllers"}
)
@Slf4j
public class ControllerAdvice {

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<byte[]> handleRestClientResponseException(final RestClientResponseException e) {
        log.error("Unexpected exception", e);
        return ResponseEntity
                .status(e.getRawStatusCode())
                .body(e.getResponseBodyAsByteArray());
    }


}
