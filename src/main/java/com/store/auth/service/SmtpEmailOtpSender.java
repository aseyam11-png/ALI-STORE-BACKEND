package com.store.auth.service;

import com.store.auth.entity.EmailOtpPurpose;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SmtpEmailOtpSender implements EmailOtpSender {

    private final JavaMailSender mailSender;
    private final Environment environment;

    @Override
    public void send(String recipient, String code, EmailOtpPurpose purpose, Duration validFor) {
        String from = environment.getProperty("app.mail.from", "");
        if (!StringUtils.hasText(from)) {
            throw new IllegalStateException(
                    "MAIL_FROM must be configured before sending authentication email");
        }

        String subject = purpose == EmailOtpPurpose.LOGIN
                ? "ALI STORE - رمز تسجيل الدخول"
                : "ALI STORE - تأكيد البريد الإلكتروني";

        String action = purpose == EmailOtpPurpose.LOGIN
                ? "إكمال تسجيل الدخول"
                : "تأكيد البريد الإلكتروني";

        String text = """
                مرحبًا،

                استخدم رمز التحقق التالي لإكمال %s في ALI STORE:

                %s

                صلاحية الرمز: %d دقائق.
                لا تشارك هذا الرمز مع أي شخص.

                إذا لم تطلب هذه العملية، تجاهل هذه الرسالة.
                """.formatted(
                action,
                code,
                Math.max(1, validFor.toMinutes()));

        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
        } catch (Exception ex) {
            throw new IllegalStateException("Authentication email could not be sent", ex);
        }
    }
}
