package com.velocira.backend.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator implementation for the {@link StrongPassword} annotation.
 *
 * <p>
 * Checks that a password meets the following requirements:
 * </p>
 * <ul>
 * <li>At least one uppercase letter [A-Z]</li>
 * <li>At least one lowercase letter [a-z]</li>
 * <li>At least one digit [0-9]</li>
 * <li>At least one special character (non-alphanumeric)</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
public class PasswordStrengthValidator implements ConstraintValidator<StrongPassword, String> {

    /**
     * Validates that the password meets strength requirements.
     *
     * @param password the password to validate
     * @param context  the constraint validator context
     * @return {@code true} if the password is strong enough
     */
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return true; // Let @NotBlank handle null/blank
        }

        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
