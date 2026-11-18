package software.ulpgc.presencia.frontend.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Capturas para el README. Solo se guardan si se ejecutan las pruebas con
 * -Dcapturas.dir=RUTA; en una ejecucion normal no se escribe nada.
 */
public final class Screenshots {
    private Screenshots() { }

    public static Path directory() {
        String dir = System.getProperty("capturas.dir");
        return dir == null || dir.isBlank() || dir.startsWith("${") ? null : Path.of(dir);
    }

    public static void save(Node node, String name) throws Exception {
        Path dir = directory();
        if (dir == null) return;
        CompletableFuture<WritableImage> image = new CompletableFuture<>();
        Platform.runLater(() -> image.complete(node.snapshot(null, null)));
        write(image.get(10, TimeUnit.SECONDS), dir.resolve(name + ".png"));
    }

    private static void write(WritableImage image, Path file) throws IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                buffered.setRGB(x, y, reader.getArgb(x, y));
        Files.createDirectories(file.getParent());
        ImageIO.write(buffered, "png", file.toFile());
    }
}
