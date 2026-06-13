@transfer @payments
Feature: Money transfer
  As an authenticated synthetic wallet user
  I want to transfer funds to a synthetic beneficiary and confirm with OTP
  So that payments are completed securely with no real PII involved

  Background:
    Given the user is logged in and on the dashboard

  @smoke @android @ios @REQ-PAY-101 @PCI-DSS-4.1
  Scenario: Successful transfer to a synthetic beneficiary confirmed with OTP
    When the user opens the transfer screen
    And the user selects the synthetic beneficiary
    And the user enters a transfer amount of "25.00"
    And the user confirms the transfer with OTP
    Then the transfer is successful
    And a confirmation message is shown

  @regression @android @ios @REQ-PAY-102
  Scenario: Transfer opened via bottom navigation completes successfully
    When the user navigates to transfers via the bottom navigation
    And the user selects the synthetic beneficiary
    And the user enters a transfer amount of "10.50"
    And the user confirms the transfer with OTP
    Then the transfer is successful

  @regression @android @ios @REQ-PAY-103 @PCI-DSS-4.1
  Scenario Outline: Transfers of varying synthetic amounts succeed
    When the user opens the transfer screen
    And the user selects the synthetic beneficiary
    And the user enters a transfer amount of "<amount>"
    And the user confirms the transfer with OTP
    Then the transfer is successful

    Examples:
      | amount |
      | 1.00   |
      | 99.99  |
      | 250.00 |
