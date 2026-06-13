@login @authentication
Feature: Wallet login
  As a synthetic fintech wallet user
  I want to authenticate with credentials, OTP, or biometrics
  So that only I can access my (synthetic) account

  Background:
    Given the wallet app is launched on the login screen

  @smoke @android @ios @REQ-AUTH-014 @PCI-DSS-8.2
  Scenario: Successful login with valid credentials and OTP
    When the user enters a valid username and password
    And the user submits the login form
    And the user completes the OTP challenge
    Then the dashboard is displayed
    And the masked account balance is shown

  @regression @android @ios @REQ-AUTH-014
  Scenario: One-step login helper with valid credentials and OTP
    When the user logs in with valid credentials and OTP
    Then the dashboard is displayed

  @smoke @android @ios @REQ-AUTH-021 @PCI-DSS-8.3
  Scenario: Successful biometric login
    When the user authenticates with biometrics
    Then the dashboard is displayed
    And the masked account balance is shown
