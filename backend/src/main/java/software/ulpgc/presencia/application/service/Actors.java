package software.ulpgc.presencia.application.service;

/** Normaliza el nombre de quien realiza una operacion para la auditoria. */
final class Actors {
    static final String ANONYMOUS = "anonimo";

    private Actors() { }

    static String normalize(String actor) {
        if (actor == null || actor.isBlank()) return ANONYMOUS;
        String clean = actor.strip().replaceAll("[^\\p{L}\\p{N}._@ -]", "");
        if (clean.isEmpty()) return ANONYMOUS;
        return clean.length() > 40 ? clean.substring(0, 40) : clean;
    }
}
