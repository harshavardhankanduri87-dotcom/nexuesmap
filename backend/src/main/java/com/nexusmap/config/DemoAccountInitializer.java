package com.nexusmap.config;

import com.nexusmap.model.Role;
import com.nexusmap.model.User;
import com.nexusmap.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Order(10)
@RequiredArgsConstructor
public class DemoAccountInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${nexusmap.demo.seed-accounts:true}")
    private boolean seedAccounts;

    @Override
    public void run(String... args) {
        if (!seedAccounts) {
            log.info("Demo account seeding is disabled in current configuration.");
            return;
        }
        if (!userRepository.existsByEmailIgnoreCase("admin@nexus.edu")) {
            User admin = User.builder()
                .name("Campus Administrator")
                .email("admin@nexus.edu")
                .password(passwordEncoder.encode("Admin@123456"))
                .role(Role.ROLE_ADMIN)
                .ghostMode(false)
                .locationSharingEnabled(true)
                .fuzzingRadiusMeters(100)
                .build();
            userRepository.save(admin);
            log.info("Provisioned default administrator account: admin@nexus.edu");
        }

        if (!userRepository.existsByEmailIgnoreCase("student@nexus.edu")) {
            User student = User.builder()
                .name("Alex Chen")
                .email("student@nexus.edu")
                .password(passwordEncoder.encode("Nexus@123456"))
                .role(Role.ROLE_USER)
                .ghostMode(false)
                .locationSharingEnabled(true)
                .fuzzingRadiusMeters(100)
                .build();
            userRepository.save(student);
            log.info("Provisioned default student account: student@nexus.edu");
        }
    }
}
