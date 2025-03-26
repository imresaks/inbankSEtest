package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.InvalidLoanAmountException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanPeriodException;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;
import ee.taltech.inbankbackend.exceptions.NoValidLoanException;
import org.springframework.stereotype.Service;

/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 *
 */
@Service
public class DecisionEngine {

    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    // Removed creditModifier field, get it when needed

    public Decision calculateApprovedLoan(String personalCode, Long requestedLoanAmount, int requestedLoanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException {

        // 1. Verify inputs (including the updated MAX_PERIOD)
        try {
            verifyInputs(personalCode, requestedLoanAmount, requestedLoanPeriod);
        } catch (InvalidPersonalCodeException | InvalidLoanAmountException | InvalidLoanPeriodException e) {
            // Decide if these should return Decision or throw - current controller expects throws
            if (e instanceof InvalidPersonalCodeException) throw (InvalidPersonalCodeException) e;
            if (e instanceof InvalidLoanAmountException) throw (InvalidLoanAmountException) e;
            if (e instanceof InvalidLoanPeriodException) throw (InvalidLoanPeriodException) e;
            // Fallback for unexpected Exception types from verifyInputs if any added later
            throw new RuntimeException("Input validation failed", e);
        }
        // Removed the try-catch returning Decision as controller now handles exceptions

        // 2. Check for debt
        int creditModifier = getCreditModifier(personalCode);
        if (creditModifier == 0) {
            // If customer has debt, reject immediately
            throw new NoValidLoanException("Loan rejected due to existing debt.");
        }

        // 3. Find the best possible loan offer
        // Iterate through periods from requestedLoanPeriod up to MAXIMUM_LOAN_PERIOD
        for (int currentPeriod = requestedLoanPeriod; currentPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD; currentPeriod++) {
            // For each period, check potential amounts from MAXIMUM down to MINIMUM
            // Start from MAXIMUM_LOAN_AMOUNT to find the highest possible approval
            for (int currentAmount = DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT; currentAmount >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT; currentAmount--) {
                double score = calculateCreditScore(creditModifier, currentAmount, currentPeriod);

                if (score >= DecisionEngineConstants.CREDIT_SCORE_THRESHOLD) {
                    // Found the highest possible valid amount for this period (or potentially extended period)
                    // Return this best offer
                    return new Decision(currentAmount, currentPeriod, null);
                }
            }
            // If no amount was suitable for currentPeriod, loop continues to next period
        }

        // 4. If no suitable loan found after checking all periods
        throw new NoValidLoanException("No suitable loan found within the allowed period and amount constraints.");
    }

    /**
     * Calculates the credit score based on the provided formula.
     * credit score = ((credit modifier / loan amount) * loan period) / 10
     */
    private double calculateCreditScore(int creditModifier, int loanAmount, int loanPeriod) {
        if (loanAmount <= 0) { // Prevent division by zero
            return 0.0;
        }
        // Use double for division to maintain precision
        return (((double) creditModifier / loanAmount) * loanPeriod) / 10.0;
    }


    // getCreditModifier remains the same as in the original code
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) { // Assuming 0000-2499 is debt based on original logic
            return 0; // Debt indicator
        } else if (segment < 5000) { // Segment 1: 2500-4999
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) { // Segment 2: 5000-7499
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        } else { // Segment 3: 7500-9999
            return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
        }
    }


    // verifyInputs needs update for MAXIMUM_LOAN_PERIOD change
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {

        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        // Use constants for bounds checking
        if (loanAmount < DecisionEngineConstants.MINIMUM_LOAN_AMOUNT
                || loanAmount > DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT) {
            throw new InvalidLoanAmountException(
                    String.format("Requested loan amount must be between %d€ and %d€!",
                            DecisionEngineConstants.MINIMUM_LOAN_AMOUNT,
                            DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT));
        }
        if (loanPeriod < DecisionEngineConstants.MINIMUM_LOAN_PERIOD
                || loanPeriod > DecisionEngineConstants.MAXIMUM_LOAN_PERIOD) { // Uses updated MAX constant
            throw new InvalidLoanPeriodException(
                    String.format("Requested loan period must be between %d and %d months!",
                            DecisionEngineConstants.MINIMUM_LOAN_PERIOD,
                            DecisionEngineConstants.MAXIMUM_LOAN_PERIOD));
        }
    }
}