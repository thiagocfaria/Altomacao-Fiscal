package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconciliadorPortalDestinoTest {
    @TempDir
    Path tempDir;

    @Test
    void respeitaMaxLotesEIndicaTruncamentoQuandoAindaHaProximoNsu() throws Exception {
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.empty());
        AtomicInteger consultas = new AtomicInteger();
        ReconciliadorPortalDestino reconciliador = new ReconciliadorPortalDestino();

        ResultadoReconciliacaoPortal resultado = reconciliador.executar(
                cadastro,
                YearMonth.of(2026, 5),
                "1",
                1,
                (empresa, nsu) -> {
                    consultas.incrementAndGet();
                    return jsonLote("2", "12345678901234567890123456789012345678901234567890");
                },
                (empresa, nsu, consulta, estadoDestino, documentos, agora) ->
                        ResultadoProcessamentoLote.importados(documentos.size()),
                Instant.parse("2026-05-12T12:00:00Z"));

        assertThat(consultas).hasValue(1);
        assertThat(resultado.truncadoPorMaxLotes()).isTrue();
        assertThat(resultado.totalLotesConsultados()).isEqualTo(1);
        assertThat(resultado.totalDocumentosPortal()).isEqualTo(1);
        assertThat(resultado.totalDocumentosAfetados()).isEqualTo(1);
    }

    @Test
    void http404EmDfeEncerraVarreduraSemErroExterno() throws Exception {
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.empty());
        AtomicInteger consultas = new AtomicInteger();
        AtomicInteger processamentos = new AtomicInteger();
        ReconciliadorPortalDestino reconciliador = new ReconciliadorPortalDestino();

        ResultadoReconciliacaoPortal resultado = reconciliador.executar(
                cadastro,
                YearMonth.of(2026, 5),
                "1277",
                5,
                (empresa, nsu) -> {
                    consultas.incrementAndGet();
                    return new ResultadoConsultaAdn(404, "application/json",
                            "{}".getBytes(StandardCharsets.UTF_8));
                },
                (empresa, nsu, consulta, estadoDestino, documentos, agora) -> {
                    processamentos.incrementAndGet();
                    return ResultadoProcessamentoLote.importados(documentos.size());
                },
                Instant.parse("2026-05-12T12:00:00Z"));

        assertThat(consultas).hasValue(1);
        assertThat(processamentos).hasValue(0);
        assertThat(resultado.erroExternoPortal()).isFalse();
        assertThat(resultado.totalLotesConsultados()).isEqualTo(1);
        assertThat(resultado.totalDocumentosPortal()).isZero();
        assertThat(resultado.totalDocumentosAfetados()).isZero();
    }

    private static ResultadoConsultaAdn jsonLote(String nsu, String chave) throws Exception {
        String xml = """
                <NFSe>
                  <infNFSe>
                    <dhEmi>2026-05-09T10:00:00-03:00</dhEmi>
                  </infNFSe>
                </NFSe>
                """;
        String json = """
                {"LoteDFe":[{"NSU":%s,"ChaveAcesso":"%s","TipoDocumento":"NFSE","ArquivoXml":"%s"}]}
                """.formatted(nsu, chave, base64Gzip(xml));
        return new ResultadoConsultaAdn(200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static EmpresaImportacao empresa(Path rest, Path dms) {
        return new EmpresaImportacao("DGA ENERGIA", "25014360000173",
                Optional.of(rest), Optional.of(dms),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), "CADASTRO MAIO", 54);
    }

    private static String base64Gzip(String texto) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
