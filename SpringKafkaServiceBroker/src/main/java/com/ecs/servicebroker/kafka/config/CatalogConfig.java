package com.ecs.servicebroker.kafka.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.servicebroker.model.Catalog;
import org.springframework.cloud.servicebroker.model.Plan;
import org.springframework.cloud.servicebroker.model.ServiceDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfig {
	@Bean
	public Catalog catalog() {
		return new Catalog(Collections.singletonList(
				new ServiceDefinition(
						"kafka-service-broker",
						"Kafka",
						"A Kafka Service Broker Implementation",
						true,
						false,
						Collections.singletonList(
								new Plan("kafka-plan",
										"standard",
										"This is a default kafka plan.  All services are created equally.",
										getPlanMetadata())),
						Arrays.asList("kafka", "document"),
						getServiceDefinitionMetadata(),
						null,
						null)));
	}

	private Map<String, Object> getServiceDefinitionMetadata() {
		Map<String, Object> sdMetadata = new HashMap<>();
		sdMetadata.put("displayName", "Kafka");
		sdMetadata.put("imageUrl", "http://kafka.apache.org/images/kafka_logo.png");
		sdMetadata.put("longDescription", "Kafka Service");
		sdMetadata.put("providerDisplayName", "ECS");
		sdMetadata.put("documentationUrl", "https://github.com/ECSTeam/cloudfoundry-kafka-service-broker");
		sdMetadata.put("supportUrl", "https://github.com/ECSTeam/cloudfoundry-kafka-service-broker");
		return sdMetadata;
	}
	private Map<String,Object> getPlanMetadata() {
		Map<String,Object> planMetadata = new HashMap<>();
		planMetadata.put("costs", getCosts());
		planMetadata.put("bullets", getBullets());
		return planMetadata;
	}
	private List<Map<String,Object>> getCosts() {
		Map<String,Object> costsMap = new HashMap<>();
		
		Map<String,Object> amount = new HashMap<>();
		amount.put("usd", 0.0);
	
		costsMap.put("amount", amount);
		costsMap.put("unit", "MONTHLY");
		
		return Collections.singletonList(costsMap);
	}
	
	private List<String> getBullets() {
		return Arrays.asList("Shared Kafka server");
	}
}
