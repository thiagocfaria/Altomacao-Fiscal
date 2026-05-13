package br.com.nfse.importadorpn.agenda;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public record JanelaExecucao(LocalDate data, HorarioJanela horario) {
    public String identificador() {
        return data + "T" + horario.horario();
    }

    public LocalDateTime dataHora() {
        return LocalDateTime.of(data, horario.horario());
    }

    public YearMonth mes() {
        return YearMonth.from(data);
    }
}
