package Software.ulpgc;

/** Protects credentials independently of accounts, sessions and storage. */
public interface PasswordHasher {
    record Credentials(String salt, String hash) { }
    /** Rejects null passwords and passwords shorter than eight characters. */
    Credentials create(char[] password);
    boolean matches(char[] password, Credentials credentials);
}
