package br.com.nfse.importadorpn.prevoo;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimuladorReconciliacaoDryRunTest {
    private static final String CHAVE = "12345678901234567890123456789012345678901234567890";

    @TempDir
    Path tempDir;

    @Test
    void dryRunContaFaltantesSemGravarEntradaRestDmsOuLedger() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path backend = Files.createDirectories(tempDir.resolve("backend"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.of(entradaRest));
        SimuladorReconciliacaoDryRun simulador = new SimuladorReconciliacaoDryRun(
                (empresa, nsu) -> jsonLote("2", CHAVE, "2026-05-09T10:00:00-03:00"));

        ResultadoDryRunReconciliacao resultado = simulador.simular(
                cadastro, cadastro, YearMonth.of(2026, 5), "1", 1);

        assertThat(resultado.totalDocumentosPortal()).isEqualTo(1);
        assertThat(resultado.totalXmlRestFaltantes()).isEqualTo(1);
        assertThat(resultado.totalPdfRestFaltantes()).isEqualTo(1);
        assertThat(resultado.totalDmsFaltantes()).isEqualTo(1);
        assertThat(Files.list(entradaRest)).isEmpty();
        assertThat(Files.list(dms)).isEmpty();
        assertThat(backend.resolve("ledger")).doesNotExist();
    }

    @Test
    void maxLotesAtingidoViraAtencao() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.of(entradaRest));
        SimuladorReconciliacaoDryRun simulador = new SimuladorReconciliacaoDryRun(
                (empresa, nsu) -> jsonLote("2", CHAVE, "2026-05-09T10:00:00-03:00"));

        ResultadoDryRunReconciliacao resultado = simulador.simular(
                cadastro, cadastro, YearMonth.of(2026, 5), "1", 1);

        assertThat(resultado.truncadoPorMaxLotes()).isTrue();
        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.ATENCAO);
    }

    @Test
    void portalNao200ViraErroExternoNoDryRun() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.of(entradaRest));
        SimuladorReconciliacaoDryRun simulador = new SimuladorReconciliacaoDryRun(
                (empresa, nsu) -> new ResultadoConsultaAdn(500, "text/html", "<html/>".getBytes(StandardCharsets.UTF_8)));

        ResultadoDryRunReconciliacao resultado = simulador.simular(
                cadastro, cadastro, YearMonth.of(2026, 5), "1", 1);

        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.ERRO_EXTERNO);
        assertThat(resultado.totalLotesConsultados()).isEqualTo(1);
    }

    @Test
    void portal404EmDfeViraParadaNaturalNoDryRun() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.of(entradaRest));
        SimuladorReconciliacaoDryRun simulador = new SimuladorReconciliacaoDryRun(
                (empresa, nsu) -> new ResultadoConsultaAdn(404, "application/json",
                        "{}".getBytes(StandardCharsets.UTF_8)));

        ResultadoDryRunReconciliacao resultado = simulador.simular(
                cadastro, cadastro, YearMonth.of(2026, 5), "1277", 5);

        assertThat(resultado.nivel()).isEqualTo(NivelPrevoo.OK);
        assertThat(resultado.totalLotesConsultados()).isEqualTo(1);
        assertThat(resultado.totalDocumentosPortal()).isZero();
    }

    @Test
    void dryRunNaoBloqueiaPorTomadorSemRotaQuandoDocumentoPertenceAoCnpjConsulta() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path rest = Files.createDirectories(tempDir.resolve("rest"));
        Path dms = Files.createDirectories(tempDir.resolve("dms"));
        CadastroImportacao cadastro = new CadastroImportacao(List.of(empresa(rest, dms)), Optional.of(entradaRest));
        SimuladorReconciliacaoDryRun simulador = new SimuladorReconciliacaoDryRun(
                (empresa, nsu) -> jsonLote("7", CHAVE, "2026-05-09T10:00:00-03:00", "25014360000173",
                        "99999999000191"));

        ResultadoDryRunReconciliacao resultado = simulador.simular(
                cadastro, cadastro, YearMonth.of(2026, 5), "1", 1);

        assertThat(resultado.totalRotaDmsAusente()).isZero();
        assertThat(resultado.rotasDmsAusentes()).isEmpty();
        assertThat(resultado.totalDmsFaltantes()).isEqualTo(1);
    }

    private static ResultadoConsultaAdn jsonLote(String nsu, String chave, String emissao) throws Exception {
        return jsonLote(nsu, chave, emissao, "25014360000173", "25014360000173");
    }

    private static ResultadoConsultaAdn jsonLote(String nsu, String chave, String emissao, String cnpjPrestador,
                                                String cnpjTomador) throws Exception {
        String xml = """
                <NFSe>
                  <infNFSe>
                    <dhEmi>%s</dhEmi>
                    <prest><CNPJ>%s</CNPJ></prest>
                    <toma><CNPJ>%s</CNPJ></toma>
                  </infNFSe>
                </NFSe>
                """.formatted(emissao, cnpjPrestador, cnpjTomador);
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
