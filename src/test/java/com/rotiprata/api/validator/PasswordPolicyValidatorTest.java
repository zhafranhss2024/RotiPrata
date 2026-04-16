package com.rotiprata.api.validator;

import com.rotiprata.validation.PasswordPolicyValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Covers password policy validator scenarios and regression behavior for the current branch changes.
 */
class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;
    private ConstraintValidatorContext context;

    /**
     * Builds the shared test fixture and default mock behavior for each scenario.
     */
    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    /**
     * Verifies that is valid should return true when value is null.
     */
    // Returns true because null password values are treated as valid by the validator.
    @Test
    void isValid_ShouldReturnTrue_WhenValueIsNull() {
        //arrange
        String value = null;

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertTrue(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return false when password is shorter than minimum length.
     */
    // Returns false because passwords shorter than 12 characters violate the minimum length requirement.
    @Test
    void isValid_ShouldReturnFalse_WhenPasswordIsShorterThanMinimumLength() {
        //arrange
        String value = "Aa1!short";

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertFalse(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return false when password is longer than maximum length.
     */
    // Returns false because passwords longer than 128 characters violate the maximum length requirement.
    @Test
    void isValid_ShouldReturnFalse_WhenPasswordIsLongerThanMaximumLength() {
        //arrange
        String value = "Aa1!" + "a".repeat(125);

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertFalse(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return false when password missing uppercase character.
     */
    // Returns false because password must include at least one uppercase letter.
    @Test
    void isValid_ShouldReturnFalse_WhenPasswordMissingUppercaseCharacter() {
        //arrange
        String value = "lowercase1!xx";

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertFalse(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return false when password missing lowercase character.
     */
    // Returns false because password must include at least one lowercase letter.
    @Test
    void isValid_ShouldReturnFalse_WhenPasswordMissingLowercaseCharacter() {
        //arrange
        String value = "UPPERCASE1!XX";

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertFalse(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return false when password missing digit character.
     */
    // Returns false because password must include at least one numeric digit.
    @Test
    void isValid_ShouldReturnFalse_WhenPasswordMissingDigitCharacter() {
        //arrange
        String value = "NoDigits!!Abc";

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertFalse(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return false when password missing symbol character.
     */
    // Returns false because password must include at least one symbol character.
    @Test
    void isValid_ShouldReturnFalse_WhenPasswordMissingSymbolCharacter() {
        //arrange
        String value = "NoSymbol123Ab";

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertFalse(result);

        //verify
        verifyNoInteractions(context);
    }

    /**
     * Verifies that is valid should return true when password meets all policy requirements.
     */
    // Returns true because password satisfies length and all character category requirements.
    @Test
    void isValid_ShouldReturnTrue_WhenPasswordMeetsAllPolicyRequirements() {
        //arrange
        String value = "StrongPass1!";

        //act
        boolean result = validator.isValid(value, context);

        //assert
        assertTrue(result);

        //verify
        verifyNoInteractions(context);
    }
}
