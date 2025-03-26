# TICKET-101 Validation Conclusion

## Overview

The intern implemented an MVP for the decision engine backend. The goal was to take personal code, loan amount, and period to return a loan decision.

## Validation Against Requirements

* **Inputs/Outputs:** The backend correctly defines an endpoint (`/loan/decision`) accepting `personalCode`, `loanAmount`, and `loanPeriod` and returns `loanAmount`, `loanPeriod`, and `errorMessage`.
* **Specific Personal Codes:** The backend attempts to handle debt and segmentation based on personal codes[cite: 13]. Debt (segment < 2500) results in a `NoValidLoanException`. Segments 1, 2, and 3 assign credit modifiers (100, 300, 1000).
* **Input Validation:** Basic input validation for personal code (using `EstonianPersonalCodeValidator`), loan amount, and loan period is implemented. Custom exceptions are defined and handled in the controller.
* **Finding Suitable Loan:** The code attempts to find a suitable loan by increasing the loan period if the initial calculation is below the minimum amount. It also caps the output amount at the maximum.
* **Frontend:** The Flutter frontend provides sliders for amount and period, an input field for the personal ID, and makes calls to the backend API, displaying the results or errors.

## What Went Well

* **Project Structure:** Clear separation between backend (Java) and frontend (Flutter) projects.
* **API Definition:** Defined with clear request and response structures.
* **Basic Validation:** Input validation for amount, period, and personal code format is there.
* **Use of Constants:** Configuration values like min/max amounts/periods and credit modifiers are in `DecisionEngineConstants`.
* **Exception Handling:** Custom exceptions are defined for specific error conditions (e.g., `InvalidLoanAmountException`, `NoValidLoanException`) and handled to return appropriate HTTP statuses.
* **Testing:** Unit tests for the `DecisionEngine` and integration tests for the `DecisionEngineController` are present in the backend. Frontend includes basic widget tests and API service tests.

## Areas for Improvement (SOLID Principles & Requirements)

* **Requirement Mismatch (Critical):** The implemented logic in `DecisionEngine.java` calculates the highest approvable amount simply as `creditModifier * loanPeriod`. This **does not match** the scoring algorithm specified in TICKET-101 (`credit score = ((credit modifier / loan amount) * loan period) / 10 >= 0.1`). This is the most significant problem.
* **Requirement Mismatch (Loan Period):** TICKET-101 specifies a maximum loan period of **48 months**. However, `DecisionEngineConstants.java` defines `MAXIMUM_LOAN_PERIOD` as **60**, and the validation/logic uses this value. The frontend slider also goes up to 60 months.
* **Hardcoded boundaries:** The `getCreditModifier` method [cite: 13] uses `if-else if` statements based on hardcoded segment boundaries (2500, 5000, 7500). Adding new segments or changing boundaries requires modifying this class.
* **Algorithm Logic:** The current logic for finding an approved loan/period iterates by increasing the period until `highestValidLoanAmount` reaches the minimum loan amount. This doesn't align with the ticket's goal of finding the *maximum* possible sum (up to 10000€) for the *given or an extended* period, nor does it address finding a *smaller* approved amount if the requested amount fails. The logic needs a complete rework based on the correct scoring algorithm.

## Most Important Shortcoming

* **Wrong Logic for Calculating Approval:** TICKET-101 defines the approval criteria based on a credit score:
credit score = ((credit modifier / loan amount) * loan period) / 10
A loan is approved if the credit score >= 0.1.
The current implementation, however, uses a different logic:
    *It determines a creditModifier based on the personal code.
    *It calculates a maximum approvable amount for a given period using highestValidLoanAmount = creditModifier * loanPeriod.
    *It increases the loanPeriod until this highestValidLoanAmount is at least the MINIMUM_LOAN_AMOUNT.
    *It returns Math.min(DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT, highestValidLoanAmount(loanPeriod)) as the approved amount.
This implemented logic does not use the requested loanAmount in its core calculation and does not apply the credit score >= 0.1 check. 