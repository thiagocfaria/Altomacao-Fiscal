package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DestinationService {
    public DestinationResult send(Path source, ResolvedCompanyPath companyPath, ProcessingStatus status,
                                  String fileName, boolean preserveInput) throws IOException {
        Path directory = destinationDirectory(companyPath, status);
        Files.createDirectories(directory);
        Path destination = nextAvailable(directory.resolve(fileName));
        if (preserveInput) {
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            move(source, destination);
        }
        return new DestinationResult(destination, preserveInput);
    }

    public DestinationResult sendTechnicalError(Path source, ResolvedCompanyPath companyPath,
                                                boolean preserveInput) throws IOException {
        String fileName = "ERRO_PROCESSAMENTO_" + source.getFileName();
        return send(source, companyPath, ProcessingStatus.MISSING_REQUIRED, fileName, preserveInput);
    }

    private static Path destinationDirectory(ResolvedCompanyPath companyPath, ProcessingStatus status) {
        return switch (status) {
            case OK -> PathsForCompany.processed(companyPath);
            case CANCELLED -> PathsForCompany.cancelled(companyPath);
            case UNSUPPORTED, WRONG_COMPANY, MISSING_REQUIRED, RETENTION_CONFLICT -> PathsForCompany.review(companyPath);
        };
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
        throw new IllegalStateException("Nao foi possivel gerar nome unico para destino: " + desired);
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }
}
