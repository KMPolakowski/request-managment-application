Feature: Lifecycle of a request
  As a business user of the structured finance platform
  I want every request to follow the agreed state diagram
  So that nothing is published or discarded without going through the right checks

  Background:
    Given a request named "Liquidity coverage ratio report" with body "First draft"

  Scenario: A request is verified, accepted and finally published
    When I verify the request
    And I accept the request
    And I publish the request
    Then the request is in state PUBLISHED
    And the request has been given a publication number

  Scenario: A published request carries a unique number
    Given another request named "Net stable funding ratio report" with body "First draft"
    When I take both requests all the way to publication
    Then both requests have different publication numbers

  Scenario: A request cannot skip the verification step
    When I try to accept the request
    Then the action is refused because the transition is not allowed
    And the request is in state CREATED

  Scenario: A request cannot be published before being accepted
    When I verify the request
    And I try to publish the request
    Then the action is refused because the transition is not allowed
    And no publication number has been handed out

  Scenario Outline: Discarding a request always has to be justified
    When I take the request to state <state>
    And I try to <action> the request without a reason
    Then the action is refused because a reason is required
    And the request is in state <state>

    Examples:
      | state    | action |
      | CREATED  | delete |
      | VERIFIED | reject |
      | ACCEPTED | reject |

  Scenario: A rejected request keeps the reason it was rejected for
    When I take the request to state ACCEPTED
    And I reject the request because of "Supporting evidence is missing"
    Then the request is in state REJECTED
    And the audit trail records "Supporting evidence is missing" as the reason of the last change

  Scenario Outline: The body can be corrected while the request is still under review
    When I take the request to state <state>
    And I change the body to "Reviewed draft"
    Then the body of the request is "Reviewed draft"

    Examples:
      | state    |
      | CREATED  |
      | VERIFIED |

  Scenario Outline: The body is frozen once the request has been accepted
    When I take the request to state <state>
    And I try to change the body to "Too late"
    Then the change is refused because the body is not editable
    And the body of the request is "First draft"

    Examples:
      | state     |
      | ACCEPTED  |
      | PUBLISHED |

  Scenario: Every change of state is auditable
    When I verify the request
    And I accept the request
    And I publish the request
    Then the audit trail reads CREATED, VERIFIED, ACCEPTED, PUBLISHED

  Scenario: A deleted request stays in the system for audit purposes
    When I delete the request because of "Submitted twice by mistake"
    Then the request is in state DELETED
    And the request can still be read
    And the audit trail records "Submitted twice by mistake" as the reason of the last change
