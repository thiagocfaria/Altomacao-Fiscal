package br.com.nfse.importadorpn.prevoo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrevooVerificarTudoTest {
    @TempDir
    Path tempDir;

    @Test
    void bloqueiaBackendInexistente() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.of(entradaRest));

        ResultadoPrevoo resultado = new PrevooVerificarTudo()
                .verificar(cadastro, YearMonth.of(2026, 5), tempDir.resolve("backend-inexistente"));

        assertThat(resultado.podeLigar()).isFalse();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.BLOQUEADO);
        assertThat(resultado.problemas())
                .anySatisfy(problema -> assertThat(problema.mensagem()).contains("Backend"));
    }

    @Test
    void zeroEmpresasAtivasApontaAbaMesEColunaApiPn() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path backend = Files.createDirectories(tempDir.resolve("backend"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(), Optional.of(entradaRest));

        ResultadoPrevoo resultado = new PrevooVerificarTudo()
                .verificar(cadastro, YearMonth.of(2026, 4), backend);

        assertThat(resultado.podeLigar()).isFalse();
        assertThat(resultado.problemas())
                .anySatisfy(problema -> {
                    assertThat(problema.mensagem()).contains("Nenhuma empresa ativa");
                    assertThat(problema.ondeCorrigir()).contains("CADASTRO ABRIL", "IMPORT API PN ATIVO");
                    assertThat(problema.acao()).contains("Marque SIM");
                });
    }

    private static EmpresaImportacao empresa(Path rest, Path dms) {
        return new EmpresaImportacao(
                "DGA ENERGIA",
                "25014360000173",
                Optional.of(rest),
                Optional.of(dms),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "CADASTRO MAIO",
                10);
    }
}
