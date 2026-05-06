package br.com.nfse.renomeador;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

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
}
