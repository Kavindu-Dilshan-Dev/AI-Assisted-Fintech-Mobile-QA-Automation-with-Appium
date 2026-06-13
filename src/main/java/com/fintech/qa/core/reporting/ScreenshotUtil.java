package com.fintech.qa.core.reporting;

import com.fintech.qa.core.config.ConfigManager;
import com.fintech.qa.core.driver.DriverManager;
import com.fintech.qa.core.security.MaskingUtil;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Captures device screenshots from the active {@link DriverManager} session and
 * writes them under {@code target/screenshots/}.
 *
 * <p>File names follow the contract pattern {@code <name>-<counter>.png}, where
 * {@code <counter>} is a process-wide monotonically increasing value so that
 * concurrent (parallel) captures never collide. The output directory is read
 * from the {@code screenshot.dir} config key (default {@code target/screenshots})
 * to stay aligned with {@code extent.properties}.</p>
 *
 * <p><strong>Compliance / sensitive screens:</strong> screenshot pixels cannot be
 * masked the way text can, so a banking app may legitimately render a full PAN,
 * CVV or balance on screen. For those screens use
 * {@link #capture(String, boolean)} with {@code sensitive = true}; the capture is
 * deliberately skipped and {@code null} is returned (with a logged note) so the
 * image is never persisted. Returned paths from the non-sensitive method are
 * safe on-disk locations; any human-readable name component is masked via
 * {@link MaskingUtil#mask(String)} before being logged.</p>
 */
public final class ScreenshotUtil {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotUtil.class);

    /** Config key for the screenshot output directory. */
    private static final String DIR_KEY = "screenshot.dir";
    /** Default output directory (kept in sync with extent.properties). */
    private static final String DEFAULT_DIR = "target/screenshots";

    /** Process-wide, thread-safe sequence to keep file names unique under parallel runs. */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private ScreenshotUtil() {
        // Static utility; not instantiable.
    }

    /**
     * Captures a screenshot of the current screen and stores it as
     * {@code <screenshot.dir>/<name>-<counter>.png}.
     *
     * @param name logical name for the screenshot (sanitized for use in a file name)
     * @return the absolute path of the written PNG, or {@code null} if the capture
     *         could not be performed (e.g. no active driver / I/O failure). Failures
     *         are logged, never thrown, so reporting never breaks a test.
     */
    public static String capture(String name) {
        WebDriver driver = DriverManager.getDriver();
        if (driver == null) {
            log.warn("ScreenshotUtil.capture: no active driver; skipping screenshot '{}'",
                    MaskingUtil.mask(name));
            return null;
        }
        if (!(driver instanceof TakesScreenshot)) {
            log.warn("ScreenshotUtil.capture: active driver does not support screenshots; skipping '{}'",
                    MaskingUtil.mask(name));
            return null;
        }

        String fileName = sanitize(name) + "-" + COUNTER.incrementAndGet() + ".png";
        try {
            Path dir = Path.of(ConfigManager.get(DIR_KEY, DEFAULT_DIR));
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);

            java.io.File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(src.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

            String path = target.toAbsolutePath().toString();
            log.info("ScreenshotUtil.capture: saved screenshot to {}", MaskingUtil.mask(path));
            return path;
        } catch (IOException e) {
            log.warn("ScreenshotUtil.capture: failed to write screenshot '{}'",
                    MaskingUtil.mask(fileName), e);
            return null;
        } catch (RuntimeException e) {
            // e.g. WebDriverException if the session died mid-capture.
            log.warn("ScreenshotUtil.capture: driver failed to produce screenshot '{}'",
                    MaskingUtil.mask(fileName), e);
            return null;
        }
    }

    /**
     * Capture variant that is aware of sensitive screens.
     *
     * <p>When {@code sensitive} is {@code true} the capture is intentionally
     * <em>skipped</em> and {@code null} is returned, because image pixels (an
     * on-screen PAN, CVV, OTP or balance) cannot be masked the way text is. A note
     * is logged so the omission is auditable. When {@code sensitive} is
     * {@code false} this delegates to {@link #capture(String)}.</p>
     *
     * @param name      logical name for the screenshot
     * @param sensitive {@code true} if the current screen may show unmaskable
     *                  sensitive data and must not be captured
     * @return the path of the written PNG, or {@code null} when skipped (sensitive)
     *         or on failure
     */
    public static String capture(String name, boolean sensitive) {
        if (sensitive) {
            log.info("ScreenshotUtil.capture: skipping screenshot '{}' - screen flagged sensitive; "
                    + "image pixels cannot be masked, so no capture is taken (compliance)",
                    MaskingUtil.mask(name));
            return null;
        }
        return capture(name);
    }

    /**
     * Reduces an arbitrary logical name to a safe file-name component, replacing
     * any character outside {@code [A-Za-z0-9._-]} with an underscore. A
     * {@code null}/blank name becomes {@code "screenshot"}.
     */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "screenshot";
        }
        String cleaned = name.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() ? "screenshot" : cleaned;
    }
}
