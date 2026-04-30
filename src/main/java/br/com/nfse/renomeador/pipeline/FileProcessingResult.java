package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.nio.file.Path;

public record FileProcessingResult(
        String companyId,
        Path source,
        ProcessingStatus status,
        boolean skipped,
        String reason,
        Path destination,
        Exception error
) {
    public static FileProcessingResult skipped(String companyId, Path source, String reason) {
        return new FileProcessingResult(companyId, source, null, true, reason, null, null);
    }

    public static FileProcessingResult processed(String companyId, Path source, ProcessingStatus status,
                                                 String reason, Path destination) {
        return new FileProcessingResult(companyId, source, status, false, reason, destination, null);
    }

    public static FileProcessingResult failed(String companyId, Path source, String reason, Path destination,
                                              Exception error) {
        return new FileProcessingResult(companyId, source, ProcessingStatus.MISSING_REQUIRED, false,
                reason, destination, error);
    }
}
