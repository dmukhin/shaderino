package test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

public class UniformParameters {
    private final Map<String, Object> values = new HashMap<String, Object>();

    public UniformParameters() {
    }

    public UniformParameters(Properties properties) {
	setParameters(properties);
    }

    public UniformParameters(UniformParameters parameters,
	    Properties defaultValues) {
	this(defaultValues);
	setParameters(parameters);
    }

    public Map<String, Object> getValues() {
	return values;
    }

    public void setParameter(String name, Object value) {
	getValues().put(name, value);
    }

    public void setParameter(String name, String value) {
	try {
	    if (value.matches(".*[\\.eE].*")) {
		setParameter(name, Float.parseFloat(value));
	    } else {
		setParameter(name, Integer.parseInt(value));
	    }
	} catch (NumberFormatException exception) {
	    throw new IllegalArgumentException("wrong format for parameter "
		    + name, exception);
	}
    }

    public void setParameters(UniformParameters parameters) {
	getValues().putAll(parameters.getValues());
    }

    public void setParameters(Properties properties) {
	for (Entry<Object, Object> entry : properties.entrySet()) {
	    setParameter((String) entry.getKey(), (String) entry.getValue());
	}
    }

}