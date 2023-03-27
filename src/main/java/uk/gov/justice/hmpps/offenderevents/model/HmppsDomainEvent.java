package uk.gov.justice.hmpps.offenderevents.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class HmppsDomainEvent {
    private int version = 1;
    private String eventType;
    private String description;
    private String detailUrl;
    private String occurredAt;
    private String publishedAt;
    private PersonReference personReference;
    private Map<String, String> additionalInformation = new HashMap<>();

    private static int $default$version() {
        return 1;
    }

    private static Map<String, String> $default$additionalInformation() {
        return new HashMap<>();
    }

    public static HmppsDomainEventBuilder builder() {
        return new HmppsDomainEventBuilder();
    }

    public HmppsDomainEvent withAdditionalInformation(String key, String value) {
        if (value != null)  additionalInformation.put(key, value);
        return this;
    }
    public HmppsDomainEvent withAdditionalInformation(String key, Number value) {
        if (value != null)  additionalInformation.put(key, value.toString());
        return this;
    }

    public Map<String, String> asTelemetryMap() {
        final var elements = new HashMap<>(Map.of(
            "eventType", eventType,
            "occurredAt", occurredAt,
            "publishedAt", publishedAt,
            "nomsNumber", personReference.nomsNumber()
        ));
        elements.putAll(additionalInformation);
        return elements;
    }

    public Map<String, MessageAttributeValue> asMetadataMap() {
        final var attributes = new HashMap<>(Map.of(
            "eventType", MessageAttributeValue.builder().dataType("String").stringValue(eventType).build()
        ));
        if (additionalInformation.containsKey("caseNoteType")) attributes.put(
            "caseNoteType", MessageAttributeValue.builder().dataType("String").stringValue(additionalInformation.get("caseNoteType")).build()
        );
        return attributes;
    }

    public int getVersion() {
        return this.version;
    }

    public String getEventType() {
        return this.eventType;
    }

    public String getDescription() {
        return this.description;
    }

    public String getDetailUrl() {
        return this.detailUrl;
    }

    public String getOccurredAt() {
        return this.occurredAt;
    }

    public String getPublishedAt() {
        return this.publishedAt;
    }

    public PersonReference getPersonReference() {
        return this.personReference;
    }

    public Map<String, String> getAdditionalInformation() {
        return this.additionalInformation;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setPublishedAt(String publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void setPersonReference(PersonReference personReference) {
        this.personReference = personReference;
    }

    public void setAdditionalInformation(Map<String, String> additionalInformation) {
        this.additionalInformation = additionalInformation;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof HmppsDomainEvent)) return false;
        final HmppsDomainEvent other = (HmppsDomainEvent) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.getVersion() != other.getVersion()) return false;
        final Object this$eventType = this.getEventType();
        final Object other$eventType = other.getEventType();
        if (this$eventType == null ? other$eventType != null : !this$eventType.equals(other$eventType)) return false;
        final Object this$description = this.getDescription();
        final Object other$description = other.getDescription();
        if (this$description == null ? other$description != null : !this$description.equals(other$description))
            return false;
        final Object this$detailUrl = this.getDetailUrl();
        final Object other$detailUrl = other.getDetailUrl();
        if (this$detailUrl == null ? other$detailUrl != null : !this$detailUrl.equals(other$detailUrl)) return false;
        final Object this$occurredAt = this.getOccurredAt();
        final Object other$occurredAt = other.getOccurredAt();
        if (this$occurredAt == null ? other$occurredAt != null : !this$occurredAt.equals(other$occurredAt))
            return false;
        final Object this$publishedAt = this.getPublishedAt();
        final Object other$publishedAt = other.getPublishedAt();
        if (this$publishedAt == null ? other$publishedAt != null : !this$publishedAt.equals(other$publishedAt))
            return false;
        final Object this$personReference = this.getPersonReference();
        final Object other$personReference = other.getPersonReference();
        if (this$personReference == null ? other$personReference != null : !this$personReference.equals(other$personReference))
            return false;
        final Object this$additionalInformation = this.getAdditionalInformation();
        final Object other$additionalInformation = other.getAdditionalInformation();
        if (this$additionalInformation == null ? other$additionalInformation != null : !this$additionalInformation.equals(other$additionalInformation))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof HmppsDomainEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getVersion();
        final Object $eventType = this.getEventType();
        result = result * PRIME + ($eventType == null ? 43 : $eventType.hashCode());
        final Object $description = this.getDescription();
        result = result * PRIME + ($description == null ? 43 : $description.hashCode());
        final Object $detailUrl = this.getDetailUrl();
        result = result * PRIME + ($detailUrl == null ? 43 : $detailUrl.hashCode());
        final Object $occurredAt = this.getOccurredAt();
        result = result * PRIME + ($occurredAt == null ? 43 : $occurredAt.hashCode());
        final Object $publishedAt = this.getPublishedAt();
        result = result * PRIME + ($publishedAt == null ? 43 : $publishedAt.hashCode());
        final Object $personReference = this.getPersonReference();
        result = result * PRIME + ($personReference == null ? 43 : $personReference.hashCode());
        final Object $additionalInformation = this.getAdditionalInformation();
        result = result * PRIME + ($additionalInformation == null ? 43 : $additionalInformation.hashCode());
        return result;
    }

    public String toString() {
        return "HmppsDomainEvent(version=" + this.getVersion() + ", eventType=" + this.getEventType() + ", description=" + this.getDescription() + ", detailUrl=" + this.getDetailUrl() + ", occurredAt=" + this.getOccurredAt() + ", publishedAt=" + this.getPublishedAt() + ", personReference=" + this.getPersonReference() + ", additionalInformation=" + this.getAdditionalInformation() + ")";
    }

    public HmppsDomainEventBuilder toBuilder() {
        return new HmppsDomainEventBuilder().version(this.version).eventType(this.eventType).description(this.description).detailUrl(this.detailUrl).occurredAt(this.occurredAt).publishedAt(this.publishedAt).personReference(this.personReference).additionalInformation(this.additionalInformation);
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

    public static class HmppsDomainEventBuilder {
        private int version$value;
        private boolean version$set;
        private String eventType;
        private String description;
        private String detailUrl;
        private String occurredAt;
        private String publishedAt;
        private PersonReference personReference;
        private Map<String, String> additionalInformation$value;
        private boolean additionalInformation$set;

        HmppsDomainEventBuilder() {
        }

        public HmppsDomainEventBuilder version(int version) {
            this.version$value = version;
            this.version$set = true;
            return this;
        }

        public HmppsDomainEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public HmppsDomainEventBuilder description(String description) {
            this.description = description;
            return this;
        }

        public HmppsDomainEventBuilder detailUrl(String detailUrl) {
            this.detailUrl = detailUrl;
            return this;
        }

        public HmppsDomainEventBuilder occurredAt(String occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public HmppsDomainEventBuilder publishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
            return this;
        }

        public HmppsDomainEventBuilder personReference(PersonReference personReference) {
            this.personReference = personReference;
            return this;
        }

        public HmppsDomainEventBuilder additionalInformation(Map<String, String> additionalInformation) {
            this.additionalInformation$value = additionalInformation;
            this.additionalInformation$set = true;
            return this;
        }

        public HmppsDomainEvent build() {
            int version$value = this.version$value;
            if (!this.version$set) {
                version$value = HmppsDomainEvent.$default$version();
            }
            Map<String, String> additionalInformation$value = this.additionalInformation$value;
            if (!this.additionalInformation$set) {
                additionalInformation$value = HmppsDomainEvent.$default$additionalInformation();
            }
            return new HmppsDomainEvent(version$value, eventType, description, detailUrl, occurredAt, publishedAt, personReference, additionalInformation$value);
        }

        public String toString() {
            return "HmppsDomainEvent.HmppsDomainEventBuilder(version$value=" + this.version$value + ", eventType=" + this.eventType + ", description=" + this.description + ", detailUrl=" + this.detailUrl + ", occurredAt=" + this.occurredAt + ", publishedAt=" + this.publishedAt + ", personReference=" + this.personReference + ", additionalInformation$value=" + this.additionalInformation$value + ")";
        }
    }
}
