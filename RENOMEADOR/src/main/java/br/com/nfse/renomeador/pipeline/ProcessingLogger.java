package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class ProcessingLogger {
    private final TechnicalRetentionPolicy retentionPolicy;

    public ProcessingLogger() {
        this(new TechnicalRetentionPolicy());
    }

    ProcessingLogger(TechnicalRetentionPolicy retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }

    public void record(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath, FileProcessingResult result) throws IOException {
        Path log = logFile(routes, companyPath);
        Files.createDirectories(log.getParent());
        appendLine(log, lineFor(result));
    }

    public void recordSummary(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath,
                              ProcessingSummary summary) throws IOException {
        Path log = logFile(routes, companyPath);
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
        appendLine(log, line);
        applyRetention(log.getParent());
    }

    private static Path logFile(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return TechnicalPaths.log(routes, companyPath);
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

    private static void appendLine(Path log, String line) throws IOException {
        try (FileChannel channel = FileChannel.open(log,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void applyRetention(Path logDirectory) {
        try {
            retentionPolicy.apply(logDirectory);
        } catch (IOException ignored) {
            // A retencao nao pode transformar um processamento correto em erro operacional.
        }
    }
}
