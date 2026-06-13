package com.fintech.qa.core.driver;

import io.appium.java_client.AppiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-confined registry for the active {@link AppiumDriver}.
 *
 * <p>The driver is held in a {@link ThreadLocal} so that parallel Cucumber scenarios (each
 * running on its own thread) get an isolated session. {@code CucumberHooks} binds a freshly
 * created driver via {@link #setDriver(AppiumDriver)} in a {@code @Before} hook and releases it
 * via {@link #quitDriver()} in {@code @After}; pages, components and steps resolve their driver
 * through {@link #getDriver()}.</p>
 *
 * <p>All methods are static; this class cannot be instantiated.</p>
 */
public final class DriverManager {

    private static final Logger log = LoggerFactory.getLogger(DriverManager.class);

    /** Per-thread driver binding for parallel-safe execution. */
    private static final ThreadLocal<AppiumDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
        throw new AssertionError("DriverManager is a static utility and must not be instantiated");
    }

    /**
     * Returns the {@link AppiumDriver} bound to the current thread.
     *
     * @return the thread-bound driver
     * @throws IllegalStateException if no driver has been set for this thread (i.e. the
     *                               {@code @Before} hook did not run or already cleaned up)
     */
    public static AppiumDriver getDriver() {
        AppiumDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException(
                    "No AppiumDriver bound to the current thread. Ensure the Cucumber @Before hook "
                            + "created a session via DriverManager.setDriver(...).");
        }
        return driver;
    }

    /**
     * Binds the given driver to the current thread.
     *
     * @param driver the driver to bind; must not be {@code null}
     */
    public static void setDriver(AppiumDriver driver) {
        if (driver == null) {
            throw new IllegalArgumentException("driver must not be null");
        }
        DRIVER.set(driver);
        log.debug("Bound AppiumDriver to thread '{}' (sessionId={})",
                Thread.currentThread().getName(), driver.getSessionId());
    }

    /**
     * Resolves the {@link Platform} of the currently bound driver instance.
     *
     * <p>This is derived from the concrete driver type so it always reflects the live session
     * rather than configuration alone.</p>
     *
     * @return the platform of the active session
     * @throws IllegalStateException if no driver is bound, or the driver type is unrecognised
     */
    public static Platform getPlatform() {
        AppiumDriver driver = getDriver();
        if (driver instanceof io.appium.java_client.android.AndroidDriver) {
            return Platform.ANDROID;
        }
        if (driver instanceof io.appium.java_client.ios.IOSDriver) {
            return Platform.IOS;
        }
        // Fall back to configuration if a non-standard driver subclass is in use.
        return Platform.current();
    }

    /**
     * Quits the current thread's driver (if any) and clears the thread binding.
     *
     * <p>Safe to call when no driver is bound. The {@link ThreadLocal} is always removed to
     * prevent leaks across pooled worker threads.</p>
     */
    public static void quitDriver() {
        AppiumDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } catch (RuntimeException e) {
                log.warn("Error quitting AppiumDriver on thread '{}'",
                        Thread.currentThread().getName(), e);
            } finally {
                DRIVER.remove();
                log.debug("Cleared AppiumDriver binding for thread '{}'",
                        Thread.currentThread().getName());
            }
        } else {
            DRIVER.remove();
        }
    }
}
