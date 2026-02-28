package com.velocira.backend.auth.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link PasswordStrengthValidator}.
 *
 * @author Velocira Team
 * @since 1.0
 */
@DisplayName("PasswordStrengthValidator Tests")
class PasswordStrengthValidatorTest {

    private PasswordStrengthValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new PasswordStrengthValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @ParameterizedTest
    @DisplayName("Should accept strong passwords")
    @ValueSource(strings = {
            "StrongP@ss1",
            "MyP@ssw0rd!",
            "C0mpl3x#Pass",
            "Abcdef1!",
            "Test123$%^"
    })
    void shouldAcceptStrongPasswords(String password) {
        assertThat(validator.isValid(password, context)).isTrue();
    }

    @Test
    @DisplayName("Should reject password without uppercase")
    void shouldRejectWithoutUppercase() {
        assertThat(validator.isValid("lowercase1!", context)).isFalse();
    }

    @Test
    @DisplayName("Should reject password without lowercase")
    void shouldRejectWithoutLowercase() {
        assertThat(validator.isValid("UPPERCASE1!", context)).isFalse();
    }

    @Test
    @DisplayName("Should reject password without digit")
    void shouldRejectWithoutDigit() {
        assertThat(validator.isValid("NoDigits!@", context)).isFalse();
    }

    @Test
    @DisplayName("Should reject password without special character")
    void shouldRejectWithoutSpecialChar() {
        assertThat(validator.isValid("NoSpecial1A", context)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("Should accept null and empty (delegates to @NotBlank)")
    @NullAndEmptySource
    void shouldRejectNullAndEmpty(String password) {
        // Validator returns true for null/blank — @NotBlank handles those constraints
        assertThat(validator.isValid(password, context)).isTrue();
    }
}
