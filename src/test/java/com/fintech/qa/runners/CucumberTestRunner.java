package com.fintech.qa.runners;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * JUnit Platform suite that discovers and runs the Cucumber feature files via the
 * {@code cucumber} test engine.
 *
 * <p>All Cucumber configuration (glue packages, plugins, publish settings) is supplied
 * declaratively through {@code src/test/resources/junit-platform.properties}; this class
 * intentionally has an empty body so the wiring lives in one place. Feature files are
 * resolved from the {@code features} classpath resource
 * ({@code src/test/resources/features}).</p>
 *
 * <p>The single Extent reporting engine is driven by the grasshopper
 * {@code ExtentCucumberAdapter} plugin (configured by {@code extent.properties}); no second
 * report engine is created here.</p>
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
public class CucumberTestRunner {
    // Intentionally empty: configuration is provided via junit-platform.properties.
}
