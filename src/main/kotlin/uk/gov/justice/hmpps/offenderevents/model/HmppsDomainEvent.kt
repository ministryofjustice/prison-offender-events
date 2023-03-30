package uk.gov.justice.hmpps.offenderevents.model

import com.fasterxml.jackson.annotation.JsonInclude
import software.amazon.awssdk.services.sns.model.MessageAttributeValue

@JsonInclude(JsonInclude.Include.NON_NULL)
data class HmppsDomainEvent(
  val version: Int = 1,
  val eventType: String,
  val description: String,
  val detailUrl: String? = null,
  val occurredAt: String,
  val publishedAt: String,
  val personReference: PersonReference,

  val additionalInformation: MutableMap<String, String> = mutableMapOf(),
) {
  fun withAdditionalInformation(key: String, value: String?): HmppsDomainEvent {
    if (value != null) additionalInformation[key] = value
    return this
  }

  fun withAdditionalInformation(key: String, value: Number?): HmppsDomainEvent {
    if (value != null) additionalInformation[key] = value.toString()
    return this
  }

  fun asTelemetryMap(): Map<String, String> {
    val elements = mutableMapOf(
      "eventType" to eventType,
      "occurredAt" to occurredAt,
      "publishedAt" to publishedAt,
      "nomsNumber" to personReference.nomsNumber(),
    )
    elements.putAll(additionalInformation)
    return elements.toMap()
  }

  fun asMetadataMap(): Map<String, MessageAttributeValue> {
    val attributes = mutableMapOf(
      "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
    )
    if (additionalInformation.containsKey("caseNoteType")) {
      attributes["caseNoteType"] =
        MessageAttributeValue.builder().dataType("String").stringValue(additionalInformation["caseNoteType"]).build()
    }
    return attributes
  }

  data class PersonIdentifier(val type: String, val value: String)

  class PersonReference(nomsNumber: String) {
    @Suppress("MemberVisibilityCanBePrivate")
    val identifiers: List<PersonIdentifier> = listOf(PersonIdentifier("NOMS", nomsNumber))

    fun nomsNumber(): String =
      identifiers.find { it.type == "NOMS" }?.value ?: throw RuntimeException("No NOMS identifier")
  }
}
