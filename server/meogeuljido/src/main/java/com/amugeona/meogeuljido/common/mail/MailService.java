package com.amugeona.meogeuljido.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(JavaMailSender mailSender, @Value("${spring.mail.username}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void send(String to, String subject, String body) {
        if (fromAddress == null || fromAddress.isBlank()) {
            /**
             * MAIL_USERNAME이 설정되지 않은 환경(로컬 개발 등)에서는 매일 발송 자체가
             * 불가능, 예외를 던져 매번 스택트레이스를 남기는 대신 조용히 건너뜀
             */
            log.warn("메일 발송을 건너뜀: spring.mail.username(MAIL_USERNAME)이 설정되지 않았습니다. to={}", to);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

}
