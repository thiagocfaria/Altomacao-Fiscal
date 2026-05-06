package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class ProcessingLogger {
    public void record(ResolvedCompanyPath companyPath, FileProcessingResult result) throws IOException {
        Path log = logFile(companyPath);
        Files.createDirectories(log.getParent());
        Files.writeString(log, lineFor(result), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public void recordSummary(ResolvedCompanyPath companyPath, ProcessingSummary summary) throws IOException {
        Path log = logFile(companyPath);
        Files.createDirectories(log.getParent());
        String line = "%s\tSUMMARY\ttotal=%d\tok=%d\trevisar=%d\tcanceladas=%d\tduplicadas=%d\tignorados=%d\terros=%d%n".formatted(
                Instant.now(),
                summary.total(),
                summary.count(ProcessingStatus.OK),
                summary.count(ProcessingStatus.UNSUPPORTED)
                        + summary.count(ProcessingStatus.WRONG_COMPANY)
                        + summary.count(ProcessingStatus.MISSING_REQUIRED)
                        + summary.count(ProcessingStatus.RETENTION_CONFLICT),
                summary.count(ProcessingStatus.CANCELLED),
                summary.count(ProcessingStatus.DUPLICATE),
                summary.skipped(),
                summary.errors()
        );
        Files.writeString(log, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static Path logFile(ResolvedCompanyPath companyPath) {
        return PathsForCompany.logs(companyPath).resolve("execucao.log");
    }

    private static String lineFor(FileProcessingResult result) {
        String status = result.skipped() ? "SKIPPED" : String.valueOf(result.status());
        String destination = result.destination() == null ? "" : result.destination().toString();
        String error = result.error() == null ? "" : result.error().getClass().getSimpleName() + ": " + result.error().getMessage();
        return "%s\t%s\t%s\t%s\t%s\t%s\tduracaoMs=%d\t%s%n".formatted(
                Instant.now(),
                result.companyId(),
                result.source(),
                status,
                result.reason(),
                destination,
                result.durationMillis(),
                error
        );
    }
}
