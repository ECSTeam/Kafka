package com.ecs.servicebroker.kafka.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecs.servicebroker.kafka.model.ServiceInstance;

public interface KafkaServiceInstanceRepository extends JpaRepository <ServiceInstance, String>{

}
