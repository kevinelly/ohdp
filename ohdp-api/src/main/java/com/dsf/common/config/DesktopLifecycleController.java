package com.dsf.common.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Local desktop launcher lifecycle; never enabled in server deployments. */
@Profile("desktop")
@RestController
public class DesktopLifecycleController {
    private final ConfigurableApplicationContext context;
    private final ApplicationAvailability availability;
    private final String token;
    private final DesktopSampleDataService samples;

    public DesktopLifecycleController(ConfigurableApplicationContext context,
            ApplicationAvailability availability,
            @Value("${BHFYZX_INSTANCE_TOKEN}") String token, DesktopSampleDataService samples) {
        this.context = context;
        this.availability = availability;
        this.token = token;
        this.samples = samples;
    }

    private boolean authorized(String suppliedToken) {
        return !token.isBlank() && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/desktop/sample-data")
    public ResponseEntity<?> sampleStatus(@RequestHeader(value = "X-Desktop-Token", defaultValue = "") String suppliedToken) {
        return authorized(suppliedToken) ? ResponseEntity.ok(samples.status()) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/desktop/sample-data/import")
    public ResponseEntity<?> importSamples(@RequestHeader(value = "X-Desktop-Token", defaultValue = "") String suppliedToken) {
        return authorized(suppliedToken) ? ResponseEntity.ok(samples.importData()) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/desktop/sample-data/delete")
    public ResponseEntity<?> deleteSamples(@RequestHeader(value = "X-Desktop-Token", defaultValue = "") String suppliedToken) {
        return authorized(suppliedToken) ? ResponseEntity.ok(samples.deleteData()) : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/desktop/status")
    public ResponseEntity<Map<String, String>> status() {
        boolean ready = availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("application", "BHFYZX-Desktop", "status", ready ? "ready" : "starting"));
    }

    @PostMapping("/desktop/shutdown")
    public ResponseEntity<Void> shutdown(
            @RequestHeader(value = "X-Desktop-Token", defaultValue = "") String suppliedToken) {
        if (token.isBlank() || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Thread shutdown = new Thread(() -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            System.exit(SpringApplication.exit(context));
        }, "desktop-shutdown");
        shutdown.setDaemon(false);
        shutdown.start();
        return ResponseEntity.accepted().build();
    }
}
