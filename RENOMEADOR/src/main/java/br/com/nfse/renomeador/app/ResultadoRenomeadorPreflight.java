package br.com.nfse.renomeador.app;

import java.nio.file.Path;

public record ResultadoRenomeadorPreflight(
        int rotasMonitoradas,
        int watchesRegistrados,
        Path backendRoot) {
}
