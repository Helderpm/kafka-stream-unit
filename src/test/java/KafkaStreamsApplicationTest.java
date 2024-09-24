import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.KStream;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaStreamsApplicationTest {

    private static KafkaContainer kafkaContainer;
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, String> inputTopic;
    private TestOutputTopic<String, Long> outputTopic;

    @BeforeAll
    static void setUpAll() {
        kafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:latest"));
        kafkaContainer.start();
    }

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-word-count-application");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        StreamsBuilder builder = new StreamsBuilder();
        KStream<String, String> textLines = builder.stream("input-topic"); // Assuming "input-topic" is your input topic

        KStream<String, Long> wordCounts = textLines
                .flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\W+"))) // Ensure toLowerCase() is applied
                .groupBy((key, word) -> word)
                .count()
                .toStream();

        wordCounts.to("output-topic"); // Assuming "output-topic" is your output topic

        Topology topology = builder.build();

        testDriver = new TopologyTestDriver(topology, props);

        // Create topics
        createTopics("input-topic", "output-topic");

        // Wait for topics to be created and available
        waitForTopicsToBeCreated("input-topic", "output-topic");

        inputTopic = testDriver.createInputTopic("input-topic", Serdes.String().serializer(), Serdes.String().serializer());
        outputTopic = testDriver.createOutputTopic("output-topic", Serdes.String().deserializer(), Serdes.Long().deserializer());
    }

    @AfterAll
    static void tearDownAll() {
        kafkaContainer.stop();
    }

    private void createTopics(String... topicNames) {
        try (AdminClient adminClient = AdminClient.create(Collections.singletonMap(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()
        ))) {
            adminClient.createTopics(Arrays.stream(topicNames)
                            .map(topicName -> new NewTopic(topicName, 1, (short) 1))
                            .toList())
                    .all().get();
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new RuntimeException("Failed to create topics", e);
            }
            // Ignore TopicExistsException, topics might have been created in a previous test run
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while creating topics", e);
        }
    }

    private void waitForTopicsToBeCreated(String... topicNames) {
        try (AdminClient adminClient = AdminClient.create(Collections.singletonMap(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()
        ))) {
            await().atMost(10, TimeUnit.SECONDS).until(() -> {
                try {
                    return adminClient.listTopics().names().get().containsAll(Arrays.asList(topicNames));
                } catch (Exception e) {
                    return false;
                }
            });
        }
    }

    @Test
    void shouldCountWordsCorrectly() {
        // Given
        List<KeyValue<String, String>> inputValues = Arrays.asList(
                new KeyValue<>("key1", "Hello, world!"),
                new KeyValue<>("key2", "This is a test."),
                new KeyValue<>("key3", "again!")
        );

        // When
        inputTopic.pipeKeyValueList(inputValues);

        // Then
        List<KeyValue<String, Long>> expectedOutput = Arrays.asList(
                new KeyValue<>("hello", 1L),
                new KeyValue<>("world", 1L),
                new KeyValue<>("this", 1L),
                new KeyValue<>("is", 1L),
                new KeyValue<>("a", 1L),
                new KeyValue<>("test", 1L),
                new KeyValue<>("again", 1L)
        );

        List<KeyValue<String, Long>> actualOutput = outputTopic.readKeyValuesToList();
        assertEquals(expectedOutput, actualOutput);
    }

    @AfterEach
    void tearDown() {
        testDriver.close();
    }
}