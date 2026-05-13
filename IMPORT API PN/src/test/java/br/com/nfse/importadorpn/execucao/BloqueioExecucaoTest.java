package br.com.nfse.importadorpn.execucao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.FileLock;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BloqueioExecucaoTest {
    @TempDir
    Path tempDir;

    @Test
    void impedeDuasExecucoesSimultaneas() throws Exception {
        BloqueioExecucao bloqueio = new BloqueioExecucao(tempDir.resolve("importador.lock"));

        try (FileLock primeiro = bloqueio.tentarAdquirir().orElseThrow()) {
            assertThat(primeiro.isValid()).isTrue();
            assertThat(bloqueio.tentarAdquirir()).isEmpty();
        }

        try (FileLock segundo = bloqueio.tentarAdquirir().orElseThrow()) {
            assertThat(segundo.isValid()).isTrue();
        }
    }
}
