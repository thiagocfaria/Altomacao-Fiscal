package br.com.nfse.importadorpn.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidadorCadastroTest {
    @TempDir
    Path tempDir;

    @Test
    void aprovaCadastroMinimoComRestDmsEntradaRestECertificadoValido() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        Path certs = Files.createDirectories(tempDir.resolve("certs"));
        Files.writeString(certs.resolve("cliente.pfx"), "certificado falso");
        EmpresaImportacao empresa = empresa("Cliente", "11222333000181", rest, dms, certs,
                "cliente.pfx", LocalDate.now().plusDays(40));

        ResultadoValidacao resultado = new ValidadorCadastro().validar(new CadastroImportacao(List.of(empresa),
                Optional.of(entradaRest)));

        assertThat(resultado.aprovado()).isTrue();
        assertThat(resultado.erros()).isEmpty();
        assertThat(resultado.totalEmpresas()).isEqualTo(1);
    }

    @Test
    void reprovaCadastroSemEntradaRestECaminhosObrigatorios() {
        EmpresaImportacao empresa = empresa("Cliente", "11222333000181", tempDir.resolve("rest-inexistente"),
                tempDir.resolve("dms-inexistente"), tempDir.resolve("certs"), "cliente.pfx",
                LocalDate.now().minusDays(1));

        ResultadoValidacao resultado = new ValidadorCadastro().validar(new CadastroImportacao(List.of(empresa),
                Optional.empty()));

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.erros()).anySatisfy(erro -> assertThat(erro.mensagem()).contains("entrada-rest"));
        assertThat(resultado.erros()).anySatisfy(erro -> assertThat(erro.mensagem()).contains("CAMINHO REST"));
        assertThat(resultado.erros()).anySatisfy(erro -> assertThat(erro.mensagem()).contains("CAMINHO DMS"));
        assertThat(resultado.erros()).anySatisfy(erro -> assertThat(erro.mensagem()).contains("certificado"));
        assertThat(resultado.erros()).anySatisfy(erro -> assertThat(erro.mensagem()).contains("vencido"));
    }

    @Test
    void reprovaCnpjDuplicadoComCaminhoDmsDivergente() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dmsA = Files.createDirectories(tempDir.resolve("dms-a"));
        Path dmsB = Files.createDirectories(tempDir.resolve("dms-b"));
        Path certs = Files.createDirectories(tempDir.resolve("certs"));
        Files.writeString(certs.resolve("cliente.pfx"), "certificado falso");

        ResultadoValidacao resultado = new ValidadorCadastro().validar(new CadastroImportacao(List.of(
                empresa("Cliente A", "11222333000181", rest, dmsA, certs, "cliente.pfx",
                        LocalDate.now().plusDays(40)),
                empresa("Cliente A repetido", "11222333000181", rest, dmsB, certs, "cliente.pfx",
                        LocalDate.now().plusDays(40))
        ), Optional.of(entradaRest)));

        assertThat(resultado.aprovado()).isFalse();
        assertThat(resultado.erros()).anySatisfy(erro -> assertThat(erro.mensagem()).contains("DMS conflitante"));
    }

    private static EmpresaImportacao empresa(String nome, String cnpj, Path rest, Path dms, Path certs,
                                             String certArquivo, LocalDate validade) {
        return new EmpresaImportacao(nome, cnpj, Optional.of(rest), Optional.of(dms), Optional.of(certs),
                Optional.of(certArquivo), Optional.of("alias"), Optional.empty(), Optional.of(validade),
                "CADASTRO MAIO", 2);
    }
}
