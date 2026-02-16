package com.rotiprata.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {
    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 128;
    private static final String UPPER_REGEX = ".*[A-Z].*";
    private static final String LOWER_REGEX = ".*[a-z].*";
    private static final String DIGIT_REGEX = ".*\\d.*";
    private static final String SYMBOL_REGEX = ".*[^A-Za-z0-9\\s].*";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.length();
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        return value.matches(UPPER_REGEX)
            && value.matches(LOWER_REGEX)
            && value.matches(DIGIT_REGEX)
            && value.matches(SYMBOL_REGEX);
    }
}
