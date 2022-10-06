package uk.gov.justice.hmpps.offenderevents.schedule

import com.microsoft.applicationinsights.TelemetryClient
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import uk.gov.justice.hmpps.offenderevents.services.EventRetrievalService
import java.time.LocalDateTime

@Service

class EventScheduler(
  private val eventRetrievalService: EventRetrievalService,
  private val telemetryClient: TelemetryClient
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(
    fixedDelayString = "\${application.events.poll.frequency}",
    initialDelayString = "\${application.events.poll.initialDelay}"
  )
  @SchedulerLock(name = "pollEventsLock")
  fun pollEvents() {

    log.info("Starting: Event Poll")
    try {
      eventRetrievalService.pollEvents(LocalDateTime.now())
    } catch (e: Exception) {
      log.error("pollEvents: Global exception handler", e)
      telemetryClient.trackException(e)
    }
    log.info("Complete: Event Poll")
  }

  @Scheduled(cron = "43 * * * * ?")
  @SchedulerLock(name = "runTestPollsLock")
  fun runTestPolls() {

    log.info("Starting: runTestPolls()")
    try {
      eventRetrievalService.runTestPolls(LocalDateTime.now(), true, 10)
      log.info("Complete: runTestPolls()")
    } catch (e: Exception) {
      log.error("runTestPolls: Global exception handler", e)
      telemetryClient.trackException(e)
    }
  }
}
