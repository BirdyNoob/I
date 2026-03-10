package com.icentric.Icentric.security;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrData.Builder;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;

import org.springframework.stereotype.Service;

@Service
public class MfaService {

    public String generateSecret() {
        return new DefaultSecretGenerator().generate();
    }

    public byte[] generateQrCode(String email, String secret) throws Exception {

        QrData data = new Builder()
                .label(email)
                .secret(secret)
                .issuer("AISafe")
                .digits(6)
                .period(30)
                .build();

        QrGenerator generator = new ZxingPngQrGenerator();
        return generator.generate(data);
    }

    public boolean verifyCode(String secret, String code) {

        CodeVerifier verifier =
                new DefaultCodeVerifier(
                        new DefaultCodeGenerator(),
                        new SystemTimeProvider()
                );

        return verifier.isValidCode(secret, code);
    }
}
