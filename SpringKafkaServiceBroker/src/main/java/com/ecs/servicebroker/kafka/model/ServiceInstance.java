package com.ecs.servicebroker.kafka.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.springframework.cloud.servicebroker.exception.ServiceBrokerInvalidParametersException;
import org.springframework.cloud.servicebroker.model.CreateServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.DeleteServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.GetLastServiceOperationResponse;
import org.springframework.cloud.servicebroker.model.OperationState;
import org.springframework.cloud.servicebroker.model.UpdateServiceInstanceRequest;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Data;

@Entity
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE)
@Data
public class ServiceInstance {

	@Id
	@JsonSerialize
	@JsonProperty("service_instance_id")
	private String serviceInstanceId;
	
	@JsonSerialize
	@JsonProperty("service_id")
	private String serviceDefinitionId;
	
	@JsonSerialize
	@JsonProperty("plan_id")
	private String planId;
	
	@JsonSerialize
	@JsonProperty("organization_guid")
	private String organizationGuid;
	
	@JsonSerialize
	@JsonProperty("space_guid")
	private String spaceGuid;
	
	@JsonSerialize
	@JsonProperty("dashboard_url")
	private String dashboardUrl;
	
	@JsonSerialize
	@JsonProperty("credentials")
	private String credentials;

	@Transient
	@JsonSerialize
	@JsonProperty("last_operation")
	private GetLastServiceOperationResponse lastOperation;
	
	@JsonIgnore
	private boolean async;
	
	private ServiceInstance() {}
	
	
	/**
	 * Create a ServiceInstance from a create request. If fields 
	 * are not present in the request they will remain null in the  
	 * ServiceInstance.
	 * @param request containing details of ServiceInstance
	 */
	public ServiceInstance(CreateServiceInstanceRequest request) {
		this.serviceDefinitionId = request.getServiceDefinitionId();
		this.planId = request.getPlanId();
		this.organizationGuid = request.getOrganizationGuid();
		this.spaceGuid = request.getSpaceGuid();
		this.serviceInstanceId = request.getServiceInstanceId();
		this.lastOperation = new GetLastServiceOperationResponse()
				.withOperationState(OperationState.IN_PROGRESS)
				.withDescription("Provisioning");

		try {
			this.credentials = new ObjectMapper().writeValueAsString(request.getParameters());
		} catch (Exception e) {
			e.printStackTrace();
			throw new ServiceBrokerInvalidParametersException(e);
		}
	}
	
	/**
	 * Create a ServiceInstance from a delete request. If fields 
	 * are not present in the request they will remain null in the 
	 * ServiceInstance.
	 * @param request containing details of ServiceInstance
	 */
	public ServiceInstance(DeleteServiceInstanceRequest request) {
		this.serviceInstanceId = request.getServiceInstanceId();
		this.planId = request.getPlanId();
		this.serviceDefinitionId = request.getServiceDefinitionId();
		this.lastOperation = new GetLastServiceOperationResponse()
				.withOperationState(OperationState.IN_PROGRESS)
				.withDescription("Deprovisioning");
	}
	
	/**
	 * Create a service instance from an update request. If fields
	 * are not present in the request they will remain null in the 
	 * ServiceInstance.
	 * @param request containing details of ServiceInstance
	 */
	public ServiceInstance(UpdateServiceInstanceRequest request) {
		this.serviceInstanceId = request.getServiceInstanceId();
		this.planId = request.getPlanId();
		this.lastOperation = new GetLastServiceOperationResponse()
				.withOperationState(OperationState.IN_PROGRESS)
				.withDescription("Updating");
	}
	
	public String getServiceInstanceId() {
		return serviceInstanceId;
	}

	public String getServiceDefinitionId() {
		return serviceDefinitionId;
	}

	public String getPlanId() {
		return planId;
	}

	public String getOrganizationGuid() {
		return organizationGuid;
	}

	public String getSpaceGuid() {
		return spaceGuid;
	}

	public String getDashboardUrl() {
		return dashboardUrl;
	}

	public boolean isAsync() {
		return async;
	}

	public ServiceInstance and() {
		return this;
	}

	public ServiceInstance withLastOperation(GetLastServiceOperationResponse lastOperation) {
		this.lastOperation = lastOperation;
		return this;
	}

	public ServiceInstance withDashboardUrl(String dashboardUrl) {
		this.dashboardUrl = dashboardUrl;
		return this;
	}

	public ServiceInstance withAsync(boolean async) {
		this.async = async;
		return this;
	}

	public GetLastServiceOperationResponse getServiceInstanceLastOperation() {
		return lastOperation;
	}
	
}