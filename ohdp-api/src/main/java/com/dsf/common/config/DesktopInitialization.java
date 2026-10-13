package com.dsf.common.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("desktop")
public class DesktopInitialization {
    private final DesktopSampleDataService samples;
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final PasswordEncoder encoder;
    private final String password;

    public DesktopInitialization(DesktopSampleDataService samples, DataSource source,
            PlatformTransactionManager manager, PasswordEncoder encoder,
            @Value("${dsf.auth.initial-password}") String password) {
        this.samples = samples;
        this.db = new JdbcTemplate(source);
        this.tx = new TransactionTemplate(manager);
        this.encoder = encoder;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        samples.ensureSchema();
        tx.executeWithoutResult(status -> {
            if (db.queryForObject("SELECT COUNT(*) FROM desktop_setting WHERE setting_key='admin-default-v1'", Long.class) > 0) return;
            // One-time upgrade requested for desktop installations only; never reset on later launches.
            db.update("UPDATE sys_user SET password_hash=?,must_change_password=false,updated_at=CURRENT_TIMESTAMP WHERE username='admin' AND deleted=false", encoder.encode(password));
            db.update("UPDATE sys_user_token SET deleted=true,revoked_at=CURRENT_TIMESTAMP WHERE user_id IN (SELECT id FROM sys_user WHERE username='admin')");
            db.update("INSERT INTO desktop_setting(setting_key,setting_value) VALUES ('admin-default-v1','applied')");
        });
    }
}
