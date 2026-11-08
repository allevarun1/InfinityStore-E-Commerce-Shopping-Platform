package com.example.ecommerce.service;

import com.example.ecommerce.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    /** Resend (https://resend.com) sends over HTTPS, which works on hosts that block mail-server ports (Render free plan). */
    @Value("${app.mail.resend.api-key:}")
    private String resendKey;

    /** Must be on a domain verified in Resend, e.g. Rekha Shopping Mall <orders@rekhashoppingmall.com>. */
    @Value("${app.mail.resend.from:}")
    private String resendFrom;

    @Value("${app.store.support-email:}")
    private String replyTo;

    private final RestClient resend = RestClient.builder().baseUrl("https://api.resend.com").build();

    private boolean resendOn() {
        return resendKey != null && !resendKey.isBlank() && resendFrom != null && !resendFrom.isBlank();
    }

    // @Async here too: these call send() on this same object, which would otherwise run on the shopper's request.
    @Async
    public void sendOrderPlaced(String to, Order order) {
        send(to, "Rekha Shopping Mall - Order Confirmed",
                "Hi there!\n\nYour order has been placed successfully.\n\n" +
                "Order ID: " + order.getTrackingNumber() + "\n" +
                "Total: Rs. " + order.getTotalPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "\n" +
                "Payment: " + order.getPaymentMethod() + " (" + order.getPaymentStatus() + ")\n\n" +
                "We'll notify you when it ships.\n\nThank you for shopping with Rekha Shopping Mall!");
    }

    @Async
    public void sendPaymentSuccess(String to, Order order) {
        send(to, "Rekha Shopping Mall - Payment Successful",
                "Hi there!\n\nPayment for your order has been confirmed.\n\n" +
                "Order ID: " + order.getTrackingNumber() + "\n" +
                "Amount Paid: Rs. " + order.getTotalPrice().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "\n\n" +
                "Thank you for shopping with Rekha Shopping Mall!");
    }

    @Async
    public void sendPasswordReset(String to, String resetLink, long validMinutes) {
        send(to, "Rekha Shopping Mall - Password Reset",
                "Hi there!\n\nYou requested a password reset.\n\n" +
                "Open this link to choose a new password (valid for " + validMinutes + " minutes):\n" + resetLink + "\n\n" +
                "If you didn't request this, you can safely ignore this email.\n\n" +
                "- Rekha Shopping Mall Team");
    }

    /** True when an SMTP server is configured, so Autopilot knows whether email can really go out. */
    public boolean configured() {
        return resendOn() || (mailSender != null && fromEmail != null && !fromEmail.isBlank());
    }

    @Async
    public void send(String to, String subject, String body) {
        if (to == null || to.isBlank() || (!resendOn() && mailSender == null)) {
            log.warn("Email skipped: mail is not configured or the recipient is blank");
            return;
        }
        if (resendOn()) {
            sendWithResend(to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("Rekha Shopping Mall <" + fromEmail + ">");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent: subject=\"{}\"", subject);
        } catch (Exception ex) {
            log.error("Email delivery failed: subject=\"{}\" cause={}", subject, ex.getMessage());
        }
    }

    private void sendWithResend(String to, String subject, String body) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("from", resendFrom);
            payload.put("to", List.of(to));
            payload.put("subject", subject);
            payload.put("text", body);
            if (replyTo != null && !replyTo.isBlank()) payload.put("reply_to", replyTo);
            resend.post().uri("/emails")
                    .header("Authorization", "Bearer " + resendKey.trim())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().toBodilessEntity();
            log.info("Email sent via Resend: subject=\"{}\"", subject);
        } catch (Exception ex) {
            log.error("Resend email failed: subject=\"{}\" cause={}", subject, ex.getMessage());
        }
    }
}
