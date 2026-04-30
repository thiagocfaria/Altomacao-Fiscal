package br.com.nfse.renomeador.config;

import java.util.Locale;

public enum MonthStrategy {
    CURRENT,
    INFORMED,
    LIST,
    DIRECT;

    public static MonthStrategy fromConfig(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("estrategiaMes e obrigatoria");
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "atual" -> CURRENT;
            case "informado" -> INFORMED;
            case "lista" -> LIST;
            case "direto" -> DIRECT;
            default -> throw new IllegalArgumentException("estrategiaMes invalida: " + value);
        };
    }
}
