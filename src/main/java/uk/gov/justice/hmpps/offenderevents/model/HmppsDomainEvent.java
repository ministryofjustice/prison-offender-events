package uk.gov.justice.hmpps.offenderevents.model;

import com.amazonaws.services.sns.model.MessageAttributeValue;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
public class HmppsDomainEvent {
    @Builder.Default
    private int version = 1;
    private String eventType;
    private String description;
    private String detailUrl;
    private String occurredAt;
    private String publishedAt;
    private PersonReference personReference;
    @Builder.Default
    private Map<String, String> additionalInformation = new HashMap<>();

    public HmppsDomainEvent withAdditionalInformation(String key, String value) {
        if (value != null) {
            additionalInformation.put(key, value);
        }
        return this;
    }

    public Map<String, String> asTelemetryMap() {
        final var elements = new HashMap<>(Map.of(
            "eventType", eventType,
            "occurredAt", occurredAt,
            "nomsNumber", personReference.nomsNumber()
        ));
        elements.putAll(additionalInformation);
        return elements;
    }

    public Map<String, MessageAttributeValue> asMetadataMap() {
        final var attributes = new HashMap<>(Map.of(
            "eventType", new MessageAttributeValue().withDataType("String").withStringValue(eventType)
        ));
        if (additionalInformation.containsKey("caseNoteType")) attributes.put(
            "caseNoteType", new MessageAttributeValue().withDataType("String").withStringValue(additionalInformation.get("caseNoteType"))
        );
        return attributes;
    }

    public record PersonIdentifier(String type, String value) {}

    public record PersonReference(List<PersonIdentifier> identifiers) {
        public PersonReference(String nomsNumber) {
            this(List.of(new PersonIdentifier("NOMS", nomsNumber)));
        }

        public String nomsNumber() {
            return identifiers.stream()
                .filter(identifier -> "NOMS".equals(identifier.type())).findFirst()
                .map(PersonIdentifier::value).orElseThrow();
        }
    }
}
