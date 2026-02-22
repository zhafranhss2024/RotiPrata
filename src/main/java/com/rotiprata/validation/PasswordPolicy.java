package com.rotiprata.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ FIELD, PARAMETER })
@Retention(RUNTIME)
public @interface PasswordPolicy {
    String message() default "Password must be at least 12 characters and include uppercase, lowercase, number, and symbol.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
