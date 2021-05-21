package uk.gov.justice.hmpps.offenderevents.services;

import org.springframework.stereotype.Service;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

@Service
public class HMPPSDomainEventsEmitter {
    public void convertAndSendWhenSignificant(OffenderEvent event) {
        // TODO check significant HMPPS wide event and send to Domain topic
    }
}
