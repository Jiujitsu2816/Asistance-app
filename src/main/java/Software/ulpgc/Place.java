package Software.ulpgc;

/** A workplace. x and y are local map coordinates, not GPS coordinates. */
public record Place(String name, String type, int x, int y) {
    public Place {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Place name is required");
        if (type == null || type.isBlank()) throw new IllegalArgumentException("Place type is required");
    }
}
