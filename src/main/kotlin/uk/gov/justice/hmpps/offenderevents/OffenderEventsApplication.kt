package uk.gov.justice.hmpps.offenderevents

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class OffenderEventsApplication

fun main(args: Array<String>) {
  runApplication<OffenderEventsApplication>(*args)
}
