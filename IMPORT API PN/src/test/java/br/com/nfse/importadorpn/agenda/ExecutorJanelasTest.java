package br.com.nfse.importadorpn.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.execucao.BloqueioExecucao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutorJanelasTest {
    @TempDir
    Path tempDir;

    @Test
    void executaJanelasPendentesMarcaComoExecutadasENaoRepete() throws Exception {
        ControleJanelaExecutada controle = new ControleJanelaExecutada(tempDir.resolve("agenda"));
        AgendadorJanelas agendador = new AgendadorJanelas(controle);
        List<String> executadas = new ArrayList<>();
        ExecutorJanelas executor = new ExecutorJanelas(agendador, controle,
                new BloqueioExecucao(tempDir.resolve("importador.lock")),
                janela -> executadas.add(janela.identificador()));

        ResultadoExecucaoJanelas primeira = executor.executarPendentes(LocalDateTime.of(2026, 5, 8, 13, 0));
        ResultadoExecucaoJanelas segunda = executor.executarPendentes(LocalDateTime.of(2026, 5, 8, 13, 0));

        assertThat(primeira.executadas()).isEqualTo(2);
        assertThat(segunda.executadas()).isZero();
        assertThat(executadas).containsExactly("2026-05-08T05:00", "2026-05-08T12:00");
        assertThat(Files.readString(tempDir.resolve("agenda").resolve("2026-05.tsv")))
                .contains("2026-05-08T05:00")
                .contains("2026-05-08T12:00");
    }

    @Test
    void naoExecutaQuandoLockJaEstaEmUso() throws Exception {
        ControleJanelaExecutada controle = new ControleJanelaExecutada(tempDir.resolve("agenda"));
        AgendadorJanelas agendador = new AgendadorJanelas(controle);
        BloqueioExecucao bloqueio = new BloqueioExecucao(tempDir.resolve("importador.lock"));
        ExecutorJanelas executor = new ExecutorJanelas(agendador, controle, bloqueio, janela -> {
            throw new AssertionError("Nao deveria executar com lock ocupado");
        });

        try (var ignored = bloqueio.tentarAdquirir().orElseThrow()) {
            ResultadoExecucaoJanelas resultado = executor.executarPendentes(LocalDateTime.of(2026, 5, 8, 13, 0));
            assertThat(resultado.lockOcupado()).isTrue();
            assertThat(resultado.executadas()).isZero();
        }
    }
}
