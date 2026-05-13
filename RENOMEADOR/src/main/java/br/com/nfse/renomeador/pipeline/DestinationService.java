package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class DestinationService {
    public static final String RETAINED_FOLDER = "RETIDO";
    public static final String MISSING_CUSTOMER_FOLDER = "TOMADOR NAO ENCONTRADO";

    public DestinationResult send(Path source, ResolvedCompanyPath companyPath, ProcessingStatus status,
                                  String fileName, boolean preserveInput) throws IOException {
        return send(source, companyPath, status, fileName, preserveInput, false, DocumentType.PDF,
                CompanyRouteDirectory.single(companyPath));
    }

    public DestinationResult send(Path source, ResolvedCompanyPath companyPath, ProcessingStatus status,
                                  String fileName, boolean preserveInput, boolean retained,
                                  CompanyRouteDirectory routes) throws IOException {
        return send(source, companyPath, status, fileName, preserveInput, retained, DocumentType.PDF, routes);
    }

    public DestinationResult send(Path source, ResolvedCompanyPath companyPath, ProcessingStatus status,
                                  String fileName, boolean preserveInput, boolean retained,
                                  DocumentType documentType, CompanyRouteDirectory routes) throws IOException {
        Path directory = destinationDirectory(companyPath, status, retained, documentType, routes);
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
        return sendTechnicalError(source, companyPath, preserveInput, CompanyRouteDirectory.single(companyPath));
    }

    public DestinationResult sendTechnicalError(Path source, ResolvedCompanyPath companyPath,
                                                boolean preserveInput, CompanyRouteDirectory routes) throws IOException {
        String fileName = "ERRO_PROCESSAMENTO_" + source.getFileName();
        return send(source, companyPath, ProcessingStatus.MISSING_REQUIRED, fileName, preserveInput, false, routes);
    }

    public DestinationResult sendToReview(Path source, ResolvedCompanyPath companyPath, String fileName,
                                          boolean preserveInput) throws IOException {
        return sendToReview(source, companyPath, fileName, preserveInput, CompanyRouteDirectory.single(companyPath));
    }

    public DestinationResult sendToReview(Path source, ResolvedCompanyPath companyPath, String fileName,
                                          boolean preserveInput, CompanyRouteDirectory routes) throws IOException {
        Path directory = TechnicalPaths.review(routes, companyPath);
        return sendToDirectory(source, directory, fileName, preserveInput);
    }

    public DestinationResult sendToMissingCustomer(Path source, ResolvedCompanyPath companyPath, String fileName,
                                                   boolean preserveInput) throws IOException {
        return sendToMissingCustomer(source, companyPath, fileName, preserveInput, DocumentType.PDF);
    }

    public DestinationResult sendToMissingCustomer(Path source, ResolvedCompanyPath companyPath, String fileName,
                                                   boolean preserveInput, DocumentType documentType) throws IOException {
        return sendToMissingCustomer(source, companyPath, fileName, preserveInput, documentType,
                CompanyRouteDirectory.single(companyPath));
    }

    public DestinationResult sendToMissingCustomer(Path source, ResolvedCompanyPath companyPath, String fileName,
                                                   boolean preserveInput, DocumentType documentType,
                                                   CompanyRouteDirectory routes) throws IOException {
        Path directory = companyPath.company().sourceOnly()
                ? TechnicalPaths.review(routes, companyPath)
                : documentType.folderUnder(companyPath.root()).resolve(MISSING_CUSTOMER_FOLDER);
        return sendToDirectory(source, directory, fileName, preserveInput);
    }

    private static DestinationResult sendToDirectory(Path source, Path directory, String fileName,
                                                     boolean preserveInput) throws IOException {
        Files.createDirectories(directory);
        Path destination = nextAvailable(directory.resolve(fileName));
        if (preserveInput) {
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        } else {
            move(source, destination);
        }
        return new DestinationResult(destination, preserveInput);
    }

    private static Path destinationDirectory(ResolvedCompanyPath companyPath, ProcessingStatus status,
                                             boolean retained, DocumentType documentType, CompanyRouteDirectory routes) {
        Path documentRoot = documentType.folderUnder(companyPath.root());
        return switch (status) {
            case OK -> retained ? documentRoot.resolve(RETAINED_FOLDER) : documentRoot.resolve(companyPath.company().folders().processed());
            case CANCELLED -> documentRoot.resolve(companyPath.company().folders().cancelled());
            case UNSUPPORTED, WRONG_COMPANY, MISSING_REQUIRED, RETENTION_CONFLICT, DUPLICATE -> TechnicalPaths.review(routes, companyPath);
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
        VerifiedFileMover.move(source, destination);
    }
}
