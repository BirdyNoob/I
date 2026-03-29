package com.icentric.Icentric.platform.admin.service;

import com.icentric.Icentric.platform.admin.entity.PlatformAdmin;
import com.icentric.Icentric.platform.admin.repository.PlatformAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminBootstrapService implements CommandLineRunner {

    private final PlatformAdminRepository platformAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.admin.default.email:admin@icentric.com}")
    private String defaultEmail;

    @Value("${platform.admin.default.password:ChangeMe@123}")
    private String defaultPassword;

    @Value("${platform.admin.default.name:Platform Super Admin}")
    private String defaultName;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (platformAdminRepository.count() == 0) {
            log.info("No Platform Admin found. Bootstrapping default super admin account...");

            PlatformAdmin defaultAdmin = new PlatformAdmin(
                    defaultEmail,
                    passwordEncoder.encode(defaultPassword),
                    defaultName
            );

            platformAdminRepository.save(defaultAdmin);
            
            log.info("Successfully created default Platform Admin with email: {}", defaultEmail);
        } else {
            log.info("Platform Admin accounts already exist. Skipping bootstrap.");
        }
    }
}
