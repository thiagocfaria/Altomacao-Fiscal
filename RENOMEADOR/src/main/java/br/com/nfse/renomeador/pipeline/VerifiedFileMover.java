package br.com.nfse.renomeador.pipeline;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class VerifiedFileMover {
    private VerifiedFileMover() {
    }

    static void move(Path source, Path destination) throws IOException {
        if (sameFileStore(source, destination)) {
            try {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (AtomicMoveNotSupportedException exception) {
                copyVerifyDelete(source, destination);
                return;
            }
        }
        copyVerifyDelete(source, destination);
    }

    static void copyVerifyDelete(Path source, Path destination) throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Path temporary = temporaryPath(destination);
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        verifyCopy(source, temporary);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        verifyCopy(source, destination);
        Files.delete(source);
    }

    private static boolean sameFileStore(Path source, Path destination) {
        try {
            FileStore sourceStore = Files.getFileStore(source);
            Path destinationParent = destination.getParent();
            FileStore destinationStore = Files.getFileStore(destinationParent == null ? destination.toAbsolutePath().getParent() : destinationParent);
            return sourceStore.equals(destinationStore);
        } catch (IOException exception) {
            return false;
        }
    }

    private static void verifyCopy(Path source, Path destination) throws IOException {
        if (Files.size(source) != Files.size(destination)) {
            throw new IOException("Copia verificada falhou por tamanho diferente: " + source + " -> " + destination);
        }
        if (Files.mismatch(source, destination) != -1L) {
            throw new IOException("Copia verificada falhou por conteudo diferente: " + source + " -> " + destination);
        }
    }

    private static Path temporaryPath(Path destination) {
        return destination.resolveSibling(destination.getFileName() + ".tmp");
    }
}
