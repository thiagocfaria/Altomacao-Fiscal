package br.com.nfse.importadorpn.prevoo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.certificado.ResultadoCertificado;
import br.com.nfse.importadorpn.certificado.ResultadoSenhaCertificado;
import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificacaoCertificadosPrevooTest {
    @TempDir
    Path tempDir;

    @Test
    void aprovaCertificadoComCnpjDaPlanilha() {
        EmpresaImportacao empresa = empresa("11222333000181");
        VerificacaoCertificadosPrevoo verificacao = new VerificacaoCertificadosPrevoo(
                (alias, senhaPlanilha) -> ResultadoSenhaCertificado.encontrada("senha", "teste"),
                (arquivo, senha) -> ResultadoCertificado.valido(
                        "ok", 1, Optional.of(Instant.parse("2026-12-01T00:00:00Z")),
                        Set.of("11222333000181")));

        ResultadoPrevoo resultado = verificacao.verificar(new CadastroImportacao(List.of(empresa), Optional.empty()));

        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.OK);
        assertThat(resultado.problemas()).isEmpty();
    }

    @Test
    void bloqueiaCertificadoComCnpjDiferenteDaPlanilha() {
        EmpresaImportacao empresa = empresa("11222333000181");
        VerificacaoCertificadosPrevoo verificacao = new VerificacaoCertificadosPrevoo(
                (alias, senhaPlanilha) -> ResultadoSenhaCertificado.encontrada("senha", "teste"),
                (arquivo, senha) -> ResultadoCertificado.valido(
                        "ok", 1, Optional.of(Instant.parse("2026-12-01T00:00:00Z")),
                        Set.of("99888777000166")));

        ResultadoPrevoo resultado = verificacao.verificar(new CadastroImportacao(List.of(empresa), Optional.empty()));

        assertThat(resultado.podeLigar()).isFalse();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.BLOQUEADO);
        assertThat(resultado.problemas())
                .anySatisfy(problema -> assertThat(problema.mensagem())
                        .contains("CNPJ do certificado", "11222333000181", "99888777000166"));
    }

    @Test
    void bloqueiaCertificadoSemCnpjConfirmado() {
        EmpresaImportacao empresa = empresa("11222333000181");
        VerificacaoCertificadosPrevoo verificacao = new VerificacaoCertificadosPrevoo(
                (alias, senhaPlanilha) -> ResultadoSenhaCertificado.encontrada("senha", "teste"),
                (arquivo, senha) -> ResultadoCertificado.valido(
                        "ok", 1, Optional.of(Instant.parse("2026-12-01T00:00:00Z")),
                        Set.of()));

        ResultadoPrevoo resultado = verificacao.verificar(new CadastroImportacao(List.of(empresa), Optional.empty()));

        assertThat(resultado.podeLigar()).isFalse();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.BLOQUEADO);
        assertThat(resultado.problemas())
                .anySatisfy(problema -> assertThat(problema.mensagem()).contains("sem CNPJ confirmado"));
    }

    @Test
    void reutilizaValidacaoQuandoEmpresasUsamMesmoCertificadoEMesmaSenha() {
        AtomicInteger aberturas = new AtomicInteger();
        EmpresaImportacao primeira = empresa("11222333000181");
        EmpresaImportacao segunda = empresa("11222333000181");
        VerificacaoCertificadosPrevoo verificacao = new VerificacaoCertificadosPrevoo(
                (alias, senhaPlanilha) -> ResultadoSenhaCertificado.encontrada("senha", "teste"),
                (arquivo, senha) -> {
                    aberturas.incrementAndGet();
                    return ResultadoCertificado.valido(
                            "ok", 1, Optional.of(Instant.parse("2026-12-01T00:00:00Z")),
                            Set.of("11222333000181"));
                });

        ResultadoPrevoo resultado = verificacao.verificar(new CadastroImportacao(
                List.of(primeira, segunda), Optional.empty()));

        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.OK);
        assertThat(aberturas).hasValue(1);
    }

    private EmpresaImportacao empresa(String cnpj) {
        return new EmpresaImportacao(
                "Cliente",
                cnpj,
                Optional.empty(),
                Optional.empty(),
                Optional.of(tempDir),
                Optional.of("cliente.pfx"),
                Optional.of(cnpj),
                Optional.empty(),
                Optional.empty(),
                "CADASTRO MAIO",
                2);
    }
}
