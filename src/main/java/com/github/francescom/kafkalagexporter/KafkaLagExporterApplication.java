package com.github.francescom.kafkalagexporter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@Configuration
@EnableScheduling
public class KafkaLagExporterApplication {
	/**
	 * The interface to implement to export the kafka lag info
	 */
	public interface KafkaLagExporter {
		Logger logger = LoggerFactory.getLogger("LogNotifierInterface");
		default void notifyLag(String group,String topic,int partition, long lag){
			logger.info("Group {} has {} lag on Topic-Partition {}{}",group,lag,topic,partition);
		}
	}

	final static String CHECKRATE_CONF_STRING = "${lagexporter-checkrate:30000}";
	final static Logger logger = LoggerFactory.getLogger(KafkaLagExporterApplication.class);
	public static void main(String[] args) {
		SpringApplication.run(KafkaLagExporterApplication.class, args);
	}

	@EventListener
	public void onApplicationEvent(ApplicationReadyEvent event) {
		logger.info("Application started with check rate value {}",CheckRateValue);
	}

	@Value(CHECKRATE_CONF_STRING)
	long CheckRateValue;
	@Autowired
	KafkaProperties kafkaProperties;
	@Autowired
	ApplicationContext context;
	@Autowired
	KafkaLagExporter kafkaLagExporter;
	/**
	 * Implementation to export metric as gauge on the prometheus endpoint
	 * @param meterRegistry
	 * @return
	 */
	@Bean
	@Autowired
	KafkaLagExporter kafkaLagExporterInterface(MeterRegistry meterRegistry){
		return new KafkaLagExporter() {
			@Override
			public void notifyLag(String group, String topic, int partition, long lag) {
				List<Tag> tagList = new ArrayList<>();
				tagList.add(Tag.of("group",group));
				tagList.add(Tag.of("topic",topic));
				tagList.add(Tag.of("partition",partition+""));
				meterRegistry.gauge("kafka_topic_lag",tagList,lag);
			}
		};
	}

	@Scheduled(fixedRateString = CHECKRATE_CONF_STRING)
	private void updateMetric(){
			final KafkaConsumer<byte[],byte[]> kafkaConsumer = new KafkaConsumer<>(kafkaProperties.buildConsumerProperties());
			final AdminClient adminClient =  AdminClient.create(kafkaProperties.buildAdminProperties());
			try {
				adminClient.describeCluster().clusterId()
						.get(10, TimeUnit.SECONDS);
				kafkaConsumer.listTopics(Duration.of(10,ChronoUnit.SECONDS));
			}catch (Exception e){
				logger.error("Connection error to the kafka server. Cannot update kafka metric");
				return;
			}
			try {
				logger.debug("Calculating lag metrics cycle");
				for (ConsumerGroupListing cgListing : adminClient.listConsumerGroups().valid().get()) {
					String consumerGroup = cgListing.groupId();
					Map<TopicPartition, OffsetAndMetadata> groupOffsets = adminClient
							.listConsumerGroupOffsets(consumerGroup)
							.partitionsToOffsetAndMetadata()
							.get();
					Map<TopicPartition, Long> GroupPartitionEndOffsets = kafkaConsumer
							.endOffsets(groupOffsets.keySet());

					groupOffsets.forEach((topicPartition, offsetAndMetadata) -> {
						Long endOffset = GroupPartitionEndOffsets.get(topicPartition);
						if (endOffset == null)
							return;
						long groupLag = endOffset - offsetAndMetadata.offset();
						kafkaLagExporter
								.notifyLag(consumerGroup,topicPartition.topic(),topicPartition.partition(),groupLag);
					});
				}
			} catch (Exception e) {
				logger.error("Exception occurred in Syslog Monitor Kafka Lag monitor, will shut down the application",e);
				SpringApplication.exit(context, () -> -1);
				System.exit(-1);
			}
	}
}