package com.herafi.analytics.listener;

import com.herafi.analytics.dto.JobEvent;
import com.herafi.analytics.entity.AnalyticsJob;
import com.herafi.analytics.repository.AnalyticsJobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"jobs.lifecycle"}, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" })
@DirtiesContext
public class AnalyticsEventListenerTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private AnalyticsJobRepository jobRepository;

    @Test
    public void testHandleJobEvent_savesToDatabase() throws InterruptedException {
        jobRepository.deleteAll();
        // 1. Arrange: Create a fake event mimicking your teammate's outbox payload
        JobEvent event = new JobEvent();
        event.setJobId(UUID.randomUUID());
        event.setClientId(UUID.randomUUID());
        event.setStatus("OPEN");
        event.setSkill("PLUMBING");
        event.setCity("Ben Guerir");
        event.setTimestamp(Instant.now());

        // 2. Act: Fire the event into the Kafka topic
        kafkaTemplate.send("jobs.lifecycle", event);

        // Pause the test thread for 1 second. Kafka is asynchronous, so we must 
        // give the background listener time to catch the message and save it.
        Thread.sleep(2000);

        // 3. Assert: Check your local database to prove the listener worked
        List<AnalyticsJob> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getSkill()).isEqualTo("PLUMBING");
        assertThat(jobs.get(0).getCity()).isEqualTo("Ben Guerir");
    }
}