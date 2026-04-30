package br.com.nfse.renomeador.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalArchiveServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void copiesOriginalBeforeProcessing() throws Exception {
        Path source = tempDir.resolve("entrada").resolve("nota.pdf");
        Path originals = tempDir.resolve("originais");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "conteudo");

        Path archived = new OriginalArchiveService().archive(source, originals);

        assertThat(archived).isEqualTo(originals.resolve("nota.pdf"));
        assertThat(Files.readString(archived)).isEqualTo("conteudo");
        assertThat(Files.readString(source)).isEqualTo("conteudo");
    }

    @Test
    void addsIncrementalSuffixWhenOriginalNameAlreadyExists() throws Exception {
        Path source = tempDir.resolve("entrada").resolve("nota.pdf");
        Path originals = tempDir.resolve("originais");
        Files.createDirectories(source.getParent());
        Files.createDirectories(originals);
        Files.writeString(source, "novo");
        Files.writeString(originals.resolve("nota.pdf"), "existente");

        Path archived = new OriginalArchiveService().archive(source, originals);

        assertThat(archived).isEqualTo(originals.resolve("nota_01.pdf"));
        assertThat(Files.readString(archived)).isEqualTo("novo");
    }
}
