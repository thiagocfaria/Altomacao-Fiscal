package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import br.com.nfse.importadorpn.ledger.RepositorioImportacao;
import br.com.nfse.importadorpn.publicacao.PublicadorDmsDireto;
import br.com.nfse.importadorpn.publicacao.PublicadorRestEntrada;
import br.com.nfse.importadorpn.publicacao.PublicadorRespostaAdn;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReconciliacaoDestinoBehaviorTest {
    private static final String CHAVE =
            "12345678901234567890123456789012345678901234567890";
    private static final YearMonth MES = YearMonth.of(2026, 5);

    @TempDir
    Path tempDir;

    @Test
    void reconciliacaoSegueDestinoFinalENaoLedgerQuandoArquivoSome() throws Exception {
        Path backend = tempDir.resolve("backend");
        Path entradaRest = backend.resolve("entrada-rest");
        Path rest = tempDir.resolve("cliente").resolve("REST");
        Path dms = tempDir.resolve("cliente").resolve("DMS");
        Path xmlFinal = rest.resolve("XML").resolve("processados").resolve("nota.xml");
        Path pdfFinal = rest.resolve("PDF").resolve("processados").resolve("nota.pdf");
        Path dmsFinal = dms.resolve("PN_25014360000173_NSU_123_202605.xml");
        Files.createDirectories(xmlFinal.getParent());
        Files.createDirectories(pdfFinal.getParent());
        Files.createDirectories(dms);

        String xml = "<NFSe><chNFSe>" + CHAVE
                + "</chNFSe><dhEmi>2026-05-11T09:00:00-03:00</dhEmi>"
                + "<prest><CNPJ>25014360000173</CNPJ></prest></NFSe>";
        Files.writeString(xmlFinal, xml);
        Files.writeString(pdfFinal, "%PDF-1.4\nja estava no destino");
        Files.writeString(dmsFinal, xml);

        EmpresaImportacao empresa = empresa(rest, dms);
        String json = loteJson(xml);
        ChavesPresentesNoDestino scanner = new ChavesPresentesNoDestino();

        RegistroConsultaAdn sincronizado = registro(backend, entradaRest, documento -> {
            throw new AssertionError("Nao deve baixar DANFSe quando destino final esta completo");
        });
        ResultadoRegistroConsulta semImportar = sincronizado.registrar(empresa, "1",
                new ResultadoConsultaAdn(200, "application/json", json.getBytes(StandardCharsets.UTF_8)),
                MES, Instant.parse("2026-05-11T12:00:00Z"), scanner.escanearEstado(empresa, MES));

        assertThat(semImportar.documentosPublicados()).isZero();
        assertThat(Files.exists(entradaRest)).isFalse();

        Files.delete(pdfFinal);
        byte[] pdfReimportado = "%PDF-1.4\nreimportado".getBytes(StandardCharsets.UTF_8);
        RegistroConsultaAdn pdfFaltante = registro(backend, entradaRest,
                documento -> Optional.of(new ResultadoConsultaAdn(200, "application/pdf", pdfReimportado)));

        ResultadoRegistroConsulta importouPdf = pdfFaltante.registrar(empresa, "1",
                new ResultadoConsultaAdn(200, "application/json", json.getBytes(StandardCharsets.UTF_8)),
                MES, Instant.parse("2026-05-11T12:01:00Z"), scanner.escanearEstado(empresa, MES));

        assertThat(importouPdf.documentosPublicados()).isEqualTo(1);
        assertThat(Files.readAllBytes(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf")))
                .isEqualTo(pdfReimportado);
        assertThat(Files.exists(entradaRest.resolve("PN_25014360000173_NSU_123_202605.xml"))).isFalse();

        Files.writeString(pdfFinal, "%PDF-1.4\nrenomeador moveu de novo");
        Files.delete(dmsFinal);
        Files.delete(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf"));

        ResultadoRegistroConsulta importouDms = pdfFaltante.registrar(empresa, "1",
                new ResultadoConsultaAdn(200, "application/json", json.getBytes(StandardCharsets.UTF_8)),
                MES, Instant.parse("2026-05-11T12:02:00Z"), scanner.escanearEstado(empresa, MES));

        assertThat(importouDms.documentosPublicados()).isEqualTo(1);
        assertThat(Files.readString(dmsFinal)).isEqualTo(xml);
        assertThat(Files.exists(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf"))).isFalse();
        assertThat(Files.exists(entradaRest.resolve("PN_25014360000173_NSU_123_202605.xml"))).isFalse();
    }

    private RegistroConsultaAdn registro(Path backend, Path entradaRest, ConsultaDanfse consultaDanfse) {
        return new RegistroConsultaAdn(
                new RepositorioImportacao(backend.resolve("ledger"), MES),
                new PublicadorRespostaAdn(backend),
                new PublicadorRestEntrada(entradaRest),
                new PublicadorDmsDireto(),
                consultaDanfse,
                true);
    }

    private static EmpresaImportacao empresa(Path rest, Path dms) {
        return new EmpresaImportacao("DGA ENERGIA", "25014360000173",
                Optional.of(rest), Optional.of(dms),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), "CADASTRO MAIO", 54);
    }

    private static String loteJson(String xml) throws Exception {
        return """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "%s",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(CHAVE, base64Gzip(xml));
    }

    private static String base64Gzip(String texto) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
