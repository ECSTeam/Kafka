package com.ecs.kafka.util;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class SpringKafkaUtilApplication {
	private static final Integer orderNumber = new Integer(1);
	private static final String topicName = "orderTopic";
	public static void main(String[] args) {
		SpringApplication.run(SpringKafkaUtilApplication.class, args);
	}
	@Bean
	public CommandLineRunner publishToKafkaBean() {
		return arg -> {
			String topicName = "orderTopic";
			//Configure the Producer
            Properties configProperties = new Properties();
            configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"192.168.61.22:9092");
            configProperties.put("security.protocol", "SASL_PLAINTEXT");
            configProperties.put("sasl.kerberos.service.name", "kafka");
            configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
            configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
            Producer<String, String> producer = new KafkaProducer<String, String>(configProperties);
            ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName, orderNumber.toString());
            producer.send(rec);
            System.err.println("********************************************Message Sent********************************************");
            producer.close();
		};
	}
	@Bean
	public Producer<String, String> getKafkaProducer() {
		//Configure the Producer
        Properties configProperties = new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"192.168.61.22:9092");
        configProperties.put("security.protocol", "SASL_PLAINTEXT");
        configProperties.put("sasl.kerberos.service.name", "kafka");
        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
        Producer<String, String> producer = new KafkaProducer<String, String>(configProperties);
        return producer;
	}
	
	@Scheduled(fixedRate = 1000L)
	public void publishToKafka() {
		System.err.println("publishing message...Begin");
		ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName, orderNumber.toString());
		getKafkaProducer().send(rec);
		System.err.println("publishing message...End");
	}
	
//	@Scheduled(fixedRate = 1000L)
//	public void publishToKafka() {
//		String topicName = "orderTopic";
//		//Configure the Producer
//        Properties configProperties = new Properties();
//        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"192.168.61.22:9092");
//        configProperties.put("security.protocol", "SASL_PLAINTEXT");
//        configProperties.put("sasl.kerberos.service.name", "kafka");
//        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
//        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");
//        Producer<String, String> producer = new KafkaProducer<String, String>(configProperties);
//        ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName, orderNumber.toString());
//        producer.send(rec);
//        System.err.println("********************************************Message Sent********************************************");
//        producer.close();
//	}
}
