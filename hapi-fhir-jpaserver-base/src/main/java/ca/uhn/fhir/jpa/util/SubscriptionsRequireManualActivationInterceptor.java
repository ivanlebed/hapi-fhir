package ca.uhn.fhir.jpa.util;

/*
 * #%L
 * HAPI FHIR JPA Server
 * %%
 * Copyright (C) 2014 - 2015 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import org.hl7.fhir.instance.model.api.IIdType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import ca.uhn.fhir.jpa.dao.FhirResourceDaoSubscriptionDstu2;
import ca.uhn.fhir.jpa.dao.IFhirResourceDao;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.dstu2.resource.Subscription;
import ca.uhn.fhir.model.dstu2.valueset.SubscriptionStatusEnum;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.method.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.rest.server.interceptor.InterceptorAdapter;
import net.sourceforge.cobertura.CoverageIgnore;

/**
 * Interceptor which requires newly created {@link Subscription subscriptions} to be in
 * {@link SubscriptionStatusEnum#REQUESTED} state and prevents clients from changing the status.
 */
public class SubscriptionsRequireManualActivationInterceptor extends InterceptorAdapter {

	public static final ResourceMetadataKeyEnum<Object> ALLOW_STATUS_CHANGE = new ResourceMetadataKeyEnum<Object>(FhirResourceDaoSubscriptionDstu2.class.getName() + "_ALLOW_STATUS_CHANGE") {
		private static final long serialVersionUID = 1;

		@CoverageIgnore
		@Override
		public Object get(IResource theResource) {
			throw new UnsupportedOperationException();
		}

		@CoverageIgnore
		@Override
		public void put(IResource theResource, Object theObject) {
			throw new UnsupportedOperationException();
		}
	};

	@Autowired
	@Qualifier("mySubscriptionDaoDstu2")
	private IFhirResourceDao<Subscription> myDao;

	@Override
	public void incomingRequestPreHandled(RestOperationTypeEnum theOperation, ActionRequestDetails theProcessedRequest) {
		switch (theOperation) {
		case CREATE:
		case UPDATE:
			if (theProcessedRequest.getResourceType().equals("Subscription")) {
				verifyStatusOk(theProcessedRequest);
			}
		default:
			break;
		}
	}

	public void setDao(IFhirResourceDao<Subscription> theDao) {
		myDao = theDao;
	}

	private void verifyStatusOk(ActionRequestDetails theRequestDetails) {
		Subscription subscription = (Subscription) theRequestDetails.getResource();
		;
		SubscriptionStatusEnum newStatus = subscription.getStatusElement().getValueAsEnum();

		if (newStatus == SubscriptionStatusEnum.REQUESTED || newStatus == SubscriptionStatusEnum.OFF) {
			return;
		}

		IIdType requestId = theRequestDetails.getId();
		if (requestId != null && requestId.hasIdPart()) {
			Subscription existing;
			try {
				existing = myDao.read(requestId);
				SubscriptionStatusEnum existingStatus = existing.getStatusElement().getValueAsEnum();
				if (existingStatus != newStatus) {
					throw new UnprocessableEntityException("Subscription.status can not be changed from " + describeStatus(existingStatus) + " to " + describeStatus(newStatus));
				}
			} catch (ResourceNotFoundException e) {
				if (newStatus != SubscriptionStatusEnum.REQUESTED) {
					throw new UnprocessableEntityException("Subscription.status must be '" + SubscriptionStatusEnum.REQUESTED.getCode() + "' on a newly created subscription");
				}
			}
		} else {
			if (newStatus != SubscriptionStatusEnum.REQUESTED) {
				throw new UnprocessableEntityException("Subscription.status must be '" + SubscriptionStatusEnum.REQUESTED.getCode() + "' on a newly created subscription");
			}
		}
	}

	private String describeStatus(SubscriptionStatusEnum existingStatus) {
		String existingStatusString;
		if (existingStatus != null) {
			existingStatusString = '\'' + existingStatus.getCode() + '\'';
		} else {
			existingStatusString = "null";
		}
		return existingStatusString;
	}

}