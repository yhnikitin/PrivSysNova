package pt.unl.fct.di.syspriv.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Properties PROPERTIES = new Properties();

    // Loads from src/main/resources
    static {
        loadFromClasspath("config.props");
    }

    public static void loadFromClasspath(String fileName) {
        try (InputStream input = ConfigLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                System.err.println("[ConfigLoader] Warning: File '" + fileName + "' not found in classpath.");
                return;
            }
            PROPERTIES.load(input);
            System.out.println("[ConfigLoader] Loaded " + PROPERTIES.size() + " properties from classpath: " + fileName);
        } catch (IOException e) {
            System.err.println("[ConfigLoader] Failed to load properties from classpath: " + fileName);
            e.printStackTrace();
        }
    }

    public static String getProperty(String key) {
        return PROPERTIES.getProperty(key);
    }

    // Use this for a default fallback.
    public static String getProperty(String key, String defaultValue) {
        return PROPERTIES.getProperty(key, defaultValue);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) return defaultValue;
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1");
    }

    // Everything below this line is useful for future labs.
    // -----------------------------------------------------------------------------------------
    public static int getIntProperty(String key, int defaultValue) {
        String value = getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("[ConfigLoader] Invalid integer for key '" + key + "': " + value);
            return defaultValue;
        }
    }

    public static double getDoubleProperty(String key, double defaultValue) {
        String value = getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            System.err.println("[ConfigLoader] Invalid double for key '" + key + "': " + value);
            return defaultValue;
        }
    }

}
