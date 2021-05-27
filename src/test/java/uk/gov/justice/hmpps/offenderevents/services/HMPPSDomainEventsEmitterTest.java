package uk.gov.justice.hmpps.offenderevents.services;

import com.amazonaws.services.sns.AmazonSNSAsync;
import com.amazonaws.services.sns.model.PublishRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.hmpps.offenderevents.model.OffenderEvent;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class HMPPSDomainEventsEmitterTest {
    private HMPPSDomainEventsEmitter emitter;

    @Mock
    private AmazonSNSAsync amazonSns;

    @Captor
    private ArgumentCaptor<PublishRequest> publishRequest;

    @SuppressWarnings("unused")
    private static Stream<Arguments> eventMap() {
        return Stream.of(
            Arguments.of("OFFENDER_MOVEMENT-DISCHARGE", "PRISONER_RELEASED"),
            Arguments.of("OFFENDER_MOVEMENT-RECEPTION", "PRISONER_RECEIVED")
        );
    }

    @BeforeEach
    void setUp() {
        emitter = new HMPPSDomainEventsEmitter(amazonSns, "sometopicarn", new ObjectMapper());
    }

    @Test
    @DisplayName("Will do nothing for insignificant events")
    void willDoNothingForInsignificantEvents() {
        emitter.convertAndSendWhenSignificant(OffenderEvent.builder().eventType("BALANCE_UPDATED").build());

        verifyNoInteractions(amazonSns);
    }

    @ParameterizedTest
    @MethodSource("eventMap")
    @DisplayName("Will send to topic for these events")
    void willSendToTopicForTheseEvents(String prisonEventType, String eventType) {
        emitter.convertAndSendWhenSignificant(OffenderEvent
            .builder()
            .eventType(prisonEventType)
            .offenderIdDisplay("A1234GH")
            .build());

        verify(amazonSns).publish(publishRequest.capture());

        assertThat(publishRequest.getValue().getMessage())
            .isEqualTo(String.format("""
                {"eventType":"%s","nomsNumber":"A1234GH"}""", eventType));

    }
}
