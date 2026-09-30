package com.sks.sksiskur.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;
    private final String fromName;

    public EmailNotificationService(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail.enabled:false}") boolean enabled,
            @Value("${app.mail.from:noreply@gantep.edu.tr}") String fromAddress,
            @Value("${app.mail.from-name:SKS İŞKUR}") String fromName
    ) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    public boolean isEnabled() {
        return enabled && mailSender != null;
    }

    public boolean sendPlainText(String to, String subject, String body) {
        if (!enabled) {
            log.info("E-posta devre dışı; gönderim atlandı: {}", to);
            return false;
        }
        if (mailSender == null) {
            log.warn("JavaMailSender yapılandırılmadı; e-posta gönderilemedi: {}", to);
            return false;
        }
        if (to == null || to.isBlank()) {
            return false;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to.trim());
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("E-posta gönderilemedi ({}): {}", to, ex.getMessage());
            return false;
        }
    }
}
