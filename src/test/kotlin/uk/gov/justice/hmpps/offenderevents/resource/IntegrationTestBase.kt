package uk.gov.justice.hmpps.offenderevents.resource

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.hmpps.offenderevents.helper.JwtAuthHelper

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthHelper

  @Suppress("SpringJavaInjectionPointsAutowiringInspection")
  @Autowired
  lateinit var webTestClient: WebTestClient

  internal fun setAuthorisation(
    user: String = "hmpps-audit-client",
    roles: List<String> = listOf()
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisation(user, roles)
}
