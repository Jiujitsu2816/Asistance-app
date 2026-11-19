package software.ulpgc.presencia.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Pbkdf2PasswordHasherTest {
    private final Pbkdf2PasswordHasher hasher = new Pbkdf2PasswordHasher(1000);

    @Test
    void hashesWithRandomSaltAndVerifies() {
        String first = hasher.hash("clave-segura-1".toCharArray());
        String second = hasher.hash("clave-segura-1".toCharArray());
        assertNotEquals(first, second, "cada hash usa una sal distinta");
        assertFalse(first.contains("clave-segura-1"));
        assertTrue(first.startsWith("pbkdf2-sha256$1000$"));
        assertTrue(hasher.matches("clave-segura-1".toCharArray(), first));
        assertFalse(hasher.matches("clave-segura-2".toCharArray(), first));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(hasher.matches("x".toCharArray(), null));
        assertFalse(hasher.matches("x".toCharArray(), "texto-plano"));
        assertFalse(hasher.matches("x".toCharArray(), "pbkdf2-sha256$1000$%%%$%%%"));
    }
}
