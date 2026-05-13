package br.com.nfse.importadorpn.prevoo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificacaoCaminhosTest {
    @TempDir
    Path tempDir;

    @Test
    void bloqueiaCaminhoRestInexistente() {
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path dms = tempDir.resolve("dms");
        criarDiretorio(entradaRest);
        criarDiretorio(dms);
        EmpresaImportacao empresa = empresa(tempDir.resolve("rest-inexistente"), dms);
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa), Optional.of(entradaRest));

        ResultadoPrevoo resultado = new VerificacaoCaminhos().verificar(cadastro);

        assertThat(resultado.podeLigar()).isFalse();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.BLOQUEADO);
        assertThat(resultado.problemas())
                .anySatisfy(problema -> {
                    assertThat(problema.mensagem()).contains("CAMINHO REST");
                    assertThat(problema.ondeCorrigir()).contains("PLANILHA_FISCAL.xlsm");
                    assertThat(problema.acao()).contains("VERIFICAR TUDO");
                });
    }

    @Test
    void aprovaCaminhosExistentesEGravaveis() {
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path rest = tempDir.resolve("rest");
        Path dms = tempDir.resolve("dms");
        criarDiretorio(entradaRest);
        criarDiretorio(rest);
        criarDiretorio(dms);
        EmpresaImportacao empresa = empresa(rest, dms);
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa), Optional.of(entradaRest));

        ResultadoPrevoo resultado = new VerificacaoCaminhos().verificar(cadastro);

        assertThat(resultado.podeLigar()).isTrue();
        assertThat(resultado.problemas()).isEmpty();
        assertThat(Files.exists(rest.resolve("XML").resolve("processados"))).isFalse();
        assertThat(Files.exists(rest.resolve("PDF").resolve("processados"))).isFalse();
        assertThat(resultado.informacoes())
                .anySatisfy(info -> assertThat(info).contains("subpastas REST ausentes", "podem ser criadas"));
    }

    @Test
    void sinalizaAtencaoQuandoEntradaRestTemArquivosSoltos() throws Exception {
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path rest = tempDir.resolve("rest");
        Path dms = tempDir.resolve("dms");
        criarDiretorio(entradaRest);
        criarDiretorio(rest);
        criarDiretorio(dms);
        Files.writeString(entradaRest.resolve("pendente.xml"), "<xml/>");
        EmpresaImportacao empresa = empresa(rest, dms);
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa), Optional.of(entradaRest));

        ResultadoPrevoo resultado = new VerificacaoCaminhos().verificar(cadastro);

        assertThat(resultado.podeLigar()).isTrue();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.ATENCAO);
        assertThat(resultado.problemas())
                .anySatisfy(problema -> assertThat(problema.mensagem()).contains("Entrada REST contem 1 arquivo"));
    }

    @Test
    void bloqueiaSubpastaRestInvalidaMesmoQuandoOutraSubpastaAindaNaoExiste() throws Exception {
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path rest = tempDir.resolve("rest");
        Path dms = tempDir.resolve("dms");
        criarDiretorio(entradaRest);
        criarDiretorio(rest);
        criarDiretorio(dms);
        criarDiretorio(rest.resolve("PDF"));
        Files.writeString(rest.resolve("PDF").resolve("processados"), "arquivo indevido");
        EmpresaImportacao empresa = empresa(rest, dms);
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa), Optional.of(entradaRest));

        ResultadoPrevoo resultado = new VerificacaoCaminhos().verificar(cadastro);

        assertThat(resultado.podeLigar()).isFalse();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.BLOQUEADO);
        assertThat(resultado.problemas())
                .anySatisfy(problema -> assertThat(problema.mensagem())
                        .contains("Subpasta REST existe mas nao e pasta", "PDF/processados"));
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

    private static void criarDiretorio(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
