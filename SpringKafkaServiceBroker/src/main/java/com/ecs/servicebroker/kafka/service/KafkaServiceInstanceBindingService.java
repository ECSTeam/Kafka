package com.ecs.servicebroker.kafka.service;

import java.util.HashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceBrokerInvalidParametersException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingDoesNotExistException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceBindingExistsException;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.model.DeleteServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.service.ServiceInstanceBindingService;
import org.springframework.stereotype.Service;

import com.ecs.servicebroker.kafka.config.KafkaClient;
import com.ecs.servicebroker.kafka.model.ServiceInstance;
import com.ecs.servicebroker.kafka.model.ServiceInstanceBinding;
import com.ecs.servicebroker.kafka.repository.KafkaServiceInstanceBindingRepository;
import com.ecs.servicebroker.kafka.repository.KafkaServiceInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KafkaServiceInstanceBindingService implements ServiceInstanceBindingService {

	@Autowired
	KafkaClient kafkaClient;
	
	private KafkaServiceInstanceBindingRepository bindingRepository;
	private KafkaServiceInstanceRepository instanceRepository;
	
	@Autowired
	public KafkaServiceInstanceBindingService(KafkaServiceInstanceBindingRepository bindingRepository, KafkaServiceInstanceRepository instanceRepository) {
		this.bindingRepository = bindingRepository;
		this.instanceRepository = instanceRepository;
	}
	
	@Override
	public CreateServiceInstanceBindingResponse createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
		System.out.println("createServiceInstanceBinding entering..."+request.getParameters());
		String bindingId = request.getBindingId();
		String serviceInstanceId = request.getServiceInstanceId();

		ServiceInstanceBinding binding = bindingRepository.findOne(bindingId);
		if (binding != null) {
			throw new ServiceInstanceBindingExistsException(serviceInstanceId, bindingId);
		}
		ServiceInstance instance = instanceRepository.findOne(serviceInstanceId);
		String credentialsJson = instance.getCredentials();
		HashMap<String, Object> credentials;
		try {
			credentials = new ObjectMapper().readValue(credentialsJson, HashMap.class);
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServiceBrokerInvalidParametersException(e);
		}

		binding = new ServiceInstanceBinding(bindingId, serviceInstanceId, credentials, null, request.getBoundAppGuid());
		bindingRepository.save(binding);
		 
		return new CreateServiceInstanceAppBindingResponse().withCredentials(credentials);
	}

	@Override
	public void deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
		System.out.println("deleteServiceInstanceBinding entering...");
		String bindingId = request.getBindingId();
		ServiceInstanceBinding binding = getServiceInstanceBinding(bindingId);

		if (binding == null) {
			throw new ServiceInstanceBindingDoesNotExistException(bindingId);
		}
		bindingRepository.delete(bindingId);
	}
	protected ServiceInstanceBinding getServiceInstanceBinding(String id) {
		return bindingRepository.findOne(id);
	}
}
