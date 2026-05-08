package br.com.nfse.renomeador.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VerifiedFileMoverTest {
    @TempDir
    Path tempDir;

    @Test
    void copyVerifyDeleteKeepsOnlyVerifiedDestination() throws Exception {
        Path source = tempDir.resolve("origem.pdf");
        Path destination = tempDir.resolve("destino").resolve("final.pdf");
        Files.writeString(source, "conteudo fiscal");

        VerifiedFileMover.copyVerifyDelete(source, destination);

        assertThat(source).doesNotExist();
        assertThat(destination).hasContent("conteudo fiscal");
        assertThat(destination.resolveSibling(destination.getFileName() + ".tmp")).doesNotExist();
    }
}
