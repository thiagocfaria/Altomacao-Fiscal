package br.com.nfse.importadorpn.manutencao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public final class ManutencaoRetencao {
    private final Path backend;

    public ManutencaoRetencao(Path backend) {
        this.backend = backend;
    }

    public ResultadoManutencao executar(LocalDate hoje, int diasLogSemCompactar, int mesesLedgerManter)
            throws IOException {
        int logs = compactarLogsAntigos(hoje, diasLogSemCompactar);
        int ledgers = removerLedgersAntigos(YearMonth.from(hoje), mesesLedgerManter);
        return new ResultadoManutencao(logs, ledgers);
    }

    private int compactarLogsAntigos(LocalDate hoje, int diasLogSemCompactar) throws IOException {
        Path logsDir = backend.resolve("logs");
        if (!Files.isDirectory(logsDir)) {
            return 0;
        }
        int compactados = 0;
        try (Stream<Path> paths = Files.list(logsDir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".jsonl")).toList()) {
                LocalDate data = dataLog(path);
                if (data != null && data.isBefore(hoje.minusDays(diasLogSemCompactar))) {
                    compactar(path);
                    compactados++;
                }
            }
        }
        return compactados;
    }

    private int removerLedgersAntigos(YearMonth mesAtual, int mesesLedgerManter) throws IOException {
        Path ledgerDir = backend.resolve("ledger");
        if (!Files.isDirectory(ledgerDir)) {
            return 0;
        }
        YearMonth menorMesMantido = mesAtual.minusMonths(mesesLedgerManter - 1L);
        int removidos = 0;
        try (Stream<Path> paths = Files.list(ledgerDir)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".tsv")).toList()) {
                YearMonth mes = mesLedger(path);
                if (mes != null && mes.isBefore(menorMesMantido)) {
                    Files.delete(path);
                    removidos++;
                }
            }
        }
        return removidos;
    }

    private static void compactar(Path origem) throws IOException {
        Path destino = origem.resolveSibling(origem.getFileName() + ".gz");
        try (InputStream input = Files.newInputStream(origem);
             OutputStream output = new GZIPOutputStream(Files.newOutputStream(destino))) {
            input.transferTo(output);
        }
        Files.delete(origem);
    }

    private static LocalDate dataLog(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".jsonl") || name.length() < 16) {
            return null;
        }
        try {
            return LocalDate.parse(name.substring(0, 10));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static YearMonth mesLedger(Path path) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".tsv") || name.length() < 11) {
            return null;
        }
        try {
            return YearMonth.parse(name.substring(0, 7));
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
