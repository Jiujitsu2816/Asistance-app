package software.ulpgc.presencia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comprueba las reglas de dependencia de la arquitectura hexagonal analizando los imports
 * del codigo fuente: el dominio no depende de nada externo y la aplicacion no depende de
 * la infraestructura.
 */
class ArchitectureTest {
    private static final Path SOURCES = Path.of("src", "main", "java", "software", "ulpgc", "presencia");

    @Test
    void domainDependsOnlyOnTheJdk() throws IOException {
        assertEquals(List.of(), violations("domain",
                "software.ulpgc.presencia.application", "software.ulpgc.presencia.infrastructure",
                "com.fasterxml", "org.h2", "java.sql", "com.sun.net"));
    }

    @Test
    void applicationDoesNotDependOnAdapters() throws IOException {
        assertEquals(List.of(), violations("application",
                "software.ulpgc.presencia.infrastructure", "com.fasterxml", "org.h2", "java.sql", "com.sun.net"));
    }

    @Test
    void persistenceAndHttpAdaptersAreIndependent() throws IOException {
        assertEquals(List.of(), violations("infrastructure/persistence", "software.ulpgc.presencia.infrastructure.http"));
        assertEquals(List.of(), violations("infrastructure/http",
                "software.ulpgc.presencia.infrastructure.persistence", "java.sql"));
    }

    private static List<String> violations(String layer, String... forbidden) throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES.resolve(layer))) {
            return files.filter(f -> f.toString().endsWith(".java")).flatMap(file -> {
                try {
                    return Files.readAllLines(file).stream()
                            .filter(line -> line.startsWith("import "))
                            .filter(line -> Stream.of(forbidden).anyMatch(line::contains))
                            .map(line -> SOURCES.relativize(file) + ": " + line);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
        }
    }
}
