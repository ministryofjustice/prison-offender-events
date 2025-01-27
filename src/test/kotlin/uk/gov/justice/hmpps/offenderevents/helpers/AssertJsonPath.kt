package uk.gov.justice.hmpps.offenderevents.helpers

import net.minidev.json.JSONArray
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ObjectArrayAssert
import org.assertj.core.api.ObjectAssert
import org.assertj.core.data.TemporalUnitOffset
import org.springframework.test.util.JsonPathExpectationsHelper
import java.time.OffsetDateTime

fun String?.assertJsonPath(path: String, expectedValue: Any) {
  assertThat(this).isNotNull
  JsonPathExpectationsHelper(path).assertValue(this!!, expectedValue)
}

fun String?.assertJsonPath(path: String): ObjectAssert<Any> = assertThat(JsonPathExpectationsHelper(path).evaluateJsonPath(this!!))

fun String?.assertJsonPathIsArray(path: String): ObjectArrayAssert<Any> {
  JsonPathExpectationsHelper(path).assertValueIsArray(this!!)
  val value = JsonPathExpectationsHelper(path).evaluateJsonPath(this) as JSONArray
  return assertThat(value.toArray())
}

fun String?.assertJsonPathDateTimeIsCloseTo(path: String, other: OffsetDateTime, offset: TemporalUnitOffset) {
  assertThat(this).isNotNull
  val value = JsonPathExpectationsHelper(path).evaluateJsonPath(this!!)
  assertThat(value).isNotNull
  assertThat(OffsetDateTime.parse(value!!.toString()))
    .isCloseTo(other, offset)
}
