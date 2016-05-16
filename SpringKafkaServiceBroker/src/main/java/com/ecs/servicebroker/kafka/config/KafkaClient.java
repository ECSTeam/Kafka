package com.ecs.servicebroker.kafka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaClient {
	@Value("${kafka.host:localhost}")
	private String host;

	@Value("${kafka.port:9092}")
	private int port;
}
