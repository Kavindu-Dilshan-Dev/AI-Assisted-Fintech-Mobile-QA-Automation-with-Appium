package com.fintech.qa.core.base;

import com.fintech.qa.core.config.ConfigManager;
import com.fintech.qa.core.driver.DriverManager;
import com.fintech.qa.core.security.MaskingUtil;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Pause;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract base for every page object and UI component in the framework.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Resolves the thread-bound {@link AppiumDriver} via {@link DriverManager}.</li>
 *   <li>Configures an explicit {@link WebDriverWait} and PageFactory-style element
 *       injection using {@link AppiumFieldDecorator} (so {@code @AndroidFindBy} /
 *       {@code @iOSXCUITFindBy} annotations work in subclasses).</li>
 *   <li>Provides the ONLY sanctioned interaction helpers ({@code tap}, {@code typeText},
 *       waits, swipe, scroll, webview switching, accessibility inspection).</li>
 * </ul>
 *
 * <p><b>Framework rule:</b> only this class is permitted to call
 * {@code driver.findElement(...)}. Pages, components and step definitions must
 * interact exclusively through the helpers exposed here.</p>
 *
 * <p>All sensitive text (typed values, accessibility descriptions) is passed
 * through {@link MaskingUtil} before it reaches a log line.</p>
 */
public abstract class BasePage {

    /** Shared SLF4J logger for all page objects/components. */
    protected static final Logger log = LoggerFactory.getLogger(BasePage.class);

    /** Thread-bound Appium driver for the current session. */
    protected final AppiumDriver driver;

    /** Explicit wait, sized from {@code explicit.wait.seconds} (default 20s). */
    protected final WebDriverWait wait;

    /**
     * Initialises the page: binds the driver, builds the explicit wait, and
     * decorates {@code @AndroidFindBy}/{@code @iOSXCUITFindBy} fields with an
     * implicit element-lookup timeout sourced from configuration.
     */
    protected BasePage() {
        this.driver = DriverManager.getDriver();
        this.wait = new WebDriverWait(
                driver,
                Duration.ofSeconds(ConfigManager.getInt("explicit.wait.seconds", 20)));
        PageFactory.initElements(
                new AppiumFieldDecorator(
                        driver,
                        Duration.ofSeconds(ConfigManager.getInt("implicit.wait.seconds", 10))),
                this);
    }

    // ------------------------------------------------------------------
    //  Wait helpers
    // ------------------------------------------------------------------

    /**
     * Waits until the given element is visible.
     *
     * @param element a PageFactory-proxied or already-resolved element
     * @return the same element once visible
     */
    protected WebElement waitForVisible(WebElement element) {
        return wait.until(ExpectedConditions.visibilityOf(element));
    }

    /**
     * Waits until an element matching the locator is visible.
     *
     * @param locator the Selenium/Appium {@link By} locator
     * @return the visible element
     */
    protected WebElement waitForVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Waits until the given element is clickable.
     *
     * @param element the element to wait on
     * @return the same element once clickable
     */
    protected WebElement waitForClickable(WebElement element) {
        return wait.until(ExpectedConditions.elementToBeClickable(element));
    }

    // ------------------------------------------------------------------
    //  Interaction helpers
    // ------------------------------------------------------------------

    /**
     * Taps (clicks) an element once it is clickable.
     *
     * @param element the element to tap
     */
    protected void tap(WebElement element) {
        waitForClickable(element).click();
    }

    /**
     * Types text into an element after clearing it, logging only a masked
     * representation of the value (never the raw secret/PAN/OTP).
     *
     * @param element the input element
     * @param text    the value to type (may be sensitive)
     */
    protected void typeText(WebElement element, String text) {
        WebElement field = waitForVisible(element);
        field.clear();
        field.sendKeys(text);
        log.info("Typed '{}' into element [{}]", MaskingUtil.mask(text), describe(element));
    }

    /**
     * Returns the visible text of an element.
     *
     * @param element the element to read
     * @return the element's text
     */
    protected String getText(WebElement element) {
        return waitForVisible(element).getText();
    }

    /**
     * Returns {@code true} if the element is currently displayed, without
     * throwing if it is absent.
     *
     * @param element the element to probe
     * @return whether the element is displayed
     */
    protected boolean isDisplayed(WebElement element) {
        try {
            return element.isDisplayed();
        } catch (org.openqa.selenium.NoSuchElementException
                 | org.openqa.selenium.StaleElementReferenceException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  Gestures (W3C Actions / mobile gestures)
    // ------------------------------------------------------------------

    /**
     * Performs a swipe across the centre band of the screen in the given
     * direction using a single-finger W3C {@link PointerInput} gesture.
     *
     * @param direction the swipe direction
     */
    protected void swipe(SwipeDirection direction) {
        Dimension size = driver.manage().window().getSize();
        int width = size.getWidth();
        int height = size.getHeight();

        // Centre line and a 25%..75% travel band keep the gesture inside the
        // viewport and clear of system bars / notches on both platforms.
        int midX = width / 2;
        int midY = height / 2;
        int left = (int) (width * 0.25);
        int right = (int) (width * 0.75);
        int top = (int) (height * 0.25);
        int bottom = (int) (height * 0.75);

        int startX;
        int startY;
        int endX;
        int endY;

        switch (direction) {
            case UP -> {
                startX = midX; startY = bottom; endX = midX; endY = top;
            }
            case DOWN -> {
                startX = midX; startY = top; endX = midX; endY = bottom;
            }
            case LEFT -> {
                startX = right; startY = midY; endX = left; endY = midY;
            }
            case RIGHT -> {
                startX = left; startY = midY; endX = right; endY = midY;
            }
            default -> throw new IllegalArgumentException("Unsupported swipe direction: " + direction);
        }

        performDrag(startX, startY, endX, endY, Duration.ofMillis(600));
        log.debug("Swiped {} from ({},{}) to ({},{})", direction, startX, startY, endX, endY);
    }

    /**
     * Scrolls the given element into view by issuing short upward swipes until
     * it becomes displayed (or a bounded number of attempts is exhausted).
     *
     * @param element the element to bring into view
     */
    protected void scrollToElement(WebElement element) {
        if (isDisplayed(element)) {
            return;
        }
        final int maxAttempts = 8;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            swipe(SwipeDirection.UP);
            if (isDisplayed(element)) {
                log.debug("Scrolled element into view after {} swipe(s)", attempt + 1);
                return;
            }
        }
        log.warn("Element [{}] not visible after {} scroll attempts", describe(element), maxAttempts);
    }

    /**
     * Executes a single-pointer press-move-release drag via W3C actions.
     */
    private void performDrag(int startX, int startY, int endX, int endY, Duration travel) {
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence drag = new Sequence(finger, 1);
        drag.addAction(finger.createPointerMove(Duration.ZERO,
                PointerInput.Origin.viewport(), startX, startY));
        drag.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        drag.addAction(new Pause(finger, Duration.ofMillis(150)));
        drag.addAction(finger.createPointerMove(travel,
                PointerInput.Origin.viewport(), endX, endY));
        drag.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(drag));
    }

    // ------------------------------------------------------------------
    //  Context (native / webview) switching
    // ------------------------------------------------------------------

    /**
     * Switches the driver context to the first available WEBVIEW context, used
     * for hybrid screens (e.g. embedded 3-D Secure / SSL-pinning UX flows).
     * No-op if no webview context is present.
     */
    protected void switchToWebView() {
        String webView = availableContexts().stream()
                .filter(c -> c != null && c.toUpperCase().startsWith("WEBVIEW"))
                .findFirst()
                .orElse(null);
        if (webView == null) {
            log.warn("No WEBVIEW context available; remaining in current context");
            return;
        }
        switchContext(webView);
        log.debug("Switched to webview context [{}]", webView);
    }

    /**
     * Switches the driver context back to NATIVE_APP.
     */
    protected void switchToNative() {
        switchContext("NATIVE_APP");
        log.debug("Switched to NATIVE_APP context");
    }

    // ------------------------------------------------------------------
    //  Accessibility (a11y) helpers
    // ------------------------------------------------------------------

    /**
     * Returns the accessibility content description of an element, used by
     * accessibility assertion helpers in step definitions.
     *
     * <p>On Android this is the {@code content-desc} attribute; on iOS the
     * equivalent {@code name}/{@code label} accessibility attribute. Falls back
     * across the platform-appropriate attributes and finally to visible text.</p>
     *
     * @param element the element to inspect
     * @return the content description, or an empty string if none is present
     */
    protected String contentDescription(WebElement element) {
        WebElement target = waitForVisible(element);
        String value = firstNonBlank(
                attribute(target, "content-desc"),
                attribute(target, "contentDescription"),
                attribute(target, "name"),
                attribute(target, "label"),
                attribute(target, "accessibilityLabel"));
        if (value == null) {
            value = target.getText();
        }
        String result = value == null ? "" : value;
        log.debug("content-description for [{}] = '{}'", describe(target), MaskingUtil.mask(result));
        return result;
    }

    // ------------------------------------------------------------------
    //  Internal utilities
    // ------------------------------------------------------------------

    /** Reads an element attribute, swallowing unsupported-attribute errors. */
    private String attribute(WebElement element, String name) {
        try {
            return element.getAttribute(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Returns the first argument that is non-null and not blank, else {@code null}. */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        return Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Produces a short, log-safe identifier for an element for diagnostics.
     * Never includes user-entered values.
     */
    private String describe(WebElement element) {
        try {
            String tag = element.getTagName();
            return tag == null ? "element" : tag;
        } catch (RuntimeException e) {
            return "element";
        }
    }

    /**
     * Returns the available automation contexts (e.g. {@code NATIVE_APP},
     * {@code WEBVIEW_*}).
     *
     * <p>Uses the Appium {@code mobile: getContexts} endpoint via
     * {@code executeScript}, avoiding a direct dependency on Selenium's
     * {@code ContextAware} interface (removed in Selenium 4) so the framework
     * stays compile-compatible with the pinned Selenium that ships with
     * java-client 9.x.</p>
     */
    @SuppressWarnings("unchecked")
    private List<String> availableContexts() {
        Object result = driver.executeScript("mobile: getContexts");
        if (result instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (result instanceof Collection<?> coll) {
            return coll.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    /**
     * Activates the given automation context via the Appium
     * {@code mobile: switchContext} endpoint.
     *
     * @param name the target context name
     */
    private void switchContext(String name) {
        driver.executeScript("mobile: switchContext", Map.of("name", name));
    }
}
