package br.com.nfse.importadorpn.agenda;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AgendadorJanelas {
    private final ControleJanelaExecutada controle;

    public AgendadorJanelas(ControleJanelaExecutada controle) {
        this.controle = controle;
    }

    public List<JanelaExecucao> janelasPendentes(LocalDateTime agora) throws IOException {
        LocalDate data = agora.toLocalDate();
        List<JanelaExecucao> pendentes = new ArrayList<>();
        for (HorarioJanela horario : HorarioJanela.values()) {
            JanelaExecucao janela = new JanelaExecucao(data, horario);
            if (!janela.dataHora().isAfter(agora) && !controle.jaExecutada(janela)) {
                pendentes.add(janela);
            }
        }
        return List.copyOf(pendentes);
    }
}
