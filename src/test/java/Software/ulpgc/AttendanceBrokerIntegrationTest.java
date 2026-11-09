package Software.ulpgc;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/** Includes the dependency-free broker scenarios in Maven and IDE test runs. */
class AttendanceBrokerIntegrationTest {
    @Test void backupRotationKeepsThePreviousCopyWhenSourceHasNotChanged() throws Exception {
        var directory = Files.createTempDirectory(Path.of("target"), "rotation-");
        try (var broker = new AttendanceBroker(directory.resolve("attendance.properties"), Clock.systemUTC())) {
            broker.createFirstManager("manager", "test-password".toCharArray());
            var credentials = broker.recoverUsers();
            var root = broker.login("root", credentials.get("root").toCharArray());
            var previous = broker.createBackup(root);
            for (int i = 0; i < 6; i++) {
                var current = broker.createBackup(root);
                try (var files = Files.list(directory.toAbsolutePath())) {
                    var backups = files.filter(p -> p.getFileName().toString().contains(".backup-"))
                            .collect(Collectors.toSet());
                    assertEquals(Set.of(previous, current), backups);
                }
                previous = current;
            }
        }
    }

    @Test void brokerRegressionScenarios() throws Exception {
        AttendanceBrokerTest.main(new String[0]);
    }

    @Test void rejectsNegativeStoredCountsWithoutOverwritingData() throws Exception {
        var directory = Files.createTempDirectory(Path.of("target"), "bad-counts-");
        var file = directory.resolve("attendance.properties");
        var clock = Clock.systemUTC();
        try (var broker = new AttendanceBroker(file, clock)) {
            broker.createFirstManager("manager", "test-password".toCharArray());
            var manager = broker.login("manager", "test-password".toCharArray());
            var employee = new Employe("Ana", "Garcia", 1);
            broker.registerEmployee(manager, "ana", "test-password".toCharArray(), employee);
            broker.registerQuadrant(manager, new Quadrant(employee, new Place("Office", "Office", 0, 0),
                    List.of(LocalDate.of(2026, 10, 27)), List.of(LocalTime.of(9, 0)), ZoneOffset.UTC));
        }
        String original = Files.readString(file);
        for (String key : List.of("users", "requests", "quadrants", "quadrant.0.entries")) {
            var properties = new Properties();
            properties.load(new StringReader(original));
            properties.setProperty(key, "-1");
            try (var output = Files.newOutputStream(file)) { properties.store(output, "test"); }
            String corrupt = Files.readString(file);
            assertThrows(IOException.class, () -> {
                try (var ignored = new AttendanceBroker(file, clock)) { }
            }, key);
            assertEquals(corrupt, Files.readString(file));
            Files.writeString(file, original);
            try (var reopened = new AttendanceBroker(file, clock)) {
                assertFalse(reopened.needsSetup());
            }
        }
    }
}
