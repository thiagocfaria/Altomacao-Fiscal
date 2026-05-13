package br.com.nfse.importadorpn.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgendadorJanelasTest {
    @TempDir
    Path tempDir;

    @Test
    void agendaJanelaPerdidaDasCincoQuandoComputadorLigaAsOito() throws Exception {
        ControleJanelaExecutada controle = new ControleJanelaExecutada(tempDir.resolve("agenda"));
        AgendadorJanelas agendador = new AgendadorJanelas(controle);

        List<JanelaExecucao> pendentes = agendador.janelasPendentes(LocalDateTime.of(2026, 5, 8, 8, 0));

        assertThat(pendentes).extracting(JanelaExecucao::identificador)
                .containsExactly("2026-05-08T05:00");
    }

    @Test
    void agendaCincoEDozeEmOrdemQuandoComputadorLigaAsTreze() throws Exception {
        ControleJanelaExecutada controle = new ControleJanelaExecutada(tempDir.resolve("agenda"));
        AgendadorJanelas agendador = new AgendadorJanelas(controle);

        List<JanelaExecucao> pendentes = agendador.janelasPendentes(LocalDateTime.of(2026, 5, 8, 13, 0));

        assertThat(pendentes).extracting(JanelaExecucao::identificador)
                .containsExactly("2026-05-08T05:00", "2026-05-08T12:00");
    }

    @Test
    void naoAgendaJanelaJaMarcadaComoExecutada() throws Exception {
        ControleJanelaExecutada controle = new ControleJanelaExecutada(tempDir.resolve("agenda"));
        controle.marcarExecutada(new JanelaExecucao(LocalDate.of(2026, 5, 8), HorarioJanela.CINCO));
        AgendadorJanelas agendador = new AgendadorJanelas(controle);

        List<JanelaExecucao> pendentes = agendador.janelasPendentes(LocalDateTime.of(2026, 5, 8, 13, 0));

        assertThat(pendentes).extracting(JanelaExecucao::identificador)
                .containsExactly("2026-05-08T12:00");
    }

    @Test
    void limitaRecuperacaoDeDiasAntigosParaNaoRodarBacklogSemControle() throws Exception {
        ControleJanelaExecutada controle = new ControleJanelaExecutada(tempDir.resolve("agenda"));
        AgendadorJanelas agendador = new AgendadorJanelas(controle);

        List<JanelaExecucao> pendentes = agendador.janelasPendentes(LocalDateTime.of(2026, 5, 10, 18, 0));

        assertThat(pendentes).extracting(JanelaExecucao::identificador)
                .containsExactly("2026-05-10T05:00", "2026-05-10T12:00", "2026-05-10T17:00");
    }
}
