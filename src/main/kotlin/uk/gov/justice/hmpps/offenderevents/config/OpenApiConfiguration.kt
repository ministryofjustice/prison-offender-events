package uk.gov.justice.hmpps.offenderevents.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties) {
  private val version: String = buildProperties.version

  @Bean
  fun customOpenAPI(buildProperties: BuildProperties): OpenAPI? = OpenAPI()
    .servers(
      listOf(
        Server().url("https://offender-events.hmpps.service.justice.gov.uk").description("Prod"),
        Server().url("https://offender-events-preprod.hmpps.service.justice.gov.uk").description("PreProd"),
        Server().url("https://offender-events-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("http://localhost:8080").description("Local"),
      )
    )
    .info(
      Info().title("Prison Offender Events").version(version).description(
        javaClass.getResource("/swagger-description.html").readText()
      ).contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk"))
    )
}
