package br.com.nfse.renomeador.app;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WatchModeRunnerTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

    @TempDir
    Path tempDir;

    @Test
    void watchReloadsConfigAndRecoversTomadorNaoEncontradoWhenRestPathAppears() throws Exception {
        Path sourceRoot = tempDir.resolve("origem_generica");
        Path targetRoot = tempDir.resolve("empresa_correta");
        Files.createDirectories(sourceRoot.resolve("entrada"));
        Path pendingFolder = Files.createDirectories(sourceRoot.resolve("TOMADOR NAO ENCONTRADO"));
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), pendingFolder.resolve("pendente.pdf"));
        Path config = tempDir.resolve("empresas.yaml");
        writeWatchConfig(config, sourceRoot, targetRoot, false);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread watcher = new Thread(() -> {
            try {
                new WatchModeRunner().run(config, Optional.empty(), Optional.empty(), false);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        watcher.setDaemon(true);
        watcher.start();

        await(() -> Files.exists(tempDir.resolve("backend").resolve("empresas")
                .resolve("origem_generica").resolve("execucao-" + YearMonth.now() + ".tsv")));

        Files.createDirectories(targetRoot.resolve("entrada"));
        writeWatchConfig(config, sourceRoot, targetRoot, true);

        await(() -> Files.isDirectory(targetRoot.resolve("processados"))
                && !Files.exists(sourceRoot.resolve("TOMADOR NAO ENCONTRADO")));
        watcher.interrupt();
        watcher.join(3_000);

        assertThat(failure.get()).isNull();
        assertThat(targetRoot.resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
    }

    @Test
    void watchImportsSpreadsheetAgainWhenPlanilhaIsSavedWithNewRestPath() throws Exception {
        Path sourceRoot = tempDir.resolve("origem_generica");
        Path targetRoot = tempDir.resolve("empresa_correta");
        Files.createDirectories(sourceRoot.resolve("entrada"));
        Path pendingFolder = Files.createDirectories(sourceRoot.resolve("TOMADOR NAO ENCONTRADO"));
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), pendingFolder.resolve("pendente.pdf"));
        Path spreadsheet = tempDir.resolve("PLANILHA_FISCAL.xlsx");
        Path config = tempDir.resolve("empresas.yaml");
        writeSpreadsheet(spreadsheet, sourceRoot, targetRoot, false);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread watcher = new Thread(() -> {
            try {
                new WatchModeRunner().run(config, Optional.empty(), Optional.empty(), false, Optional.of(spreadsheet));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        watcher.setDaemon(true);
        watcher.start();

        await(() -> Files.exists(tempDir.resolve("backend").resolve("empresas")
                .resolve("origem_generica").resolve("execucao-" + YearMonth.now() + ".tsv")));

        Files.createDirectories(targetRoot.resolve("entrada"));
        writeSpreadsheet(spreadsheet, sourceRoot, targetRoot, true);

        await(() -> Files.isDirectory(targetRoot.resolve("processados"))
                && !Files.exists(sourceRoot.resolve("TOMADOR NAO ENCONTRADO")));
        watcher.interrupt();
        watcher.join(3_000);

        assertThat(failure.get()).isNull();
        assertThat(Files.readString(config)).contains(targetRoot.toString().replace("\\", "/"));
        assertThat(targetRoot.resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
    }

    private static void await(CheckedBoolean condition) throws Exception {
        Instant deadline = Instant.now().plusSeconds(8);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Condicao nao atendida dentro do tempo limite");
    }

    private static void writeWatchConfig(Path config, Path sourceRoot, Path targetRoot,
                                         boolean targetEnabled) throws Exception {
        String targetBase = targetEnabled ? targetRoot.toString().replace("\\", "/") : ".";
        Files.writeString(config, """
                empresas:
                  - id: origem_generica
                    habilitada: true
                    somenteOrigem: true
                    cnpjTomador: "000"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      canceladas: "canceladas"
                  - id: empresa_correta
                    habilitada: %s
                    cnpjTomador: "25.014.360/0001-73"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      canceladas: "canceladas"
                """.formatted(
                sourceRoot.toString().replace("\\", "/"),
                targetEnabled,
                targetBase));
    }

    private static void writeSpreadsheet(Path spreadsheet, Path sourceRoot, Path targetRoot,
                                         boolean targetPathFilled) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("empresas");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("empresa");
            header.createCell(1).setCellValue("cnpj");
            header.createCell(2).setCellValue("caminho");
            header.createCell(3).setCellValue("somente origem");

            Row source = sheet.createRow(1);
            source.createCell(0).setCellValue("Origem Generica");
            source.createCell(1).setCellValue("000");
            source.createCell(2).setCellValue(sourceRoot.toString());
            source.createCell(3).setCellValue("SIM");

            Row target = sheet.createRow(2);
            target.createCell(0).setCellValue("Empresa Correta");
            target.createCell(1).setCellValue("25.014.360/0001-73");
            target.createCell(2).setCellValue(targetPathFilled ? targetRoot.toString() : "");

            try (var output = Files.newOutputStream(spreadsheet)) {
                workbook.write(output);
            }
        }
    }

    @FunctionalInterface
    private interface CheckedBoolean {
        boolean getAsBoolean() throws Exception;
    }
}
