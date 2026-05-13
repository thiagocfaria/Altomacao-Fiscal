package br.com.nfse.importadorpn.agenda;

import java.time.LocalTime;

public enum HorarioJanela {
    CINCO(LocalTime.of(5, 0)),
    DOZE(LocalTime.of(12, 0)),
    DEZESSETE(LocalTime.of(17, 0));

    private final LocalTime horario;

    HorarioJanela(LocalTime horario) {
        this.horario = horario;
    }

    public LocalTime horario() {
        return horario;
    }
}
