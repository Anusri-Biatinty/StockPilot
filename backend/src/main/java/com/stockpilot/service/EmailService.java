package com.stockpilot.service;

import com.stockpilot.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends notification emails when MAIL_ENABLED=true; otherwise only logs them. Runs asynchronously so a
 * slow or unavailable SMTP server never blocks or fails a business transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final AppProperties properties;
    private final ObjectProvider<JavaMailSender> mailSender;

    @Async
    public void send(String to, String subject, String body) {
        if (!properties.mail().enabled()) {
            log.debug("Email disabled; would send '{}' to {}", subject, to);
            return;
        }
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("MAIL_ENABLED=true but no mail sender is configured");
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(properties.mail().from());
            message.setTo(to);
            message.setSubject("[StockPilot] " + subject);
            message.setText(body);
            sender.send(message);
        } catch (Exception ex) {
            log.warn("Failed to send email '{}' to {}: {}", subject, to, ex.getMessage());
        }
    }
}
