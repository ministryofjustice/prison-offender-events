package uk.gov.justice.hmpps.offenderevents.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.justice.hmpps.offenderevents.model.PollAudit;

@Repository
public interface PollAuditRepository extends CrudRepository<PollAudit, String> {
}
