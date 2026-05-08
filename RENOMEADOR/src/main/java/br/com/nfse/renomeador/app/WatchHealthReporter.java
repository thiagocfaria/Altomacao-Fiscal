package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

final class WatchHealthReporter {
    void record(CompanyRouteDirectory routes, ProcessingSummary summary, String message) throws IOException {
        Path healthFile = routes.backendRoot().resolve("health").resolve("watch-status.json");
        Files.createDirectories(healthFile.getParent());
        Path tempFile = healthFile.resolveSibling(healthFile.getFileName() + ".tmp");
        Files.writeString(tempFile, json(routes, summary, message), StandardCharsets.UTF_8);
        try {
            Files.move(tempFile, healthFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempFile, healthFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String json(CompanyRouteDirectory routes, ProcessingSummary summary, String message) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576L;
        String status = summary.needsAttention() ? "ATENCAO" : "OK";
        return """
                {"status":"%s","ultimoPulso":"%s","pastasMonitoradas":%d,"empresasAtivas":%d,"memoriaMb":%d,"total":%d,"revisar":%d,"ignorados":%d,"erros":%d,"mensagem":"%s"}%n""".formatted(
                status,
                Instant.now(),
                routes.monitoredPaths().size(),
                routes.activePaths().size(),
                usedMemoryMb,
                summary.total(),
                summary.reviewCount(),
                summary.skipped(),
                summary.errors(),
                escape(message)
        );
    }

    private static String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
