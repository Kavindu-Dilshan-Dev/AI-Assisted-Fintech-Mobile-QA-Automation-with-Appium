package com.fintech.qa.core.context;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fintech.qa.core.security.MaskingUtil;

/**
 * Thread-confined key/value store for sharing per-scenario state across step-definition classes.
 *
 * <p>Cucumber instantiates a fresh set of step-definition objects per scenario but offers no
 * built-in, dependency-injection-free way to pass state between them. This class fills that gap:
 * a generated unique entity name produced in one step can be read back in another step living in a
 * different class, enabling re-runnable CRUD tests that rely on a unique-data strategy.</p>
 *
 * <p>State is held in a {@link ThreadLocal} map so that parallel Cucumber scenarios (each running
 * on its own worker thread) never share or clobber one another's data. The map is initialised lazily
 * to a fresh {@link HashMap} per thread. Mirroring the {@code DriverManager} idiom, all members are
 * static and this class cannot be instantiated.</p>
 *
 * <p>The Cucumber {@code @After} hook must call {@link #clear()} so the thread-local map does not
 * leak across pooled worker threads between scenarios. This class deliberately carries no Cucumber
 * dependency; the glue that invokes {@link #clear()} lives in the test-scope hook.</p>
 *
 * <p><strong>PII safety:</strong> values may carry sensitive data (e.g. account numbers), so this
 * class never logs raw values. Diagnostic logging is at {@code debug} level and includes only the
 * key; on the rare occasion a value is referenced in a log message it is routed through
 * {@link MaskingUtil#mask(String)}.</p>
 */
public final class ScenarioContext {

    private static final Logger log = LoggerFactory.getLogger(ScenarioContext.class);

    /** Per-thread state map for parallel-safe, per-scenario sharing. */
    private static final ThreadLocal<Map<String, Object>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    private ScenarioContext() {
        throw new AssertionError("ScenarioContext is a static utility and must not be instantiated");
    }

    /**
     * Stores a value under the given key for the current scenario (current thread).
     *
     * <p>An existing value under the same key is overwritten.</p>
     *
     * @param key   the lookup key; must not be {@code null}
     * @param value the value to store; may be {@code null}
     * @throws IllegalArgumentException if {@code key} is {@code null}
     */
    public static void put(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        CONTEXT.get().put(key, value);
        log.debug("ScenarioContext put key '{}' on thread '{}'",
                key, Thread.currentThread().getName());
    }

    /**
     * Retrieves the value stored under the given key for the current scenario (current thread),
     * cast to the caller's expected type.
     *
     * <p>The cast is unchecked: the caller is responsible for requesting a type compatible with
     * whatever was {@link #put(String, Object) put} under the key, otherwise a
     * {@link ClassCastException} is raised at the assignment site.</p>
     *
     * @param key the lookup key
     * @param <T> the expected value type
     * @return the stored value cast to {@code T}, or {@code null} if no entry exists for the key
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) CONTEXT.get().get(key);
    }

    /**
     * Retrieves the value stored under the given key as a {@link String}.
     *
     * @param key the lookup key
     * @return the stored value's {@link Object#toString()} representation, or {@code null} if no
     *         entry exists for the key (or the stored value itself is {@code null})
     */
    public static String getString(String key) {
        Object value = CONTEXT.get().get(key);
        return value == null ? null : value.toString();
    }

    /**
     * Indicates whether a value is stored under the given key for the current scenario.
     *
     * @param key the lookup key
     * @return {@code true} if an entry exists for the key, {@code false} otherwise
     */
    public static boolean contains(String key) {
        return CONTEXT.get().containsKey(key);
    }

    /**
     * Clears all entries for the current scenario and removes the thread-local map binding.
     *
     * <p>Safe to call when nothing has been stored. Intended to be invoked by the Cucumber
     * {@code @After} hook so that no state leaks across scenarios reusing the same pooled worker
     * thread.</p>
     */
    public static void clear() {
        CONTEXT.get().clear();
        CONTEXT.remove();
        log.debug("Cleared ScenarioContext for thread '{}'", Thread.currentThread().getName());
    }
}
