package uk.gov.justice.hmpps.offenderevents.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Data
public class OffenderEvent {

    private String eventType;
    private String nomsId;

}
