package com.fintech.qa.components;

import com.fintech.qa.core.base.BasePage;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * Transient toast / snackbar message component.
 *
 * <p>On Android this maps to the system {@code android.widget.Toast} widget
 * (surfaced by UiAutomator2); on iOS — which has no native toast — it maps to
 * the app's in-app snackbar/banner element.</p>
 *
 * <p>The message text is always read through {@link BasePage#getText(WebElement)}
 * and logged via the masking-aware base helpers, so any sensitive content (e.g.
 * an account reference echoed in a confirmation) is redacted before logging.</p>
 */
public class ToastComponent extends BasePage {

    @AndroidFindBy(uiAutomator = "new UiSelector().className(\"android.widget.Toast\")")
    @iOSXCUITFindBy(accessibility = "app-snackbar")
    private WebElement toast;

    /**
     * Returns the toast/snackbar message text.
     *
     * @return the message text, or an empty string if the toast is absent
     */
    public String getMessage() {
        if (!isShown()) {
            return "";
        }
        return getText(toast);
    }

    /**
     * Reports whether the toast/snackbar is currently displayed.
     *
     * @return {@code true} if shown
     */
    public boolean isShown() {
        return isDisplayed(toast);
    }
}
