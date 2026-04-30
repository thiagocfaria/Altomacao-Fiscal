package br.com.nfse.renomeador.files;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class OriginalArchiveService {
    public Path archive(Path source, Path originalsDirectory) throws IOException {
        Files.createDirectories(originalsDirectory);
        Path destination = nextAvailable(originalsDirectory.resolve(source.getFileName()));
        Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        return destination;
    }

    private static Path nextAvailable(Path desired) {
        if (!Files.exists(desired)) {
            return desired;
        }
        String fileName = desired.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        Path parent = desired.getParent();
        for (int counter = 1; counter < 10_000; counter++) {
            Path candidate = parent.resolve("%s_%02d%s".formatted(base, counter, extension));
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar nome unico para original: " + desired);
    }
}
