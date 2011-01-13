package test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public abstract class JavaUtils {
    public static Properties loadProperties(InputStream stream)
	    throws IOException {
	Properties properties = new Properties();
	if (stream != null) {
	    try {
		properties.load(stream);
	    } finally {
		stream.close();
	    }
	}

	return properties;
    }
}
