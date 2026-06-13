package com.fintech.qa.hooks;

import com.fintech.qa.core.context.ScenarioContext;
import com.fintech.qa.core.driver.DriverFactory;
import com.fintech.qa.core.driver.DriverManager;
import com.fintech.qa.core.reporting.ExtentReportManager;
import com.fintech.qa.core.reporting.ScreenshotUtil;
import io.appium.java_client.AppiumDriver;
import io.cucumber.java.After;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber lifecycle hooks for the synthetic fintech wallet suite.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li><b>{@code @Before}</b> &mdash; clears {@link ScenarioContext} so each scenario starts with
 *       a clean context (prevents leakage from a prior scenario on a reused/pooled thread), then
 *       creates a fresh, thread-confined {@link AppiumDriver} via {@link DriverFactory} and binds
 *       it through {@link DriverManager#setDriver(AppiumDriver)} (parallel-safe).</li>
 *   <li><b>{@code @AfterStep}</b> &mdash; on a failing step, captures a screenshot and attaches it to
 *       the current Extent report node. All attached text/paths are masked by
 *       {@link ExtentReportManager} before reaching the report.</li>
 *   <li><b>{@code @After}</b> &mdash; on a failed scenario, attaches a final failure screenshot, then
 *       always quits the driver and clears the thread binding via {@link DriverManager#quitDriver()};
 *       also clears {@link ScenarioContext} in the {@code finally} block to prevent thread-local
 *       leakage on pooled worker threads between scenarios.</li>
 * </ul>
 *
 * <p><strong>Compliance:</strong> screenshots are never taken of screens the scenario flags as
 * sensitive (handled at the step level via {@link ScreenshotUtil#capture(String, boolean)}); the
 * failure screenshots here use the non-sensitive path, and any human-readable text/paths are masked
 * by {@link ExtentReportManager}. Secrets are never logged or embedded in scenario names.</p>
 */
public class CucumberHooks {

    private static final Logger log = LoggerFactory.getLogger(CucumberHooks.class);

    /**
     * Resets per-scenario state and creates a fresh Appium session for the scenario about to run.
     *
     * <p>{@link ScenarioContext#clear()} is called first so this scenario always begins with an
     * empty context, regardless of whether a prior scenario on the same pooled worker thread left
     * stale entries (defensive reset complementing the {@code @After} clear below).</p>
     *
     * @param scenario the Cucumber scenario metadata (used only for diagnostic logging)
     */
    @Before(order = 0)
    public void setUp(Scenario scenario) {
        // Reset the per-scenario context before driver creation so no prior scenario's state leaks in.
        ScenarioContext.clear();
        log.info("Starting scenario: {}", scenario.getName());
        AppiumDriver driver = DriverFactory.createDriver();
        DriverManager.setDriver(driver);
        ExtentReportManager.logInfo("Driver session started for scenario: " + scenario.getName());
    }

    /**
     * After each step, if the step failed, capture and attach a screenshot to the report.
     *
     * @param scenario the Cucumber scenario metadata (exposes the running failure state)
     */
    @AfterStep
    public void afterStep(Scenario scenario) {
        if (!scenario.isFailed()) {
            return;
        }
        log.warn("Step failed in scenario '{}'; capturing screenshot", scenario.getName());
        String path = ScreenshotUtil.capture("step-failure-" + safeName(scenario));
        if (path != null) {
            ExtentReportManager.logFail("Step failed; attaching screenshot");
            ExtentReportManager.attachScreenshot(path);
        }
    }

    /**
     * Cleans up the scenario: attaches a final failure screenshot if the scenario failed,
     * then always quits the driver and clears the thread-local binding.
     *
     * @param scenario the Cucumber scenario metadata
     */
    @After(order = 0)
    public void tearDown(Scenario scenario) {
        try {
            if (scenario.isFailed()) {
                String path = ScreenshotUtil.capture("scenario-failure-" + safeName(scenario));
                if (path != null) {
                    ExtentReportManager.logFail("Scenario failed: " + scenario.getName());
                    ExtentReportManager.attachScreenshot(path);
                }
            } else {
                ExtentReportManager.logPass("Scenario passed: " + scenario.getName());
            }
        } finally {
            DriverManager.quitDriver();
            // Clear per-scenario context so no thread-local state leaks to the next scenario on a
            // reused/pooled worker thread. Uses the "unique data, no delete" strategy — no entity
            // cleanup is performed here; context entries are simply discarded.
            ScenarioContext.clear();
            log.info("Finished scenario: {} [{}]", scenario.getName(), scenario.getStatus());
        }
    }

    /**
     * Produces a file-name-safe fragment from the scenario name for screenshot naming.
     * Never includes user-entered values or secrets.
     */
    private static String safeName(Scenario scenario) {
        String name = scenario.getName();
        if (name == null || name.isBlank()) {
            return "scenario";
        }
        return name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
