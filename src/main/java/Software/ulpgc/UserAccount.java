package Software.ulpgc;

/** Stored identity and protected credentials; never contains a plaintext password. */
public record UserAccount(String username, String salt, String passwordHash,
                          AttendanceBroker.Role role, Employe employee) { }
