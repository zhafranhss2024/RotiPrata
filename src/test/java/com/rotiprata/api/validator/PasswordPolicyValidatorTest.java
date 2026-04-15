package com.rotiprata.api.validator;

import com.rotiprata.validation.PasswordPolicyValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordPolicyValidatorTest {

    private PasswordPolicyValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator();
        context = mock(ConstraintValidatorContext.class);
    }

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
