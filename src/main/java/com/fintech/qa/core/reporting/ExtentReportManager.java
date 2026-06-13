package com.fintech.qa.core.reporting;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter;
import com.fintech.qa.core.security.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin facade over the grasshopper {@link ExtentCucumberAdapter}, which is the
 * <strong>single</strong> Extent reporting engine for the framework (configured by
 * {@code src/test/resources/extent.properties}).
 *
 * <p>This class intentionally does <em>not</em> create its own
 * {@code com.aventstack.extentreports.ExtentReports} instance. All log lines and
 * screenshot attachments are routed into the report node that the adapter is
 * currently maintaining for the executing Cucumber step, via
 * {@link ExtentCucumberAdapter#addTestStepLog(String)},
 * {@link ExtentCucumberAdapter#addTestStepScreenCaptureFromPath(String)} and
 * {@link ExtentCucumberAdapter#getCurrentStep()}.</p>
 *
 * <p><strong>Compliance:</strong> every piece of text that reaches the report is
 * first passed through {@link MaskingUtil#mask(String)} so that PAN, CVV, OTP,
 * account numbers, IBANs and bearer/JWT tokens can never be persisted in the
 * HTML report. Screenshot <em>paths</em> are also masked before being attached;
 * the screenshot pixels themselves are protected upstream by
 * {@link ScreenshotUtil}'s sensitive-skip variant.</p>
 *
 * <p>All methods are null-safe: a {@code null} message is treated as an empty
 * string, and reporting failures (e.g. when no Cucumber step is active, such as
 * during unit execution) are swallowed and logged rather than propagated, so a
 * reporting hiccup never fails a test.</p>
 */
public final class ExtentReportManager {

    private static final Logger log = LoggerFactory.getLogger(ExtentReportManager.class);

    private ExtentReportManager() {
        // Static facade; not instantiable.
    }

    /**
     * Logs an informational line into the current report step.
     *
     * @param message the message to log; masked before it reaches the report
     */
    public static void logInfo(String message) {
        String safe = MaskingUtil.mask(nullToEmpty(message));
        try {
            ExtentCucumberAdapter.addTestStepLog(safe);
        } catch (RuntimeException e) {
            log.warn("ExtentReportManager.logInfo: could not write to report step", e);
        }
    }

    /**
     * Logs a passing assertion/checkpoint into the current report step.
     *
     * <p>The text is routed through the adapter's step log; when an Extent step
     * node is available it is additionally flagged with {@link Status#PASS} so
     * the line renders with pass styling in the Spark report. Both paths operate
     * on the adapter's single engine&nbsp;&mdash; no second engine is created.</p>
     *
     * @param message the message to log; masked before it reaches the report
     */
    public static void logPass(String message) {
        String safe = MaskingUtil.mask(nullToEmpty(message));
        if (!logWithStatus(Status.PASS, safe)) {
            // Fall back to a plain step log if no Extent step node is active.
            try {
                ExtentCucumberAdapter.addTestStepLog(safe);
            } catch (RuntimeException e) {
                log.warn("ExtentReportManager.logPass: could not write to report step", e);
            }
        }
    }

    /**
     * Logs a failing assertion/checkpoint into the current report step.
     *
     * <p>The text is routed through the adapter's step log; when an Extent step
     * node is available it is additionally flagged with {@link Status#FAIL} so
     * the line renders with fail styling in the Spark report. Both paths operate
     * on the adapter's single engine&nbsp;&mdash; no second engine is created.</p>
     *
     * @param message the message to log; masked before it reaches the report
     */
    public static void logFail(String message) {
        String safe = MaskingUtil.mask(nullToEmpty(message));
        if (!logWithStatus(Status.FAIL, safe)) {
            try {
                ExtentCucumberAdapter.addTestStepLog(safe);
            } catch (RuntimeException e) {
                log.warn("ExtentReportManager.logFail: could not write to report step", e);
            }
        }
    }

    /**
     * Attaches a screenshot (already written to disk) to the current report step.
     *
     * <p>A {@code null}/blank path is ignored (this lets callers pass the result
     * of {@link ScreenshotUtil#capture(String, boolean)} directly, which returns
     * {@code null} when a sensitive screen was deliberately skipped). The path is
     * masked before being attached, since file names can themselves leak
     * identifiers.</p>
     *
     * @param path the on-disk path of the screenshot to attach; if {@code null}
     *             or blank, the call is a no-op
     */
    public static void attachScreenshot(String path) {
        if (path == null || path.isBlank()) {
            log.debug("ExtentReportManager.attachScreenshot: no path supplied (skipped/sensitive); nothing attached");
            return;
        }
        String safePath = MaskingUtil.mask(path);
        try {
            ExtentCucumberAdapter.addTestStepScreenCaptureFromPath(safePath);
        } catch (Exception e) {
            // addTestStepScreenCaptureFromPath declares IOException; also guard
            // against the "no active step" runtime case.
            log.warn("ExtentReportManager.attachScreenshot: could not attach screenshot '{}'", safePath, e);
        }
    }

    /**
     * Logs {@code message} against the adapter's current step node with an
     * explicit {@link Status}, using the single Extent engine owned by the
     * adapter.
     *
     * @return {@code true} if the line was written to an active step node,
     *         {@code false} if no step node was available (caller should fall back)
     */
    private static boolean logWithStatus(Status status, String maskedMessage) {
        try {
            ExtentTest step = ExtentCucumberAdapter.getCurrentStep();
            if (step == null) {
                return false;
            }
            step.log(status, maskedMessage);
            return true;
        } catch (RuntimeException e) {
            log.warn("ExtentReportManager: could not log status {} to report step", status, e);
            return false;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
