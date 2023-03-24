package uk.gov.justice.hmpps.offenderevents.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = { "eventId", "eventType", "eventDatetime" })
@ToString(of = { "eventType", "bookingId" })
public class OffenderEvent {
    private String eventId;
    private String eventType;
    private LocalDateTime eventDatetime;

    private Long rootOffenderId;
    private Long offenderId;
    private Long aliasOffenderId;
    private Long previousOffenderId;
    private String offenderIdDisplay;

    private Long bookingId;
    private String bookingNumber;
    private String previousBookingNumber;

    private Long sanctionSeq;
    private Long movementSeq;
    private Long imprisonmentStatusSeq;
    private Long assessmentSeq;

    private Long alertSeq;
    private LocalDateTime alertDateTime;
    private String alertType;
    private String alertCode;
    private LocalDateTime expiryDateTime;

    private Long caseNoteId;
    private String agencyLocationId;
    private Long riskPredictorId;
    private Long addressId;
    private Long personId;
    private Long sentenceCalculationId;
    private Long oicHearingId;
    private Long oicOffenceId;
    private String pleaFindingCode;
    private String findingCode;

    private Long resultSeq;
    private Long agencyIncidentId;
    private Long chargeSeq;

    private String identifierType;
    private String identifierValue;

    private Long ownerId;
    private String ownerClass;

    private Long sentenceSeq;
    private String conditionCode;
    private Long offenderSentenceConditionId;

    private LocalDate addressEndDate;
    private String primaryAddressFlag;
    private String mailAddressFlag;
    private String addressUsage;

    // incident event data
    private Long incidentCaseId;
    private Long incidentPartySeq;
    private Long incidentRequirementSeq;
    private Long incidentQuestionSeq;
    private Long incidentResponseSeq;

    // bed assignment data
    private Integer bedAssignmentSeq;
    private Long livingUnitId;

    // external movement event data
    private LocalDateTime movementDateTime;
    private String movementType;
    private String movementReasonCode;
    private String directionCode;
    private String escortCode;
    private String fromAgencyLocationId;
    private String toAgencyLocationId;

    private String nomisEventType;

    private String caseNoteType;
    private String caseNoteSubType;

    @Getter(onMethod_ = @JsonAnyGetter)
    private Map<String, Object> additionalFields = new HashMap<>();

    public static OffenderEventBuilder builder() {
        return new OffenderEventBuilder();
    }

    @JsonAnySetter
    private void addAdditionalField(final String key, final Object value) {
        additionalFields.put(key, value);
    }

    public String getEventId() {
        return this.eventId;
    }

    public String getEventType() {
        return this.eventType;
    }

    public LocalDateTime getEventDatetime() {
        return this.eventDatetime;
    }

    public Long getRootOffenderId() {
        return this.rootOffenderId;
    }

    public Long getOffenderId() {
        return this.offenderId;
    }

    public Long getAliasOffenderId() {
        return this.aliasOffenderId;
    }

    public Long getPreviousOffenderId() {
        return this.previousOffenderId;
    }

    public String getOffenderIdDisplay() {
        return this.offenderIdDisplay;
    }

    public Long getBookingId() {
        return this.bookingId;
    }

    public String getBookingNumber() {
        return this.bookingNumber;
    }

    public String getPreviousBookingNumber() {
        return this.previousBookingNumber;
    }

    public Long getSanctionSeq() {
        return this.sanctionSeq;
    }

    public Long getMovementSeq() {
        return this.movementSeq;
    }

    public Long getImprisonmentStatusSeq() {
        return this.imprisonmentStatusSeq;
    }

    public Long getAssessmentSeq() {
        return this.assessmentSeq;
    }

    public Long getAlertSeq() {
        return this.alertSeq;
    }

    public LocalDateTime getAlertDateTime() {
        return this.alertDateTime;
    }

    public String getAlertType() {
        return this.alertType;
    }

    public String getAlertCode() {
        return this.alertCode;
    }

    public LocalDateTime getExpiryDateTime() {
        return this.expiryDateTime;
    }

    public Long getCaseNoteId() {
        return this.caseNoteId;
    }

    public String getAgencyLocationId() {
        return this.agencyLocationId;
    }

    public Long getRiskPredictorId() {
        return this.riskPredictorId;
    }

    public Long getAddressId() {
        return this.addressId;
    }

    public Long getPersonId() {
        return this.personId;
    }

    public Long getSentenceCalculationId() {
        return this.sentenceCalculationId;
    }

    public Long getOicHearingId() {
        return this.oicHearingId;
    }

    public Long getOicOffenceId() {
        return this.oicOffenceId;
    }

    public String getPleaFindingCode() {
        return this.pleaFindingCode;
    }

    public String getFindingCode() {
        return this.findingCode;
    }

    public Long getResultSeq() {
        return this.resultSeq;
    }

    public Long getAgencyIncidentId() {
        return this.agencyIncidentId;
    }

    public Long getChargeSeq() {
        return this.chargeSeq;
    }

    public String getIdentifierType() {
        return this.identifierType;
    }

    public String getIdentifierValue() {
        return this.identifierValue;
    }

    public Long getOwnerId() {
        return this.ownerId;
    }

    public String getOwnerClass() {
        return this.ownerClass;
    }

    public Long getSentenceSeq() {
        return this.sentenceSeq;
    }

    public String getConditionCode() {
        return this.conditionCode;
    }

    public Long getOffenderSentenceConditionId() {
        return this.offenderSentenceConditionId;
    }

    public LocalDate getAddressEndDate() {
        return this.addressEndDate;
    }

    public String getPrimaryAddressFlag() {
        return this.primaryAddressFlag;
    }

    public String getMailAddressFlag() {
        return this.mailAddressFlag;
    }

    public String getAddressUsage() {
        return this.addressUsage;
    }

    public Long getIncidentCaseId() {
        return this.incidentCaseId;
    }

    public Long getIncidentPartySeq() {
        return this.incidentPartySeq;
    }

    public Long getIncidentRequirementSeq() {
        return this.incidentRequirementSeq;
    }

    public Long getIncidentQuestionSeq() {
        return this.incidentQuestionSeq;
    }

    public Long getIncidentResponseSeq() {
        return this.incidentResponseSeq;
    }

    public Integer getBedAssignmentSeq() {
        return this.bedAssignmentSeq;
    }

    public Long getLivingUnitId() {
        return this.livingUnitId;
    }

    public LocalDateTime getMovementDateTime() {
        return this.movementDateTime;
    }

    public String getMovementType() {
        return this.movementType;
    }

    public String getMovementReasonCode() {
        return this.movementReasonCode;
    }

    public String getDirectionCode() {
        return this.directionCode;
    }

    public String getEscortCode() {
        return this.escortCode;
    }

    public String getFromAgencyLocationId() {
        return this.fromAgencyLocationId;
    }

    public String getToAgencyLocationId() {
        return this.toAgencyLocationId;
    }

    public String getNomisEventType() {
        return this.nomisEventType;
    }

    public String getCaseNoteType() {
        return this.caseNoteType;
    }

    public String getCaseNoteSubType() {
        return this.caseNoteSubType;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setEventDatetime(LocalDateTime eventDatetime) {
        this.eventDatetime = eventDatetime;
    }

    public void setRootOffenderId(Long rootOffenderId) {
        this.rootOffenderId = rootOffenderId;
    }

    public void setOffenderId(Long offenderId) {
        this.offenderId = offenderId;
    }

    public void setAliasOffenderId(Long aliasOffenderId) {
        this.aliasOffenderId = aliasOffenderId;
    }

    public void setPreviousOffenderId(Long previousOffenderId) {
        this.previousOffenderId = previousOffenderId;
    }

    public void setOffenderIdDisplay(String offenderIdDisplay) {
        this.offenderIdDisplay = offenderIdDisplay;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public void setBookingNumber(String bookingNumber) {
        this.bookingNumber = bookingNumber;
    }

    public void setPreviousBookingNumber(String previousBookingNumber) {
        this.previousBookingNumber = previousBookingNumber;
    }

    public void setSanctionSeq(Long sanctionSeq) {
        this.sanctionSeq = sanctionSeq;
    }

    public void setMovementSeq(Long movementSeq) {
        this.movementSeq = movementSeq;
    }

    public void setImprisonmentStatusSeq(Long imprisonmentStatusSeq) {
        this.imprisonmentStatusSeq = imprisonmentStatusSeq;
    }

    public void setAssessmentSeq(Long assessmentSeq) {
        this.assessmentSeq = assessmentSeq;
    }

    public void setAlertSeq(Long alertSeq) {
        this.alertSeq = alertSeq;
    }

    public void setAlertDateTime(LocalDateTime alertDateTime) {
        this.alertDateTime = alertDateTime;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public void setAlertCode(String alertCode) {
        this.alertCode = alertCode;
    }

    public void setExpiryDateTime(LocalDateTime expiryDateTime) {
        this.expiryDateTime = expiryDateTime;
    }

    public void setCaseNoteId(Long caseNoteId) {
        this.caseNoteId = caseNoteId;
    }

    public void setAgencyLocationId(String agencyLocationId) {
        this.agencyLocationId = agencyLocationId;
    }

    public void setRiskPredictorId(Long riskPredictorId) {
        this.riskPredictorId = riskPredictorId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public void setPersonId(Long personId) {
        this.personId = personId;
    }

    public void setSentenceCalculationId(Long sentenceCalculationId) {
        this.sentenceCalculationId = sentenceCalculationId;
    }

    public void setOicHearingId(Long oicHearingId) {
        this.oicHearingId = oicHearingId;
    }

    public void setOicOffenceId(Long oicOffenceId) {
        this.oicOffenceId = oicOffenceId;
    }

    public void setPleaFindingCode(String pleaFindingCode) {
        this.pleaFindingCode = pleaFindingCode;
    }

    public void setFindingCode(String findingCode) {
        this.findingCode = findingCode;
    }

    public void setResultSeq(Long resultSeq) {
        this.resultSeq = resultSeq;
    }

    public void setAgencyIncidentId(Long agencyIncidentId) {
        this.agencyIncidentId = agencyIncidentId;
    }

    public void setChargeSeq(Long chargeSeq) {
        this.chargeSeq = chargeSeq;
    }

    public void setIdentifierType(String identifierType) {
        this.identifierType = identifierType;
    }

    public void setIdentifierValue(String identifierValue) {
        this.identifierValue = identifierValue;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public void setOwnerClass(String ownerClass) {
        this.ownerClass = ownerClass;
    }

    public void setSentenceSeq(Long sentenceSeq) {
        this.sentenceSeq = sentenceSeq;
    }

    public void setConditionCode(String conditionCode) {
        this.conditionCode = conditionCode;
    }

    public void setOffenderSentenceConditionId(Long offenderSentenceConditionId) {
        this.offenderSentenceConditionId = offenderSentenceConditionId;
    }

    public void setAddressEndDate(LocalDate addressEndDate) {
        this.addressEndDate = addressEndDate;
    }

    public void setPrimaryAddressFlag(String primaryAddressFlag) {
        this.primaryAddressFlag = primaryAddressFlag;
    }

    public void setMailAddressFlag(String mailAddressFlag) {
        this.mailAddressFlag = mailAddressFlag;
    }

    public void setAddressUsage(String addressUsage) {
        this.addressUsage = addressUsage;
    }

    public void setIncidentCaseId(Long incidentCaseId) {
        this.incidentCaseId = incidentCaseId;
    }

    public void setIncidentPartySeq(Long incidentPartySeq) {
        this.incidentPartySeq = incidentPartySeq;
    }

    public void setIncidentRequirementSeq(Long incidentRequirementSeq) {
        this.incidentRequirementSeq = incidentRequirementSeq;
    }

    public void setIncidentQuestionSeq(Long incidentQuestionSeq) {
        this.incidentQuestionSeq = incidentQuestionSeq;
    }

    public void setIncidentResponseSeq(Long incidentResponseSeq) {
        this.incidentResponseSeq = incidentResponseSeq;
    }

    public void setBedAssignmentSeq(Integer bedAssignmentSeq) {
        this.bedAssignmentSeq = bedAssignmentSeq;
    }

    public void setLivingUnitId(Long livingUnitId) {
        this.livingUnitId = livingUnitId;
    }

    public void setMovementDateTime(LocalDateTime movementDateTime) {
        this.movementDateTime = movementDateTime;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public void setMovementReasonCode(String movementReasonCode) {
        this.movementReasonCode = movementReasonCode;
    }

    public void setDirectionCode(String directionCode) {
        this.directionCode = directionCode;
    }

    public void setEscortCode(String escortCode) {
        this.escortCode = escortCode;
    }

    public void setFromAgencyLocationId(String fromAgencyLocationId) {
        this.fromAgencyLocationId = fromAgencyLocationId;
    }

    public void setToAgencyLocationId(String toAgencyLocationId) {
        this.toAgencyLocationId = toAgencyLocationId;
    }

    public void setNomisEventType(String nomisEventType) {
        this.nomisEventType = nomisEventType;
    }

    public void setCaseNoteType(String caseNoteType) {
        this.caseNoteType = caseNoteType;
    }

    public void setCaseNoteSubType(String caseNoteSubType) {
        this.caseNoteSubType = caseNoteSubType;
    }

    public void setAdditionalFields(Map<String, Object> additionalFields) {
        this.additionalFields = additionalFields;
    }

    public OffenderEventBuilder toBuilder() {
        return new OffenderEventBuilder().eventId(this.eventId).eventType(this.eventType).eventDatetime(this.eventDatetime).rootOffenderId(this.rootOffenderId).offenderId(this.offenderId).aliasOffenderId(this.aliasOffenderId).previousOffenderId(this.previousOffenderId).offenderIdDisplay(this.offenderIdDisplay).bookingId(this.bookingId).bookingNumber(this.bookingNumber).previousBookingNumber(this.previousBookingNumber).sanctionSeq(this.sanctionSeq).movementSeq(this.movementSeq).imprisonmentStatusSeq(this.imprisonmentStatusSeq).assessmentSeq(this.assessmentSeq).alertSeq(this.alertSeq).alertDateTime(this.alertDateTime).alertType(this.alertType).alertCode(this.alertCode).expiryDateTime(this.expiryDateTime).caseNoteId(this.caseNoteId).agencyLocationId(this.agencyLocationId).riskPredictorId(this.riskPredictorId).addressId(this.addressId).personId(this.personId).sentenceCalculationId(this.sentenceCalculationId).oicHearingId(this.oicHearingId).oicOffenceId(this.oicOffenceId).pleaFindingCode(this.pleaFindingCode).findingCode(this.findingCode).resultSeq(this.resultSeq).agencyIncidentId(this.agencyIncidentId).chargeSeq(this.chargeSeq).identifierType(this.identifierType).identifierValue(this.identifierValue).ownerId(this.ownerId).ownerClass(this.ownerClass).sentenceSeq(this.sentenceSeq).conditionCode(this.conditionCode).offenderSentenceConditionId(this.offenderSentenceConditionId).addressEndDate(this.addressEndDate).primaryAddressFlag(this.primaryAddressFlag).mailAddressFlag(this.mailAddressFlag).addressUsage(this.addressUsage).incidentCaseId(this.incidentCaseId).incidentPartySeq(this.incidentPartySeq).incidentRequirementSeq(this.incidentRequirementSeq).incidentQuestionSeq(this.incidentQuestionSeq).incidentResponseSeq(this.incidentResponseSeq).bedAssignmentSeq(this.bedAssignmentSeq).livingUnitId(this.livingUnitId).movementDateTime(this.movementDateTime).movementType(this.movementType).movementReasonCode(this.movementReasonCode).directionCode(this.directionCode).escortCode(this.escortCode).fromAgencyLocationId(this.fromAgencyLocationId).toAgencyLocationId(this.toAgencyLocationId).nomisEventType(this.nomisEventType).caseNoteType(this.caseNoteType).caseNoteSubType(this.caseNoteSubType).additionalFields(this.additionalFields);
    }

    public static class OffenderEventBuilder {
        private String eventId;
        private String eventType;
        private LocalDateTime eventDatetime;
        private Long rootOffenderId;
        private Long offenderId;
        private Long aliasOffenderId;
        private Long previousOffenderId;
        private String offenderIdDisplay;
        private Long bookingId;
        private String bookingNumber;
        private String previousBookingNumber;
        private Long sanctionSeq;
        private Long movementSeq;
        private Long imprisonmentStatusSeq;
        private Long assessmentSeq;
        private Long alertSeq;
        private LocalDateTime alertDateTime;
        private String alertType;
        private String alertCode;
        private LocalDateTime expiryDateTime;
        private Long caseNoteId;
        private String agencyLocationId;
        private Long riskPredictorId;
        private Long addressId;
        private Long personId;
        private Long sentenceCalculationId;
        private Long oicHearingId;
        private Long oicOffenceId;
        private String pleaFindingCode;
        private String findingCode;
        private Long resultSeq;
        private Long agencyIncidentId;
        private Long chargeSeq;
        private String identifierType;
        private String identifierValue;
        private Long ownerId;
        private String ownerClass;
        private Long sentenceSeq;
        private String conditionCode;
        private Long offenderSentenceConditionId;
        private LocalDate addressEndDate;
        private String primaryAddressFlag;
        private String mailAddressFlag;
        private String addressUsage;
        private Long incidentCaseId;
        private Long incidentPartySeq;
        private Long incidentRequirementSeq;
        private Long incidentQuestionSeq;
        private Long incidentResponseSeq;
        private Integer bedAssignmentSeq;
        private Long livingUnitId;
        private LocalDateTime movementDateTime;
        private String movementType;
        private String movementReasonCode;
        private String directionCode;
        private String escortCode;
        private String fromAgencyLocationId;
        private String toAgencyLocationId;
        private String nomisEventType;
        private String caseNoteType;
        private String caseNoteSubType;
        private Map<String, Object> additionalFields;

        OffenderEventBuilder() {
        }

        public OffenderEventBuilder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public OffenderEventBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public OffenderEventBuilder eventDatetime(LocalDateTime eventDatetime) {
            this.eventDatetime = eventDatetime;
            return this;
        }

        public OffenderEventBuilder rootOffenderId(Long rootOffenderId) {
            this.rootOffenderId = rootOffenderId;
            return this;
        }

        public OffenderEventBuilder offenderId(Long offenderId) {
            this.offenderId = offenderId;
            return this;
        }

        public OffenderEventBuilder aliasOffenderId(Long aliasOffenderId) {
            this.aliasOffenderId = aliasOffenderId;
            return this;
        }

        public OffenderEventBuilder previousOffenderId(Long previousOffenderId) {
            this.previousOffenderId = previousOffenderId;
            return this;
        }

        public OffenderEventBuilder offenderIdDisplay(String offenderIdDisplay) {
            this.offenderIdDisplay = offenderIdDisplay;
            return this;
        }

        public OffenderEventBuilder bookingId(Long bookingId) {
            this.bookingId = bookingId;
            return this;
        }

        public OffenderEventBuilder bookingNumber(String bookingNumber) {
            this.bookingNumber = bookingNumber;
            return this;
        }

        public OffenderEventBuilder previousBookingNumber(String previousBookingNumber) {
            this.previousBookingNumber = previousBookingNumber;
            return this;
        }

        public OffenderEventBuilder sanctionSeq(Long sanctionSeq) {
            this.sanctionSeq = sanctionSeq;
            return this;
        }

        public OffenderEventBuilder movementSeq(Long movementSeq) {
            this.movementSeq = movementSeq;
            return this;
        }

        public OffenderEventBuilder imprisonmentStatusSeq(Long imprisonmentStatusSeq) {
            this.imprisonmentStatusSeq = imprisonmentStatusSeq;
            return this;
        }

        public OffenderEventBuilder assessmentSeq(Long assessmentSeq) {
            this.assessmentSeq = assessmentSeq;
            return this;
        }

        public OffenderEventBuilder alertSeq(Long alertSeq) {
            this.alertSeq = alertSeq;
            return this;
        }

        public OffenderEventBuilder alertDateTime(LocalDateTime alertDateTime) {
            this.alertDateTime = alertDateTime;
            return this;
        }

        public OffenderEventBuilder alertType(String alertType) {
            this.alertType = alertType;
            return this;
        }

        public OffenderEventBuilder alertCode(String alertCode) {
            this.alertCode = alertCode;
            return this;
        }

        public OffenderEventBuilder expiryDateTime(LocalDateTime expiryDateTime) {
            this.expiryDateTime = expiryDateTime;
            return this;
        }

        public OffenderEventBuilder caseNoteId(Long caseNoteId) {
            this.caseNoteId = caseNoteId;
            return this;
        }

        public OffenderEventBuilder agencyLocationId(String agencyLocationId) {
            this.agencyLocationId = agencyLocationId;
            return this;
        }

        public OffenderEventBuilder riskPredictorId(Long riskPredictorId) {
            this.riskPredictorId = riskPredictorId;
            return this;
        }

        public OffenderEventBuilder addressId(Long addressId) {
            this.addressId = addressId;
            return this;
        }

        public OffenderEventBuilder personId(Long personId) {
            this.personId = personId;
            return this;
        }

        public OffenderEventBuilder sentenceCalculationId(Long sentenceCalculationId) {
            this.sentenceCalculationId = sentenceCalculationId;
            return this;
        }

        public OffenderEventBuilder oicHearingId(Long oicHearingId) {
            this.oicHearingId = oicHearingId;
            return this;
        }

        public OffenderEventBuilder oicOffenceId(Long oicOffenceId) {
            this.oicOffenceId = oicOffenceId;
            return this;
        }

        public OffenderEventBuilder pleaFindingCode(String pleaFindingCode) {
            this.pleaFindingCode = pleaFindingCode;
            return this;
        }

        public OffenderEventBuilder findingCode(String findingCode) {
            this.findingCode = findingCode;
            return this;
        }

        public OffenderEventBuilder resultSeq(Long resultSeq) {
            this.resultSeq = resultSeq;
            return this;
        }

        public OffenderEventBuilder agencyIncidentId(Long agencyIncidentId) {
            this.agencyIncidentId = agencyIncidentId;
            return this;
        }

        public OffenderEventBuilder chargeSeq(Long chargeSeq) {
            this.chargeSeq = chargeSeq;
            return this;
        }

        public OffenderEventBuilder identifierType(String identifierType) {
            this.identifierType = identifierType;
            return this;
        }

        public OffenderEventBuilder identifierValue(String identifierValue) {
            this.identifierValue = identifierValue;
            return this;
        }

        public OffenderEventBuilder ownerId(Long ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public OffenderEventBuilder ownerClass(String ownerClass) {
            this.ownerClass = ownerClass;
            return this;
        }

        public OffenderEventBuilder sentenceSeq(Long sentenceSeq) {
            this.sentenceSeq = sentenceSeq;
            return this;
        }

        public OffenderEventBuilder conditionCode(String conditionCode) {
            this.conditionCode = conditionCode;
            return this;
        }

        public OffenderEventBuilder offenderSentenceConditionId(Long offenderSentenceConditionId) {
            this.offenderSentenceConditionId = offenderSentenceConditionId;
            return this;
        }

        public OffenderEventBuilder addressEndDate(LocalDate addressEndDate) {
            this.addressEndDate = addressEndDate;
            return this;
        }

        public OffenderEventBuilder primaryAddressFlag(String primaryAddressFlag) {
            this.primaryAddressFlag = primaryAddressFlag;
            return this;
        }

        public OffenderEventBuilder mailAddressFlag(String mailAddressFlag) {
            this.mailAddressFlag = mailAddressFlag;
            return this;
        }

        public OffenderEventBuilder addressUsage(String addressUsage) {
            this.addressUsage = addressUsage;
            return this;
        }

        public OffenderEventBuilder incidentCaseId(Long incidentCaseId) {
            this.incidentCaseId = incidentCaseId;
            return this;
        }

        public OffenderEventBuilder incidentPartySeq(Long incidentPartySeq) {
            this.incidentPartySeq = incidentPartySeq;
            return this;
        }

        public OffenderEventBuilder incidentRequirementSeq(Long incidentRequirementSeq) {
            this.incidentRequirementSeq = incidentRequirementSeq;
            return this;
        }

        public OffenderEventBuilder incidentQuestionSeq(Long incidentQuestionSeq) {
            this.incidentQuestionSeq = incidentQuestionSeq;
            return this;
        }

        public OffenderEventBuilder incidentResponseSeq(Long incidentResponseSeq) {
            this.incidentResponseSeq = incidentResponseSeq;
            return this;
        }

        public OffenderEventBuilder bedAssignmentSeq(Integer bedAssignmentSeq) {
            this.bedAssignmentSeq = bedAssignmentSeq;
            return this;
        }

        public OffenderEventBuilder livingUnitId(Long livingUnitId) {
            this.livingUnitId = livingUnitId;
            return this;
        }

        public OffenderEventBuilder movementDateTime(LocalDateTime movementDateTime) {
            this.movementDateTime = movementDateTime;
            return this;
        }

        public OffenderEventBuilder movementType(String movementType) {
            this.movementType = movementType;
            return this;
        }

        public OffenderEventBuilder movementReasonCode(String movementReasonCode) {
            this.movementReasonCode = movementReasonCode;
            return this;
        }

        public OffenderEventBuilder directionCode(String directionCode) {
            this.directionCode = directionCode;
            return this;
        }

        public OffenderEventBuilder escortCode(String escortCode) {
            this.escortCode = escortCode;
            return this;
        }

        public OffenderEventBuilder fromAgencyLocationId(String fromAgencyLocationId) {
            this.fromAgencyLocationId = fromAgencyLocationId;
            return this;
        }

        public OffenderEventBuilder toAgencyLocationId(String toAgencyLocationId) {
            this.toAgencyLocationId = toAgencyLocationId;
            return this;
        }

        public OffenderEventBuilder nomisEventType(String nomisEventType) {
            this.nomisEventType = nomisEventType;
            return this;
        }

        public OffenderEventBuilder caseNoteType(String caseNoteType) {
            this.caseNoteType = caseNoteType;
            return this;
        }

        public OffenderEventBuilder caseNoteSubType(String caseNoteSubType) {
            this.caseNoteSubType = caseNoteSubType;
            return this;
        }

        public OffenderEventBuilder additionalFields(Map<String, Object> additionalFields) {
            this.additionalFields = additionalFields;
            return this;
        }

        public OffenderEvent build() {
            return new OffenderEvent(eventId, eventType, eventDatetime, rootOffenderId, offenderId, aliasOffenderId, previousOffenderId, offenderIdDisplay, bookingId, bookingNumber, previousBookingNumber, sanctionSeq, movementSeq, imprisonmentStatusSeq, assessmentSeq, alertSeq, alertDateTime, alertType, alertCode, expiryDateTime, caseNoteId, agencyLocationId, riskPredictorId, addressId, personId, sentenceCalculationId, oicHearingId, oicOffenceId, pleaFindingCode, findingCode, resultSeq, agencyIncidentId, chargeSeq, identifierType, identifierValue, ownerId, ownerClass, sentenceSeq, conditionCode, offenderSentenceConditionId, addressEndDate, primaryAddressFlag, mailAddressFlag, addressUsage, incidentCaseId, incidentPartySeq, incidentRequirementSeq, incidentQuestionSeq, incidentResponseSeq, bedAssignmentSeq, livingUnitId, movementDateTime, movementType, movementReasonCode, directionCode, escortCode, fromAgencyLocationId, toAgencyLocationId, nomisEventType, caseNoteType, caseNoteSubType, additionalFields);
        }

        public String toString() {
            return "OffenderEvent.OffenderEventBuilder(eventId=" + this.eventId + ", eventType=" + this.eventType + ", eventDatetime=" + this.eventDatetime + ", rootOffenderId=" + this.rootOffenderId + ", offenderId=" + this.offenderId + ", aliasOffenderId=" + this.aliasOffenderId + ", previousOffenderId=" + this.previousOffenderId + ", offenderIdDisplay=" + this.offenderIdDisplay + ", bookingId=" + this.bookingId + ", bookingNumber=" + this.bookingNumber + ", previousBookingNumber=" + this.previousBookingNumber + ", sanctionSeq=" + this.sanctionSeq + ", movementSeq=" + this.movementSeq + ", imprisonmentStatusSeq=" + this.imprisonmentStatusSeq + ", assessmentSeq=" + this.assessmentSeq + ", alertSeq=" + this.alertSeq + ", alertDateTime=" + this.alertDateTime + ", alertType=" + this.alertType + ", alertCode=" + this.alertCode + ", expiryDateTime=" + this.expiryDateTime + ", caseNoteId=" + this.caseNoteId + ", agencyLocationId=" + this.agencyLocationId + ", riskPredictorId=" + this.riskPredictorId + ", addressId=" + this.addressId + ", personId=" + this.personId + ", sentenceCalculationId=" + this.sentenceCalculationId + ", oicHearingId=" + this.oicHearingId + ", oicOffenceId=" + this.oicOffenceId + ", pleaFindingCode=" + this.pleaFindingCode + ", findingCode=" + this.findingCode + ", resultSeq=" + this.resultSeq + ", agencyIncidentId=" + this.agencyIncidentId + ", chargeSeq=" + this.chargeSeq + ", identifierType=" + this.identifierType + ", identifierValue=" + this.identifierValue + ", ownerId=" + this.ownerId + ", ownerClass=" + this.ownerClass + ", sentenceSeq=" + this.sentenceSeq + ", conditionCode=" + this.conditionCode + ", offenderSentenceConditionId=" + this.offenderSentenceConditionId + ", addressEndDate=" + this.addressEndDate + ", primaryAddressFlag=" + this.primaryAddressFlag + ", mailAddressFlag=" + this.mailAddressFlag + ", addressUsage=" + this.addressUsage + ", incidentCaseId=" + this.incidentCaseId + ", incidentPartySeq=" + this.incidentPartySeq + ", incidentRequirementSeq=" + this.incidentRequirementSeq + ", incidentQuestionSeq=" + this.incidentQuestionSeq + ", incidentResponseSeq=" + this.incidentResponseSeq + ", bedAssignmentSeq=" + this.bedAssignmentSeq + ", livingUnitId=" + this.livingUnitId + ", movementDateTime=" + this.movementDateTime + ", movementType=" + this.movementType + ", movementReasonCode=" + this.movementReasonCode + ", directionCode=" + this.directionCode + ", escortCode=" + this.escortCode + ", fromAgencyLocationId=" + this.fromAgencyLocationId + ", toAgencyLocationId=" + this.toAgencyLocationId + ", nomisEventType=" + this.nomisEventType + ", caseNoteType=" + this.caseNoteType + ", caseNoteSubType=" + this.caseNoteSubType + ", additionalFields=" + this.additionalFields + ")";
        }
    }
}
