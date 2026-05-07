package br.com.nfse.renomeador.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

final class TechnicalRetentionPolicy {
    static final int DEFAULT_LOG_RETENTION_MONTHS = 12;
    static final long DEFAULT_MAX_OPERATION_LOG_BYTES_PER_COMPANY = 100L * 1024L * 1024L;

    private static final Pattern OPERATION_LOG_PATTERN =
            Pattern.compile("^execucao-(\\d{4})-(\\d{2})\\.tsv(\\.gz)?$");

    private final int retentionMonths;
    private final long maxOperationLogBytesPerCompany;

    TechnicalRetentionPolicy() {
        this(DEFAULT_LOG_RETENTION_MONTHS, DEFAULT_MAX_OPERATION_LOG_BYTES_PER_COMPANY);
    }

    TechnicalRetentionPolicy(int retentionMonths, long maxOperationLogBytesPerCompany) {
        this.retentionMonths = Math.max(1, retentionMonths);
        this.maxOperationLogBytesPerCompany = Math.max(1L, maxOperationLogBytesPerCompany);
    }

    void apply(Path logDirectory) throws IOException {
        if (!Files.isDirectory(logDirectory)) {
            return;
        }
        YearMonth currentMonth = YearMonth.now();
        compressClosedMonths(logDirectory, currentMonth);
        deleteExpired(logDirectory, currentMonth);
        enforceSizeCap(logDirectory, currentMonth);
    }

    private void compressClosedMonths(Path logDirectory, YearMonth currentMonth) throws IOException {
        for (LogFile logFile : listLogFiles(logDirectory)) {
            if (logFile.compressed() || !logFile.month().isBefore(currentMonth)) {
                continue;
            }
            Path gzipPath = logFile.path().resolveSibling(logFile.path().getFileName() + ".gz");
            if (Files.exists(gzipPath)) {
                Files.deleteIfExists(logFile.path());
                continue;
            }
            Path tempPath = logFile.path().resolveSibling(logFile.path().getFileName() + ".gz.tmp");
            try (InputStream input = Files.newInputStream(logFile.path());
                 OutputStream output = new GZIPOutputStream(Files.newOutputStream(tempPath))) {
                input.transferTo(output);
            }
            Files.move(tempPath, gzipPath, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(logFile.path());
        }
    }

    private void deleteExpired(Path logDirectory, YearMonth currentMonth) throws IOException {
        YearMonth oldestKeptMonth = currentMonth.minusMonths(retentionMonths - 1L);
        for (LogFile logFile : listLogFiles(logDirectory)) {
            if (logFile.month().isBefore(oldestKeptMonth)) {
                Files.deleteIfExists(logFile.path());
            }
        }
    }

    private void enforceSizeCap(Path logDirectory, YearMonth currentMonth) throws IOException {
        List<LogFile> logFiles = listLogFiles(logDirectory);
        long totalBytes = logFiles.stream().mapToLong(LogFile::size).sum();
        if (totalBytes <= maxOperationLogBytesPerCompany) {
            return;
        }
        List<LogFile> deletionCandidates = logFiles.stream()
                .filter(logFile -> logFile.month().isBefore(currentMonth))
                .sorted(Comparator.comparing(LogFile::month)
                        .thenComparing(logFile -> logFile.path().getFileName().toString()))
                .toList();
        for (LogFile logFile : deletionCandidates) {
            Files.deleteIfExists(logFile.path());
            totalBytes -= logFile.size();
            if (totalBytes <= maxOperationLogBytesPerCompany) {
                return;
            }
        }
    }

    private static List<LogFile> listLogFiles(Path logDirectory) throws IOException {
        try (var stream = Files.list(logDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(TechnicalRetentionPolicy::parseLogFile)
                    .flatMap(java.util.Optional::stream)
                    .toList();
        }
    }

    private static java.util.Optional<LogFile> parseLogFile(Path path) {
        Matcher matcher = OPERATION_LOG_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return java.util.Optional.empty();
        }
        try {
            YearMonth month = YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            return java.util.Optional.of(new LogFile(path, month, matcher.group(3) != null, Files.size(path)));
        } catch (IOException | RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private record LogFile(Path path, YearMonth month, boolean compressed, long size) {
    }
}
