package com.fintech.qa.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

/**
 * Centralised, layered configuration access for the framework.
 *
 * <p>Values are resolved with a fixed precedence (later layers win):</p>
 * <ol>
 *   <li>{@code src/test/resources/config/config.properties} (non-secret defaults, loaded once);</li>
 *   <li>JVM system properties ({@code -Dkey=value});</li>
 *   <li>environment variables, where the dotted config key is mapped to
 *       {@code UPPER_SNAKE_CASE} (e.g. {@code otp.api.token} &rarr; {@code OTP_API_TOKEN}).</li>
 * </ol>
 *
 * <p><strong>Compliance:</strong> secrets (passwords, API tokens, device-farm keys) must come
 * ONLY from the environment. {@code config.properties} holds non-secret defaults exclusively.
 * Because the environment layer has the highest precedence, an environment variable always
 * overrides any value present in the properties file or a system property.</p>
 *
 * <p>This class is a stateless, thread-safe static facade and cannot be instantiated.</p>
 */
public final class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    /** Classpath location of the non-secret defaults file. */
    private static final String CONFIG_RESOURCE = "config/config.properties";

    /** Immutable snapshot of the file-based defaults (lowest precedence). */
    private static final Properties FILE_DEFAULTS = loadFileDefaults();

    private ConfigManager() {
        throw new AssertionError("ConfigManager is a static utility and must not be instantiated");
    }

    /**
     * Returns the value for {@code key}, or {@code null} if it is not set in any layer.
     *
     * @param key the dotted configuration key (e.g. {@code appium.server.url})
     * @return the resolved value, or {@code null} when absent
     */
    public static String get(String key) {
        return get(key, null);
    }

    /**
     * Returns the value for {@code key}, or {@code def} if it is not set in any layer.
     *
     * <p>Resolution order: environment variable (highest) &rarr; system property &rarr;
     * {@code config.properties} &rarr; {@code def}.</p>
     *
     * @param key the dotted configuration key
     * @param def the fallback value returned when the key is absent everywhere
     * @return the resolved value, or {@code def}
     */
    public static String get(String key, String def) {
        if (key == null || key.isBlank()) {
            return def;
        }

        // 3. Environment variables win (secrets live here).
        String envValue = System.getenv(toEnvKey(key));
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        // 2. JVM system properties.
        String sysValue = System.getProperty(key);
        if (sysValue != null && !sysValue.isBlank()) {
            return sysValue;
        }

        // 1. File defaults.
        String fileValue = FILE_DEFAULTS.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }

        return def;
    }

    /**
     * Returns the value for {@code key} parsed as an {@code int}, or {@code def} if it is
     * absent or not a valid integer.
     *
     * @param key the dotted configuration key
     * @param def the fallback value
     * @return the parsed integer, or {@code def}
     */
    public static int getInt(String key, int def) {
        String value = get(key, null);
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Config key '{}' value '{}' is not a valid int; using default {}", key, value, def);
            return def;
        }
    }

    /**
     * Returns the value for {@code key} parsed as a {@code boolean}, or {@code def} if it is
     * absent. Accepts {@code true}/{@code false} case-insensitively.
     *
     * @param key the dotted configuration key
     * @param def the fallback value
     * @return the parsed boolean, or {@code def}
     */
    public static boolean getBoolean(String key, boolean def) {
        String value = get(key, null);
        if (value == null) {
            return def;
        }
        String trimmed = value.trim();
        if ("true".equalsIgnoreCase(trimmed)) {
            return true;
        }
        if ("false".equalsIgnoreCase(trimmed)) {
            return false;
        }
        log.warn("Config key '{}' value '{}' is not a valid boolean; using default {}", key, value, def);
        return def;
    }

    /**
     * Maps a dotted config key to its environment-variable form: upper-cased with every
     * non-alphanumeric character replaced by {@code _} (e.g. {@code otp.api.token} &rarr;
     * {@code OTP_API_TOKEN}).
     */
    private static String toEnvKey(String key) {
        return key.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    /** Loads {@code config/config.properties} from the classpath once; missing file is tolerated. */
    private static Properties loadFileDefaults() {
        Properties props = new Properties();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ConfigManager.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(CONFIG_RESOURCE)) {
            if (in == null) {
                log.warn("Config resource '{}' not found on classpath; relying on system properties "
                        + "and environment variables only", CONFIG_RESOURCE);
            } else {
                props.load(in);
                log.debug("Loaded {} default config entries from '{}'", props.size(), CONFIG_RESOURCE);
            }
        } catch (Exception e) {
            log.warn("Failed to load config resource '{}'; continuing with system/env layers only",
                    CONFIG_RESOURCE, e);
        }
        return props;
    }
}
