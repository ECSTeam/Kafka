package com.ecs.servicebroker.kafka.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ecs.servicebroker.kafka.model.ServiceInstanceBinding;

public interface KafkaServiceInstanceBindingRepository extends JpaRepository <ServiceInstanceBinding, String>{

}
