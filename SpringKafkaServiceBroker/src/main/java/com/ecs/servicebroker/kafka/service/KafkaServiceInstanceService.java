package com.ecs.servicebroker.kafka.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceExistsException;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.DeleteServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.DeleteServiceInstanceResponse;
import org.springframework.cloud.servicebroker.model.GetLastServiceOperationRequest;
import org.springframework.cloud.servicebroker.model.GetLastServiceOperationResponse;
import org.springframework.cloud.servicebroker.model.OperationState;
import org.springframework.cloud.servicebroker.model.UpdateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.UpdateServiceInstanceResponse;
import org.springframework.cloud.servicebroker.service.ServiceInstanceService;
import org.springframework.stereotype.Service;

import com.ecs.servicebroker.kafka.config.KafkaClient;
import com.ecs.servicebroker.kafka.model.ServiceInstance;
import com.ecs.servicebroker.kafka.repository.KafkaServiceInstanceRepository;

@Service
public class KafkaServiceInstanceService implements ServiceInstanceService {

	@Autowired
	KafkaClient kafkaClient;
	
	
	private KafkaServiceInstanceRepository repository;
	
	@Autowired
	public KafkaServiceInstanceService(KafkaServiceInstanceRepository repository) {
		this.repository = repository;
	}
	
	@Override
	public CreateServiceInstanceResponse createServiceInstance(CreateServiceInstanceRequest request) {
		System.out.println("createServiceInstance entering...");
		ServiceInstance instance = repository.findOne(request.getServiceInstanceId());
		if (instance != null) {
			throw new ServiceInstanceExistsException(request.getServiceInstanceId(), request.getServiceDefinitionId());
		}

		instance = new ServiceInstance(request);

		repository.save(instance);
		CreateServiceInstanceResponse response = new CreateServiceInstanceResponse();
		System.out.println("createServiceInstance response = " + response);
		return response;
	}

	@Override
	public GetLastServiceOperationResponse getLastOperation(GetLastServiceOperationRequest request) {
		System.out.println("getLastOperation entering...");
		return new GetLastServiceOperationResponse().withOperationState(OperationState.SUCCEEDED);
	}

	@Override
	public DeleteServiceInstanceResponse deleteServiceInstance(DeleteServiceInstanceRequest request) {
		System.out.println("deleteServiceInstance entering...");
		String instanceId = request.getServiceInstanceId();
		ServiceInstance instance = repository.findOne(instanceId);
		if (instance == null) {
			throw new ServiceInstanceDoesNotExistException(instanceId);
		}

		repository.delete(instanceId);
		return new DeleteServiceInstanceResponse();
	}

	@Override
	public UpdateServiceInstanceResponse updateServiceInstance(UpdateServiceInstanceRequest request) {
		System.out.println("updateServiceInstance entering...");
		String instanceId = request.getServiceInstanceId();
		ServiceInstance instance = repository.findOne(instanceId);
		if (instance == null) {
			throw new ServiceInstanceDoesNotExistException(instanceId);
		}

		repository.delete(instanceId);
		ServiceInstance updatedInstance = new ServiceInstance(request);
		repository.save(updatedInstance);
		return new UpdateServiceInstanceResponse();
	}

}
