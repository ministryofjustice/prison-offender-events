package uk.gov.justice.hmpps.offenderevents.model

import com.fasterxml.jackson.annotation.JsonInclude
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
  fun withAdditionalInformation(key: String, value: Boolean?): HmppsDomainEvent {
    additionalInformation[key] = (value ?: false).toString()
    return this
  }

  fun withAdditionalInformation(key: String, value: LocalDate?): HmppsDomainEvent {
    if (value != null) additionalInformation[key] = value.format(DateTimeFormatter.ISO_DATE)
    return this
  }

  fun withAdditionalInformation(key: String, value: LocalDateTime?): HmppsDomainEvent {
    if (value != null) additionalInformation[key] = value.format(DateTimeFormatter.ISO_DATE_TIME)
    return this
  }

  fun asTelemetryMap(): Map<String, String> {
    val elements = mutableMapOf(
      "eventType" to eventType,
      "occurredAt" to occurredAt,
      "publishedAt" to publishedAt,
    )
    personReference.nomsNumber()?.also { elements["nomsNumber"] = it }
    personReference.personNumber()?.also { elements["personId"] = it }
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

  enum class Identifier { NOMS, PERSON }
  data class PersonIdentifier(val type: Identifier, val value: String)

  class PersonReference(personIdentifier: PersonIdentifier) {

    constructor(nomsNumber: String) : this(PersonIdentifier(Identifier.NOMS, nomsNumber))

    @Suppress("MemberVisibilityCanBePrivate")
    val identifiers: List<PersonIdentifier> = listOf(personIdentifier)

    fun nomsNumber(): String? = identifiers.find { it.type == Identifier.NOMS }?.value
    fun personNumber(): String? = identifiers.find { it.type == Identifier.PERSON }?.value
  }
}
