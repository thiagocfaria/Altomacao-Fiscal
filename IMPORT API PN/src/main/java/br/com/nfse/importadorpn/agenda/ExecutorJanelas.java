package br.com.nfse.importadorpn.agenda;

import br.com.nfse.importadorpn.execucao.BloqueioExecucao;
import java.nio.channels.FileLock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class ExecutorJanelas {
    private final AgendadorJanelas agendador;
    private final ControleJanelaExecutada controle;
    private final BloqueioExecucao bloqueio;
    private final ProcessadorJanela processador;

    public ExecutorJanelas(AgendadorJanelas agendador, ControleJanelaExecutada controle,
                           BloqueioExecucao bloqueio, ProcessadorJanela processador) {
        this.agendador = agendador;
        this.controle = controle;
        this.bloqueio = bloqueio;
        this.processador = processador;
    }

    public ResultadoExecucaoJanelas executarPendentes(LocalDateTime agora) throws Exception {
        return processarPendentes(agora, true);
    }

    public ResultadoExecucaoJanelas simularPendentes(LocalDateTime agora) throws Exception {
        return processarPendentes(agora, false);
    }

    private ResultadoExecucaoJanelas processarPendentes(LocalDateTime agora, boolean marcarExecutadas) throws Exception {
        Optional<FileLock> lock = bloqueio.tentarAdquirir();
        if (lock.isEmpty()) {
            return new ResultadoExecucaoJanelas(0, 0, true);
        }
        try (FileLock ignored = lock.get()) {
            List<JanelaExecucao> pendentes = agendador.janelasPendentes(agora);
            int executadas = 0;
            for (JanelaExecucao janela : pendentes) {
                processador.processar(janela);
                if (marcarExecutadas) {
                    controle.marcarExecutada(janela);
                }
                executadas++;
            }
            return new ResultadoExecucaoJanelas(pendentes.size(), executadas, false);
        }
    }
}
