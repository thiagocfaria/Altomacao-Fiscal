package br.com.nfse.renomeador.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileHashServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void calculatesSha256ForFileContent() throws Exception {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "abc");

        String hash = new FileHashService().sha256(file);

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
