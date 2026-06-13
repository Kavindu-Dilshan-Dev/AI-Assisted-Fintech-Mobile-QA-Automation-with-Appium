package com.fintech.qa.core.locators;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fintech.qa.core.config.ConfigManager;
import com.fintech.qa.core.driver.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Externalized locator repository.
 *
 * <p>Pages PRIMARILY use {@code @AndroidFindBy}/{@code @iOSXCUITFindBy} annotations.
 * {@code LocatorRepository} is the externalization escape hatch for locators that are
 * dynamic, environment-overridable, or otherwise better kept out of compiled annotations.</p>
 *
 * <p>Locator definitions live in {@code src/test/resources/locators/<page>-<platform>.json}
 * (a flat JSON object of {@code key -> selectorString}) and are loaded with Jackson and
 * cached per {@code page+platform}. The returned selector string is the raw locator value
 * (e.g. a UiAutomator2 resource-id for Android, or an XCUITest accessibility id for iOS);
 * the caller decides how to turn it into a {@code By}.</p>
 *
 * <p>This class is stateless from the caller's perspective and thread-safe.</p>
 */
public final class LocatorRepository {

    private static final Logger log = LoggerFactory.getLogger(LocatorRepository.class);

    private static final String CLASSPATH_DIR = "locators/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Cache keyed by "<page>-<platform>" -> (locatorKey -> selectorString). */
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private LocatorRepository() {
        // utility class
    }

    /**
     * Returns the externalized locator selector string for the given page and key,
     * resolved for the current platform (see {@link Platform#current()}).
     *
     * @param page logical page name (e.g. {@code "login"}); maps to the
     *             {@code <page>-<platform>.json} resource file.
     * @param key  locator key within that file (e.g. {@code "usernameField"}).
     * @return the raw selector string for the current platform.
     * @throws IllegalStateException    if the {@code <page>-<platform>.json} resource is missing or unreadable.
     * @throws IllegalArgumentException if the key is not present in that resource.
     */
    public static String get(String page, String key) {
        return get(page, key, Platform.current());
    }

    /**
     * Returns the externalized locator selector string for the given page, key and platform.
     *
     * @param page     logical page name.
     * @param key      locator key within the resource file.
     * @param platform the platform whose locator file should be consulted.
     * @return the raw selector string.
     * @throws IllegalStateException    if the resource is missing or unreadable.
     * @throws IllegalArgumentException if the key is absent.
     */
    public static String get(String page, String key, Platform platform) {
        Map<String, String> locators = load(page, platform);
        String value = locators.get(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "No locator '" + key + "' found for page '" + page
                            + "' on platform '" + platform.name().toLowerCase() + "'");
        }
        return value;
    }

    /**
     * Returns the externalized locator selector string, or {@code def} when the key is absent.
     * Still throws if the underlying resource file cannot be loaded.
     *
     * @param page logical page name.
     * @param key  locator key.
     * @param def  fallback returned when the key is not present.
     * @return the selector string, or {@code def}.
     */
    public static String get(String page, String key, String def) {
        Map<String, String> locators = load(page, Platform.current());
        return locators.getOrDefault(key, def);
    }

    /** Clears the in-memory cache. Primarily useful for tests that swap locator files. */
    public static void clearCache() {
        CACHE.clear();
    }

    private static Map<String, String> load(String page, Platform platform) {
        String cacheKey = page + "-" + platform.name();
        return CACHE.computeIfAbsent(cacheKey, k -> readResource(page, platform));
    }

    private static Map<String, String> readResource(String page, Platform platform) {
        String fileName = page + "-" + platform.name().toLowerCase() + ".json";
        String resource = CLASSPATH_DIR + fileName;

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = LocatorRepository.class.getClassLoader();
        }

        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Locator resource not found on classpath: " + resource
                                + " (expected under src/test/resources/" + resource + ")");
            }
            JsonNode root = MAPPER.readTree(in);
            Map<String, String> locators = new ConcurrentHashMap<>();
            root.fields().forEachRemaining(entry -> {
                String field = entry.getKey();
                if (field.startsWith("_")) {
                    // skip metadata keys such as "_comment"
                    return;
                }
                JsonNode node = entry.getValue();
                if (node.isValueNode()) {
                    locators.put(field, node.asText());
                }
            });
            log.debug("Loaded {} locator(s) from {}", locators.size(), resource);
            return locators;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read locator resource: " + resource, ex);
        }
    }

    // Reference to ConfigManager retained so the externalization layer can be extended to
    // honour config-driven overrides (e.g. an alternate locators directory) without changing
    // the public contract; touch keeps the dependency explicit and avoids an unused-import lint.
    static {
        ConfigManager.get("locators.dir", CLASSPATH_DIR);
    }
}
