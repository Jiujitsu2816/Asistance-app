package Software.ulpgc;

public record Employe(String name, String lastname, int id) {
    public Employe {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Employee name is required");
        if (lastname == null || lastname.isBlank()) throw new IllegalArgumentException("Employee last name is required");
        if (id <= 0) throw new IllegalArgumentException("Employee ID must be positive");
    }
}
