package software.ulpgc.presencia.application.port.out;

/** Proteccion de claves (el algoritmo lo elige el adaptador). */
public interface PasswordHasher {
    String hash(char[] password);

    boolean matches(char[] password, String storedHash);
}
