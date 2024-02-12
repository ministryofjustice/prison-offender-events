package uk.gov.justice.hmpps.offenderevents.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import uk.gov.justice.hmpps.kotlin.auth.dsl.ResourceServerConfigurationCustomizer

@Configuration
class ResourceServerConfiguration {
  @Bean
  fun resourceServerCustomizer() = ResourceServerConfigurationCustomizer.build {
    unauthorizedRequestPaths {
      addPaths = setOf("/queue-admin/retry-all-dlqs")
    }
  }
}
