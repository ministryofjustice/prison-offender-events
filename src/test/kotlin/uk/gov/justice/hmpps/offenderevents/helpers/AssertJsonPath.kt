package uk.gov.justice.hmpps.offenderevents.helpers

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.TemporalUnitOffset
import org.springframework.test.util.JsonPathExpectationsHelper
import java.time.OffsetDateTime

fun String.assertJsonPath(path: String, expectedValue: Any) = JsonPathExpectationsHelper(path).assertValue(this, expectedValue)

fun String.assertJsonPathDateTimeIsCloseTo(path: String, other: OffsetDateTime, offset: TemporalUnitOffset) {
  val value = JsonPathExpectationsHelper(path).evaluateJsonPath(this)
  assertThat(value).isNotNull
  assertThat(OffsetDateTime.parse(value!!.toString()))
    .isCloseTo(other, offset)
}
