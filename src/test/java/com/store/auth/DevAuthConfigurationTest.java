package com.store.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevAuthConfigurationTest {

    @Test
    void devDisablesEmailVerificationAndLoginOtpByDefault() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-dev.yml"));

        Properties properties = factory.getObject();

        assertEquals(
                "${AUTH_EMAIL_VERIFICATION_ENABLED:false}",
                properties.getProperty("app.auth.email-verification-enabled"));
        assertEquals(
                "${AUTH_LOGIN_EMAIL_OTP_ENABLED:false}",
                properties.getProperty("app.auth.login-email-otp-enabled"));
    }
}
