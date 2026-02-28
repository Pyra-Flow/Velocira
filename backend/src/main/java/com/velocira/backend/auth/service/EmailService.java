package com.velocira.backend.auth.service;

import com.velocira.backend.auth.model.OtpType;
import com.velocira.backend.auth.model.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Service responsible for sending transactional emails.
 *
 * <p>
 * All email sending is performed asynchronously via {@link Async} to prevent
 * blocking the request thread. The service is abstracted behind a simple API
 * that can easily be swapped from Gmail SMTP to SendGrid, SES, etc.
 * </p>
 *
 * @author Velocira Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${velocira.mail.from-address}")
    private String fromAddress;

    @Value("${velocira.mail.from-name}")
    private String fromName;

    /**
     * Sends an OTP verification email asynchronously.
     *
     * @param user    the recipient user
     * @param otp     the plain-text OTP code (will be in the email body)
     * @param otpType the purpose of the OTP
     */
    @Async
    public void sendOtpEmail(UserEntity user, String otp, OtpType otpType) {
        String subject;
        String htmlBody;

        switch (otpType) {
            case EMAIL_VERIFICATION -> {
                subject = "Velocira — Verify Your Email Address";
                htmlBody = buildVerificationEmailHtml(user.getFullName(), otp);
            }
            case PASSWORD_RESET -> {
                subject = "Velocira — Password Reset Code";
                htmlBody = buildPasswordResetEmailHtml(user.getFullName(), otp);
            }
            default -> {
                log.error("Unknown OTP type: {}", otpType);
                return;
            }
        }

        sendHtmlEmail(user.getEmail(), subject, htmlBody);
    }

    /**
     * Sends an HTML email to the specified recipient.
     *
     * @param to      the recipient email address
     * @param subject the email subject
     * @param html    the HTML body
     */
    private void sendHtmlEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent successfully to [{}] with subject [{}]", to, subject);
        } catch (MessagingException | MailException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send email to [{}]: {}", to, ex.getMessage(), ex);
        }
    }

    /**
     * Builds the HTML content for an email verification email.
     */
    private String buildVerificationEmailHtml(String name, String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2563EB;">Velocira — Email Verification</h2>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>Thank you for registering with Velocira! Please use the following code to verify your email address:</p>
                    <div style="background-color: #F3F4F6; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #1F2937;">%s</span>
                    </div>
                    <p>This code will expire in <strong>10 minutes</strong>.</p>
                    <p>If you did not create an account, please ignore this email.</p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 20px 0;">
                    <p style="color: #9CA3AF; font-size: 12px;">© 2026 Velocira by PyraFlow. All rights reserved.</p>
                </div>
                """
                .formatted(name, otp);
    }

    /**
     * Builds the HTML content for a password reset email.
     */
    private String buildPasswordResetEmailHtml(String name, String otp) {
        return """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #2563EB;">Velocira — Password Reset</h2>
                    <p>Hello <strong>%s</strong>,</p>
                    <p>We received a request to reset your password. Use the following code to proceed:</p>
                    <div style="background-color: #F3F4F6; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #1F2937;">%s</span>
                    </div>
                    <p>This code will expire in <strong>10 minutes</strong>.</p>
                    <p>If you did not request a password reset, please ignore this email and ensure your account is secure.</p>
                    <hr style="border: none; border-top: 1px solid #E5E7EB; margin: 20px 0;">
                    <p style="color: #9CA3AF; font-size: 12px;">© 2026 Velocira by PyraFlow. All rights reserved.</p>
                </div>
                """
                .formatted(name, otp);
    }
}
