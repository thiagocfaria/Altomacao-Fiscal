package br.com.nfse.renomeador;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AppCliTest {
    @Test
    void cliShowsBatchAndWatchCommandsInHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void cliParsesBatchHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("batch", "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void cliParsesConfigImportExcelHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("config", "import-excel", "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void cliParsesConfigPrepareExcelHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("config", "preparar-planilha", "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void cliParsesConfigCheckHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("config", "check", "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void cliParsesConfigPreflightHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("config", "preflight", "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void cliParsesMaintenanceCleanupHelp() {
        int exitCode = new CommandLine(new App.Cli()).execute("manutencao", "limpar-tecnicos", "--help");

        assertThat(exitCode).isZero();
    }

    @Test
    void batchFindsSharedSpreadsheetAtProjectRootWhenConfigIsInModuleOperationFolder(@TempDir Path tempDir)
            throws Exception {
        Path moduleOperation = tempDir.resolve("RENOMEADOR").resolve("operacao");
        Files.createDirectories(moduleOperation);
        Path spreadsheet = tempDir.resolve("PLANILHA_FISCAL.xlsm");
        Files.writeString(spreadsheet, "placeholder");
        App.BatchCommand command = new App.BatchCommand();
        command.config = moduleOperation.resolve("empresas.yaml");

        assertThat(command.spreadsheet()).contains(spreadsheet);
    }

    @Test
    void batchCanUseAlreadyValidatedYamlWithoutRefreshingSpreadsheet(@TempDir Path tempDir)
            throws Exception {
        Path moduleOperation = tempDir.resolve("RENOMEADOR").resolve("operacao");
        Files.createDirectories(moduleOperation);
        Files.writeString(tempDir.resolve("PLANILHA_FISCAL.xlsm"), "placeholder");
        App.BatchCommand command = new App.BatchCommand();
        command.config = moduleOperation.resolve("empresas.yaml");
        command.skipSpreadsheetRefresh = true;

        assertThat(command.spreadsheet()).isEmpty();
    }

    @Test
    void cliReportsOperationalErrorsWithoutJavaStackTrace(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                empresas:
                  - id: empresa_a
                    habilitada: true
                    cnpjTomador: "123"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "."
                """.formatted(tempDir.toString().replace("\\", "/")));
        StringWriter errors = new StringWriter();
        CommandLine commandLine = App.commandLine();
        commandLine.setErr(new PrintWriter(errors));

        int exitCode = commandLine.execute("config", "check", "--config", config.toString());

        assertThat(exitCode).isEqualTo(2);
        assertThat(errors.toString())
                .contains("ERRO:")
                .contains("CNPJ invalido")
                .doesNotContain("at br.com");
    }

    @Test
    void configPreflightAprovaConfiguracaoValida(@TempDir Path tempDir) throws Exception {
        Path config = validConfig(tempDir);
        StringWriter out = new StringWriter();
        CommandLine commandLine = App.commandLine();
        commandLine.setOut(new PrintWriter(out));

        int exitCode = commandLine.execute("config", "preflight", "--config", config.toString(),
                "--mes", "2026-05");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("PREFLIGHT RENOMEADOR")
                .contains("Status: OK")
                .contains("Watch registravel: SIM");
    }

    @Test
    void configPreflightFalhaComLockOcupado(@TempDir Path tempDir) throws Exception {
        Path config = validConfig(tempDir);
        try (br.com.nfse.renomeador.app.ApplicationLock ignored =
                     br.com.nfse.renomeador.app.ApplicationLock.acquire(config)) {
            int exitCode = App.commandLine().execute("config", "preflight", "--config", config.toString(),
                    "--mes", "2026-05");

            assertThat(exitCode).isEqualTo(3);
        }
    }

    @Test
    void configPreflightFalhaComEntradaTecnicaInvalida(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                empresas:
                  - id: origem_import_api_pn
                    habilitada: true
                    somenteOrigem: true
                    cnpjTomador: "123"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "entrada-inexistente"
                """.formatted(tempDir.toString().replace("\\", "/")));

        int exitCode = App.commandLine().execute("config", "preflight", "--config", config.toString(),
                "--mes", "2026-05");

        assertThat(exitCode).isEqualTo(2);
    }

    private static Path validConfig(Path tempDir) throws Exception {
        Path origem = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path destino = Files.createDirectories(tempDir.resolve("destino"));
        Path backend = tempDir.resolve("backend");
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                backendRoot: "%s"
                empresas:
                  - id: origem_import_api_pn
                    habilitada: true
                    somenteOrigem: true
                    cnpjTomador: "123"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "."
                  - id: empresa_a
                    habilitada: true
                    cnpjTomador: "25.014.360/0001-73"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "."
                """.formatted(
                backend.toString().replace("\\", "/"),
                origem.toString().replace("\\", "/"),
                destino.toString().replace("\\", "/")));
        return config;
    }
}
