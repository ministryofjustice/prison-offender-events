package uk.gov.justice.hmpps.offenderevents.model;

import lombok.*;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "POLL_AUDIT")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = {"pollName"})
@ToString
public class PollAudit {

    @Id
    private String pollName;

    private LocalDateTime nextRunTime;

    private int numberOfRecords;

}
