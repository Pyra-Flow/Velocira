package com.velocira.backend.auth.model;

/**
 * Enumeration of OTP (One-Time Password) purposes in the Velocira platform.
 *
 * <p>
 * Different OTP types are used for different verification flows:
 * </p>
 * <ul>
 * <li>{@link #EMAIL_VERIFICATION} — Sent after registration to verify email
 * ownership</li>
 * <li>{@link #PASSWORD_RESET} — Sent when user requests a password reset</li>
 * </ul>
 *
 * @author Velocira Team
 * @since 1.0
 */
public enum OtpType {

    /** OTP for verifying email address after registration. */
    EMAIL_VERIFICATION,

    /** OTP for resetting a forgotten password. */
    PASSWORD_RESET
}
