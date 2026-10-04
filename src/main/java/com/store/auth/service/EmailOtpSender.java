package com.store.auth.service;

import com.store.auth.entity.EmailOtpPurpose;

import java.time.Duration;

public interface EmailOtpSender {

    void send(String recipient, String code, EmailOtpPurpose purpose, Duration validFor);
}
