package br.com.nfse.importadorpn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class AppImportadorPnCliTest {
    @TempDir
    Path tempDir;

    @Test
    void ajudaMostraComandoParaExecutarJanelasReais() {
        CommandLine commandLine = new CommandLine(new AppImportadorPn());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute("--help");

        assertThat(exitCode).isZero();
        assertThat(output.toString()).contains("executar-janelas");
    }

    @Test
    void simularJanelasNaoMarcaAgendaComoExecutada() {
        CommandLine commandLine = new CommandLine(new AppImportadorPn());
        Path backend = tempDir.resolve("backend");

        int exitCode = commandLine.execute(
                "simular-janelas",
                "--backend", backend.toString(),
                "--agora", "2026-05-08T13:00");

        assertThat(exitCode).isZero();
        assertThat(Files.exists(backend.resolve("agenda").resolve("2026-05.tsv"))).isFalse();
    }

    @Test
    void reconciliacaoUsaLimiteDeLotesSuficienteParaChegarNoMesAtual() {
        CommandLine commandLine = new CommandLine(new AppImportadorPn());

        String padraoReconciliar = commandLine.getSubcommands().get("reconciliar")
                .getCommandSpec()
                .findOption("--max-lotes")
                .defaultValue();
        String padraoExecutarJanelas = commandLine.getSubcommands().get("executar-janelas")
                .getCommandSpec()
                .findOption("--max-lotes")
                .defaultValue();

        assertThat(Integer.parseInt(padraoReconciliar)).isGreaterThanOrEqualTo(200);
        assertThat(Integer.parseInt(padraoExecutarJanelas)).isGreaterThanOrEqualTo(200);
    }

    @Test
    void registraComandoVerificarTudo() {
        CommandLine commandLine = new CommandLine(new AppImportadorPn());

        assertThat(commandLine.getSubcommands()).containsKey("verificar-tudo");
    }

    @Test
    void excecaoDeComandoSaiComoBloqueadoENaoAtencao() {
        CommandLine commandLine = AppImportadorPn.commandLine();

        int exitCode = commandLine.execute(
                "validar-cadastro",
                "--planilha", tempDir.resolve("inexistente.xlsm").toString());

        assertThat(exitCode).isEqualTo(2);
    }
}
