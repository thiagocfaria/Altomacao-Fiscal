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
        Exception error,
        long durationMillis
) {
    public static FileProcessingResult skipped(String companyId, Path source, String reason) {
        return skipped(companyId, source, reason, 0L);
    }

    public static FileProcessingResult skipped(String companyId, Path source, String reason, long durationMillis) {
        return new FileProcessingResult(companyId, source, null, true, reason, null, null, durationMillis);
    }

    public static FileProcessingResult processed(String companyId, Path source, ProcessingStatus status,
                                                 String reason, Path destination) {
        return processed(companyId, source, status, reason, destination, 0L);
    }

    public static FileProcessingResult processed(String companyId, Path source, ProcessingStatus status,
                                                 String reason, Path destination, long durationMillis) {
        return new FileProcessingResult(companyId, source, status, false, reason, destination, null, durationMillis);
    }

    public static FileProcessingResult failed(String companyId, Path source, String reason, Path destination,
                                              Exception error) {
        return failed(companyId, source, reason, destination, error, 0L);
    }

    public static FileProcessingResult failed(String companyId, Path source, String reason, Path destination,
                                              Exception error, long durationMillis) {
        return new FileProcessingResult(companyId, source, ProcessingStatus.MISSING_REQUIRED, false,
                reason, destination, error, durationMillis);
    }

    public FileProcessingResult withDurationMillis(long durationMillis) {
        return new FileProcessingResult(companyId, source, status, skipped, reason, destination, error, durationMillis);
    }
}
