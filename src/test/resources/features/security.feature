@security @non-functional
Feature: Wallet security and resilience
  As a security-conscious synthetic fintech wallet
  I want to enforce session, device, transport, and lifecycle protections
  So that user funds and data stay protected even under adverse conditions

  @regression @android @ios @REQ-SEC-201 @PCI-DSS-8.1.8
  Scenario: Session times out and auto-logs the user out
    Given an authenticated session on the dashboard
    When the session is left idle until it times out
    Then the user is automatically logged out to the login screen

  @smoke @android @ios @REQ-SEC-202 @PCI-DSS-8.3
  Scenario: Biometric failure falls back to password and OTP
    Given the wallet app is on the login screen
    When the user attempts biometric login with a non-matching biometric
    Then biometric authentication is rejected
    And the password and OTP fallback is offered

  @regression @android @ios @REQ-SEC-203 @PCI-DSS-6.5
  Scenario: Root or jailbreak detection blocks access
    Given the wallet app is started on a rooted or jailbroken device
    Then a root or jailbreak warning prompt is displayed
    And access to wallet functionality is blocked

  @regression @android @ios @REQ-SEC-204 @PCI-DSS-4.1
  Scenario: SSL pinning failure is surfaced and blocks the request
    Given an authenticated session on the dashboard
    When the app communicates over a connection that fails certificate pinning
    Then an SSL pinning failure warning is displayed
    And the insecure request is not completed

  @regression @android @ios @REQ-SEC-205 @PCI-DSS-8.1.8
  Scenario: App backgrounding during a transaction does not auto-confirm it
    Given an authenticated session on the dashboard
    And the user has an in-progress transfer awaiting confirmation
    When the app is sent to the background and brought back to the foreground
    Then the in-progress transfer is not auto-confirmed
    And the user must re-authenticate to continue
