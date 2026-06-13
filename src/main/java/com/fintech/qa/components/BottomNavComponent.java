package com.fintech.qa.components;

import com.fintech.qa.core.base.BasePage;
import com.fintech.qa.pages.DashboardPage;
import com.fintech.qa.pages.TransferPage;
import io.appium.java_client.pagefactory.AndroidFindBy;
import io.appium.java_client.pagefactory.iOSXCUITFindBy;
import org.openqa.selenium.WebElement;

/**
 * Persistent bottom navigation bar of the synthetic wallet app.
 *
 * <p>Provides tab navigation between the Home (dashboard), Transfers and
 * Profile sections. Each navigation method returns the destination page object
 * (where one exists) to support fluent step flows.</p>
 */
public class BottomNavComponent extends BasePage {

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/nav_home")
    @iOSXCUITFindBy(accessibility = "nav-home")
    private WebElement homeTab;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/nav_transfers")
    @iOSXCUITFindBy(accessibility = "nav-transfers")
    private WebElement transfersTab;

    @AndroidFindBy(id = "com.fintech.wallet.sample:id/nav_profile")
    @iOSXCUITFindBy(accessibility = "nav-profile")
    private WebElement profileTab;

    /**
     * Navigates to the Home tab.
     *
     * @return the dashboard page object
     */
    public DashboardPage goToHome() {
        tap(homeTab);
        log.info("Navigated to Home via bottom nav");
        return new DashboardPage();
    }

    /**
     * Navigates to the Transfers tab.
     *
     * @return the transfer page object
     */
    public TransferPage goToTransfers() {
        tap(transfersTab);
        log.info("Navigated to Transfers via bottom nav");
        return new TransferPage();
    }

    /**
     * Navigates to the Profile tab. No dedicated page object exists for the
     * profile screen, so this returns nothing.
     */
    public void goToProfile() {
        tap(profileTab);
        log.info("Navigated to Profile via bottom nav");
    }
}
