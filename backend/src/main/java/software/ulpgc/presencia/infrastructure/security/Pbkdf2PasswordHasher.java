package software.ulpgc.presencia.infrastructure.security;

import software.ulpgc.presencia.application.port.out.PasswordHasher;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PBKDF2-HMAC-SHA256 con sal aleatoria de 16 bytes. Formato guardado:
 * {@code pbkdf2-sha256$iteraciones$sal$hash} (Base64).
 */
public final class Pbkdf2PasswordHasher implements PasswordHasher {
    public static final int DEFAULT_ITERATIONS = 210_000;
    private static final String PREFIX = "pbkdf2-sha256";
    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public Pbkdf2PasswordHasher() {
        this(DEFAULT_ITERATIONS);
    }

    public Pbkdf2PasswordHasher(int iterations) {
        this.iterations = iterations;
    }

    @Override
    public String hash(char[] password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return PREFIX + "$" + iterations + "$" + b64.encodeToString(salt) + "$"
                + b64.encodeToString(derive(password, salt, iterations));
    }

    @Override
    public boolean matches(char[] password, String storedHash) {
        String[] parts = storedHash == null ? new String[0] : storedHash.split("\\$");
        if (parts.length != 4 || !parts[0].equals(PREFIX)) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected, derive(password, salt, Integer.parseInt(parts[1])));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no disponible", e);
        } finally {
            spec.clearPassword();
        }
    }
}
