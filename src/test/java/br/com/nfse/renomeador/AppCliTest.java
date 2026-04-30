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
}
