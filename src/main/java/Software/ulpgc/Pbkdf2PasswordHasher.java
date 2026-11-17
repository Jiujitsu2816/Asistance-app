package Software.ulpgc;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.util.Base64;

/** Compatible with the application's existing PBKDF2 credentials. */
public final class Pbkdf2PasswordHasher implements PasswordHasher {
    @Override public Credentials create(char[] password) {
        if (password == null || password.length < 8)
            throw new IllegalArgumentException("La clave debe tener al menos 8 caracteres");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return new Credentials(Base64.getEncoder().encodeToString(salt), hash(password, salt));
    }

    @Override public boolean matches(char[] password, Credentials credentials) {
        if (password == null) return false;
        return MessageDigest.isEqual(Base64.getDecoder().decode(credentials.hash()),
                Base64.getDecoder().decode(hash(password, Base64.getDecoder().decode(credentials.salt()))));
    }

    private String hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 120_000, 256);
        try {
            return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo proteger la clave", exception);
        } finally { spec.clearPassword(); }
    }
}
