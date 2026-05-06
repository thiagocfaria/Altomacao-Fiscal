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
}
