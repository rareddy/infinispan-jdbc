/*
 * Copyright 2012-2017 the original author or authors.
 *
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
 */
package io.infinispan.data;

import javax.resource.ResourceException;
import javax.resource.cci.Connection;
import javax.resource.cci.ConnectionMetaData;
import javax.resource.cci.Interaction;
import javax.resource.cci.LocalTransaction;
import javax.resource.cci.ResultSetInfo;
import javax.transaction.xa.XAResource;

public abstract class BaseConnection implements Connection {

	public Interaction createInteraction() throws ResourceException {
		throw new ResourceException("This operation not supported"); //$NON-NLS-1$
	}

	public LocalTransaction getLocalTransaction() throws ResourceException {
		return null;
	}

	public ConnectionMetaData getMetaData() throws ResourceException {
		throw new ResourceException("This operation not supported"); //$NON-NLS-1$
	}

	public ResultSetInfo getResultSetInfo() throws ResourceException {
		throw new ResourceException("This operation not supported"); //$NON-NLS-1$
	}
	
	public XAResource getXAResource() throws ResourceException {
		return null;
	}
	
	public boolean isAlive() {
		return true;
	}
	
	public void cleanUp() {
		
	}
}