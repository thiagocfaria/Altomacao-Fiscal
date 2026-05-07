package br.com.nfse.renomeador.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalRetentionPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void compressesClosedMonthsAndDeletesExpiredOperationLogs() throws Exception {
        YearMonth current = YearMonth.now();
        YearMonth previous = current.minusMonths(1);
        YearMonth expired = current.minusMonths(13);
        Path currentLog = logFile(current, ".tsv");
        Path previousLog = logFile(previous, ".tsv");
        Path expiredLog = logFile(expired, ".tsv");
        Files.writeString(currentLog, "current");
        Files.writeString(previousLog, "previous");
        Files.writeString(expiredLog, "expired");

        new TechnicalRetentionPolicy(12, 1_000_000L).apply(tempDir);

        assertThat(currentLog).exists();
        assertThat(previousLog).doesNotExist();
        assertThat(logFile(previous, ".tsv.gz")).exists();
        assertThat(expiredLog).doesNotExist();
        assertThat(logFile(expired, ".tsv.gz")).doesNotExist();
    }

    @Test
    void deletesOldestClosedLogsWhenCompanyOperationLogsExceedSizeCap() throws Exception {
        YearMonth current = YearMonth.now();
        YearMonth older = current.minusMonths(2);
        YearMonth newer = current.minusMonths(1);
        Path currentLog = logFile(current, ".tsv");
        Path olderLog = logFile(older, ".tsv.gz");
        Path newerLog = logFile(newer, ".tsv.gz");
        Files.writeString(currentLog, "current-log");
        Files.writeString(olderLog, "x".repeat(60));
        Files.writeString(newerLog, "y".repeat(60));

        new TechnicalRetentionPolicy(12, 90L).apply(tempDir);

        assertThat(currentLog).exists();
        assertThat(olderLog).doesNotExist();
        assertThat(newerLog).exists();
    }

    private Path logFile(YearMonth month, String suffix) {
        return tempDir.resolve("execucao-" + month + suffix);
    }
}
