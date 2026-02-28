package com.velocira.backend.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Custom validation annotation for password strength enforcement.
 *
 * <p>
 * Requirements:
 * </p>
 * <ul>
 * <li>At least 8 characters long</li>
 * <li>Contains at least one uppercase letter</li>
 * <li>Contains at least one lowercase letter</li>
 * <li>Contains at least one digit</li>
 * <li>Contains at least one special character</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 * @see PasswordStrengthValidator
 */
@Documented
@Constraint(validatedBy = PasswordStrengthValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
