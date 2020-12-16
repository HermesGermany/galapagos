package com.hermesworld.ais.galapagos.notifications;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class NotificationParams {

	private String templateName;

	private Map<String, Object> variables = new HashMap<>();

	public NotificationParams(String templateName) {
		this.templateName = templateName;
	}

	public void addVariable(String name, Object value) {
		variables.put(name, value);
	}

	public String getTemplateName() {
		 return templateName;
	}

	public Map<String, Object> getVariables() {
		 return Collections.unmodifiableMap(variables);
	}

}
