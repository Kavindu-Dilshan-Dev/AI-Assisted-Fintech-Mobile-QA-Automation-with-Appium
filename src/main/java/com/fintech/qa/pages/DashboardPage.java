package com.fintech.qa.pages;

import com.fintech.qa.components.BottomNavComponent;
import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.core.security.MaskingUtil;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * Dashboard (home) screen of the synthetic fintech wallet, shown after a successful login.
 *
 * <p>Surfaces the account balance and the entry points to the rest of the app: the persistent
 * {@link BottomNavComponent} and a shortcut into the {@link TransferPage}. The balance is read
 * through {@link BasePage#getText(WebElement)} and returned masked via {@link MaskingUtil} so a
 * stray log/report line can never leak the raw on-screen value.</p>
 *
 * <p>Locators are declared as {@code @AndroidFindBy}/{@code @iOSXCUITFindBy} annotations and mirror
 * {@code src/test/resources/locators/dashboard-<platform>.json}.</p>
 */
public class DashboardPage extends BasePage {

    /** A widget unique to the dashboard screen, used by {@link #isLoaded()}. */
    @AndroidFindBy(id = "com.fintech.wallet.sample:id/dashboard_root")
    @iOSXCUITFindBy(accessibility = "dashboard-root")
    private WebElement dashboardRoot;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/text_balance")
    @iOSXCUITFindBy(accessibility = "dashboard-balance")
    private WebElement balanceLabel;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/btn_transfer")
    @iOSXCUITFindBy(accessibility = "dashboard-transfer-button")
    private WebElement transferShortcut;

    /** Composed persistent bottom navigation component. */
    private final BottomNavComponent bottomNav = new BottomNavComponent();

    /**
     * @return {@code true} when the dashboard screen is rendered (its root element is displayed)
     */
    public boolean isLoaded() {
        boolean loaded = isDisplayed(dashboardRoot);
        log.debug("DashboardPage.isLoaded() = {}", loaded);
        return loaded;
    }

    /**
     * Returns the account balance as shown on the dashboard, masked for safe logging/reporting.
     *
     * <p>The raw on-screen text is passed through {@link MaskingUtil#mask(String)} so any embedded
     * account number/amount that the masking rules recognise is redacted before it leaves the
     * method.</p>
     *
     * @return the masked balance text
     */
    public String getMaskedBalance() {
        String raw = getText(balanceLabel);
        String masked = MaskingUtil.mask(raw);
        log.info("Read masked dashboard balance");
        return masked;
    }

    /**
     * @return the persistent {@link BottomNavComponent} for tab navigation
     */
    public BottomNavComponent bottomNav() {
        return bottomNav;
    }

    /**
     * Opens the transfer screen via the dashboard shortcut.
     *
     * @return the {@link TransferPage}
     */
    public TransferPage openTransfer() {
        log.info("Opening transfer screen from dashboard");
        tap(transferShortcut);
        return new TransferPage();
    }
}
