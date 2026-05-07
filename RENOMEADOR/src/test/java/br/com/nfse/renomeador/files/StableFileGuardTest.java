package br.com.nfse.renomeador.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class StableFileGuardTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsReadableFileWhoseSizeAndTimestampStayStable() throws Exception {
        Path file = tempDir.resolve("nota.pdf");
        Files.writeString(file, "conteudo");

        boolean stable = new StableFileGuard().isStable(file, Duration.ZERO, 2);

        assertThat(stable).isTrue();
    }

    @Test
    void rejectsMissingFile() {
        boolean stable = new StableFileGuard().isStable(tempDir.resolve("ausente.pdf"), Duration.ZERO, 2);

        assertThat(stable).isFalse();
    }
}
