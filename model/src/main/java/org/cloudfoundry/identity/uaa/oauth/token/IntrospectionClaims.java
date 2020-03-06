/*
 * ******************************************************************************
 *       Cloud Foundry Copyright (c) [2009-2015] Pivotal Software, Inc. All Rights Reserved.
 *
 *       This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *       You may not use this product except in compliance with the License.
 *
 *       This product includes a number of subcomponents with
 *       separate copyright notices and license terms. Your use of these
 *       subcomponents is subject to the terms and conditions of the
 *       subcomponent's license, as noted in the LICENSE file.
 * ******************************************************************************
 */

package org.cloudfoundry.identity.uaa.oauth.token;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class IntrospectionClaims extends Claims {

	@JsonProperty("active")
	private boolean active;
	@JsonProperty("scope")
	private String scopes;

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getScopes() {
		return this.scopes;
	}

	@JsonProperty("scope")
	public void setScopes(List<String> scopes) {
		if (scopes == null)
			this.scopes = null;
		this.scopes = String.join(" ", scopes);
	}
}
