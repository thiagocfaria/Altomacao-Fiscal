package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import br.com.nfse.importadorpn.ledger.EstadoDocumento;
import br.com.nfse.importadorpn.ledger.RegistroImportacao;
import br.com.nfse.importadorpn.ledger.RepositorioImportacao;
import br.com.nfse.importadorpn.publicacao.PublicadorDmsDireto;
import br.com.nfse.importadorpn.publicacao.PublicadorRestEntrada;
import br.com.nfse.importadorpn.publicacao.PublicadorRespostaAdn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Base64;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistroConsultaAdnTest {
    @TempDir
    Path tempDir;

    @Test
    void resposta200XmlSalvaArquivoEAtualizaLedgerSemConcluirDms() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest));

        ResultadoRegistroConsulta resultado = registro.registrar(empresa(), "10",
                new ResultadoConsultaAdn(200, "application/xml", "<xml/>".getBytes()),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(resultado.publicado()).isTrue();
        Optional<RegistroImportacao> salvo = repositorio.buscar("25014360000173", "10", "NSU-10");
        assertThat(salvo).get().extracting(RegistroImportacao::estadoXml).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo).get().extracting(RegistroImportacao::estadoDms).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo).get().extracting(RegistroImportacao::caminhoRestFinal)
                .asString().contains("entrada-rest/PN_25014360000173_NSU_10_202605.xml");
        assertThat(Files.readString(entradaRest.resolve("PN_25014360000173_NSU_10_202605.xml")))
                .isEqualTo("<xml/>");
        assertThat(resultado.caminhoTecnico()).contains("respostas-adn/2026-05/25014360000173/nsu-10.xml");
        assertThat(resultado.caminhoPublicado()).contains("entrada-rest/PN_25014360000173_NSU_10_202605.xml");
    }

    @Test
    void resposta404RegistraTentativaMasNaoMarcaComoImportado() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir));

        ResultadoRegistroConsulta resultado = registro.registrar(empresa(), "0",
                new ResultadoConsultaAdn(404, "application/json", "{}".getBytes()),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(resultado.falhaRegistrada()).isTrue();
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "0", "NSU-0").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.FALHA);
        assertThat(salvo.ultimoErro()).isEqualTo("HTTP 404 application/json");
        assertThat(salvo.concluida()).isFalse();
        assertThat(repositorio.jaConcluida("25014360000173", "0", "NSU-0")).isFalse();
    }

    @Test
    void resposta200JsonNaoPublicaENaoMarcaComoImportado() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest));

        ResultadoRegistroConsulta resultado = registro.registrar(empresa(), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8", "{\"erro\":\"sem xml\"}".getBytes()),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(resultado.publicado()).isFalse();
        assertThat(resultado.falhaRegistrada()).isTrue();
        assertThat(resultado.caminhoTecnico()).contains("respostas-adn/2026-05/25014360000173/nsu-1.json");
        assertThat(Files.readString(tempDir.resolve("respostas-adn/2026-05/25014360000173/nsu-1.json")))
                .isEqualTo("{\"erro\":\"sem xml\"}");
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "1", "NSU-1").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.FALHA);
        assertThat(salvo.ultimoErro()).isEqualTo("HTTP 200 application/json; charset=utf-8");
        assertThat(salvo.concluida()).isFalse();
        assertThat(Files.exists(entradaRest)).isFalse();
    }

    @Test
    void resposta200JsonComLotePublicaXmlsDoLoteEAtualizaLedgerPorNsuReal() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest));
        String json = """
                {
                  "StatusProcessamento": "PROCESSADO_COM_SUCESSO",
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s",
                      "DataHoraGeracao": "2026-05-09T12:00:00Z"
                    },
                    {
                      "NSU": 124,
                      "ChaveAcesso": "CHAVE124",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s",
                      "DataHoraGeracao": "2026-05-09T12:01:00Z"
                    }
                  ]
                }
                """.formatted(base64Gzip("<NFSe id=\"123\"/>"), base64Gzip("<NFSe id=\"124\"/>"));

        ResultadoRegistroConsulta resultado = registro.registrar(empresa(), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(resultado.publicado()).isTrue();
        assertThat(resultado.documentosPublicados()).isEqualTo(2);
        assertThat(resultado.caminhoTecnico()).contains("respostas-adn/2026-05/25014360000173/nsu-1.json");
        assertThat(Files.readString(entradaRest.resolve("PN_25014360000173_NSU_123_202605.xml")))
                .isEqualTo("<NFSe id=\"123\"/>");
        assertThat(Files.readString(entradaRest.resolve("PN_25014360000173_NSU_124_202605.xml")))
                .isEqualTo("<NFSe id=\"124\"/>");
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.concluida()).isFalse();
    }

    @Test
    void resposta200JsonComLoteBaixaDanfseEPublicaPdfNaEntradaRest() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        byte[] pdf = "%PDF-1.4\nconteudo".getBytes(StandardCharsets.UTF_8);
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest),
                documento -> Optional.of(new ResultadoConsultaAdn(200, "application/pdf", pdf)));
        String json = """
                {
                  "StatusProcessamento": "PROCESSADO_COM_SUCESSO",
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip("<NFSe id=\"123\"/>"));

        ResultadoRegistroConsulta resultado = registro.registrar(empresa(), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(resultado.publicado()).isTrue();
        assertThat(Files.readString(entradaRest.resolve("PN_25014360000173_NSU_123_202605.xml")))
                .isEqualTo("<NFSe id=\"123\"/>");
        assertThat(Files.readAllBytes(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf")))
                .isEqualTo(pdf);
        assertThat(Files.readAllBytes(tempDir.resolve("respostas-adn/2026-05/25014360000173/nsu-123-pdf.pdf")))
                .isEqualTo(pdf);
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.estadoPdf()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.ultimoErro()).isBlank();
    }

    @Test
    void resposta200JsonComLotePublicaXmlNoCaminhoDmsDaEmpresa() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(), null);
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip("<NFSe id=\"123\"/>"));

        Path dmsRoot = tempDir.resolve("dms");
        registro.registrar(empresaComDms(dmsRoot), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        Path dms = dmsRoot.resolve("PN_25014360000173_NSU_123_202605.xml");
        assertThat(Files.readString(dms)).isEqualTo("<NFSe id=\"123\"/>");
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.caminhoDmsFinal()).endsWith("PN_25014360000173_NSU_123_202605.xml");
    }

    @Test
    void dmsUsaDestinoResolvidoPelaDataDeEmissaoDoXml() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path dmsAbril = tempDir.resolve("dms-abril");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(), null,
                (empresa, documento, mesComando) -> Optional.of(new DestinoDmsResolvido(
                        dmsAbril, "25014360000173", YearMonth.of(2026, 4))));
        String xml = """
                <NFSe>
                  <DPS>
                    <infDPS>
                      <dhEmi>2026-04-15T10:30:00-03:00</dhEmi>
                      <toma><CNPJ>25014360000173</CNPJ></toma>
                    </infDPS>
                  </DPS>
                </NFSe>
                """;
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip(xml));

        registro.registrar(empresaComDms(tempDir.resolve("dms-maio")), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(Files.readString(dmsAbril.resolve("PN_25014360000173_NSU_123_202604.xml")))
                .isEqualTo(xml);
        assertThat(tempDir.resolve("dms-maio/PN_25014360000173_NSU_123_202605.xml")).doesNotExist();
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.caminhoDmsFinal()).endsWith("PN_25014360000173_NSU_123_202604.xml");
    }

    @Test
    void reconciliacaoPublicaDmsDaEmpresaConsultaMesmoQuandoTomadorNaoTemRota() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path dms = tempDir.resolve("dms-consulta");
        EmpresaImportacao empresa = empresaComDms(dms);
        RoteadorDmsPorEmissao roteador = new RoteadorDmsPorEmissao(
                new br.com.nfse.importadorpn.configuracao.CadastroImportacao(
                        java.util.List.of(empresa), Optional.of(entradaRest)), 2026);
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(), null, true, roteador);
        String xml = """
                <NFSe>
                  <infNFSe>
                    <dhEmi>2026-05-15T10:30:00-03:00</dhEmi>
                    <prest><CNPJ>25014360000173</CNPJ></prest>
                    <toma><CNPJ>99999999000191</CNPJ></toma>
                  </infNFSe>
                </NFSe>
                """;
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip(xml));
        EstadoDestinoNotas destino = new EstadoDestinoNotas(
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), true, true);

        registro.registrar(empresa, "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"), destino);

        assertThat(Files.readString(dms.resolve("PN_25014360000173_NSU_123_202605.xml")))
                .isEqualTo(xml);
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.ultimoErro()).isBlank();
    }

    @Test
    void dmsNaoUsaCaminhoDoMesAtualQuandoRoteadorNaoEncontraMesDeEmissao() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path dmsMaio = tempDir.resolve("dms-maio");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(), null,
                (empresa, documento, mesComando) -> Optional.empty());
        String xml = """
                <NFSe>
                  <DPS>
                    <infDPS>
                      <dhEmi>2025-05-15T10:30:00-03:00</dhEmi>
                      <toma><CNPJ>25014360000173</CNPJ></toma>
                    </infDPS>
                  </DPS>
                </NFSe>
                """;
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip(xml));

        registro.registrar(empresaComDms(dmsMaio), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(dmsMaio.resolve("PN_25014360000173_NSU_123_202605.xml")).doesNotExist();
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.ultimoErro()).contains("DMS sem rota");
    }

    @Test
    void reconciliacaoIgnoraXmlForaDoMesAntesDePublicarRestDmsOuPdf() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path dmsMaio = tempDir.resolve("dms-maio");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(),
                documento -> {
                    throw new AssertionError("Nao deve baixar DANFSe de documento fora do mes de trabalho");
                },
                true,
                (empresa, documento, mesComando) -> Optional.empty());
        String xml = """
                <NFSe>
                  <DPS>
                    <infDPS>
                      <dhEmi>2025-03-15T10:30:00-03:00</dhEmi>
                      <toma><CNPJ>25014360000173</CNPJ></toma>
                    </infDPS>
                  </DPS>
                </NFSe>
                """;
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip(xml));

        ResultadoRegistroConsulta resultado = registro.registrar(empresaComDms(dmsMaio), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"),
                new EstadoDestinoNotas(java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), true, true));

        assertThat(resultado.publicado()).isFalse();
        assertThat(resultado.falhaRegistrada()).isFalse();
        assertThat(resultado.documentosPublicados()).isZero();
        assertThat(entradaRest).doesNotExist();
        assertThat(dmsMaio).doesNotExist();
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.estadoPdf()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.ultimoErro()).contains("fora do mes de trabalho 2026-05");
    }

    @Test
    void respostaDanfseSemPdfValidoFicaPendenteParaGeradorLocalFuturo() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest),
                documento -> Optional.of(new ResultadoConsultaAdn(200, "text/html", "<html>erro</html>".getBytes(StandardCharsets.UTF_8))));
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip("""
                        <NFSe>
                          <infNFSe>
                            <dhEmi>2026-05-09T10:00:00-03:00</dhEmi>
                            <prest><CNPJ>25014360000173</CNPJ></prest>
                          </infNFSe>
                        </NFSe>
                        """));

        registro.registrar(empresa(), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(Files.exists(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf")))
                .isFalse();
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.estadoPdf()).isEqualTo(EstadoDocumento.PENDENTE);
        assertThat(salvo.ultimoErro()).contains("DANFSe invalido");
    }

    @Test
    void naoBaixaDanfseDeNovoQuandoPdfJaEstaConcluidoNoLedger() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        Path entradaRest = tempDir.resolve("entrada-rest");
        Files.createDirectories(entradaRest);
        Files.write(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf"),
                "%PDF-1.4\nexistente".getBytes(StandardCharsets.UTF_8));
        repositorio.salvar(RegistroImportacao.novo("25014360000173", "123", "CHAVE123",
                        "DGA ENERGIA", Instant.parse("2026-05-09T11:00:00Z"))
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comEstadoDms(EstadoDocumento.PENDENTE)
                .comCaminhoRestFinal("/entrada-rest/PN_25014360000173_NSU_123_202605.xml"));
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest),
                documento -> {
                    throw new AssertionError("Nao deveria consultar DANFSe para PDF ja concluido");
                });
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip("<NFSe id=\"123\"/>"));

        registro.registrar(empresa(), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoXml()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.estadoPdf()).isEqualTo(EstadoDocumento.CONCLUIDO);
    }

    @Test
    void baixaDanfseDeNovoQuandoLedgerDizConcluidoMasPdfOperacionalSumiu() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        repositorio.salvar(RegistroImportacao.novo("25014360000173", "123", "CHAVE123",
                        "DGA ENERGIA", Instant.parse("2026-05-09T11:00:00Z"))
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comEstadoDms(EstadoDocumento.PENDENTE)
                .comCaminhoRestFinal("/entrada-rest/PN_25014360000173_NSU_123_202605.xml"));
        Path entradaRest = tempDir.resolve("entrada-rest");
        byte[] pdf = "%PDF-1.4\nrecomposto".getBytes(StandardCharsets.UTF_8);
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), null,
                documento -> Optional.of(new ResultadoConsultaAdn(200, "application/pdf", pdf)), true);
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip("<NFSe id=\"123\"/>"));

        registro.registrar(empresa(), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"));

        assertThat(Files.readAllBytes(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf")))
                .isEqualTo(pdf);
        assertThat(Files.readAllBytes(tempDir.resolve("respostas-adn/2026-05/25014360000173/nsu-123-pdf.pdf")))
                .isEqualTo(pdf);
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoPdf()).isEqualTo(EstadoDocumento.CONCLUIDO);
        assertThat(salvo.ultimoErro()).isBlank();
    }

    @Test
    void baixaDanfseQuandoDestinoFinalNaoTemPdfMesmoComLedgerConcluidoEEntradaRestVazia() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        repositorio.salvar(RegistroImportacao.novo("25014360000173", "123", "CHAVE123",
                        "DGA ENERGIA", Instant.parse("2026-05-09T11:00:00Z"))
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comEstadoDms(EstadoDocumento.CONCLUIDO));
        Path entradaRest = tempDir.resolve("entrada-rest");
        byte[] pdf = "%PDF-1.4\nreimportado".getBytes(StandardCharsets.UTF_8);
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(),
                documento -> Optional.of(new ResultadoConsultaAdn(200, "application/pdf", pdf)), true);
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip("""
                        <NFSe>
                          <infNFSe>
                            <dhEmi>2026-05-09T10:00:00-03:00</dhEmi>
                            <prest><CNPJ>25014360000173</CNPJ></prest>
                          </infNFSe>
                        </NFSe>
                        """));
        EstadoDestinoNotas destino = new EstadoDestinoNotas(
                java.util.Set.of("CHAVE123"), java.util.Set.of(), java.util.Set.of("CHAVE123"), true, true);

        registro.registrar(empresaComDms(tempDir.resolve("dms")), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"), destino);

        assertThat(Files.readAllBytes(entradaRest.resolve("PN_25014360000173_NSU_123_202605.pdf")))
                .isEqualTo(pdf);
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoPdf()).isEqualTo(EstadoDocumento.CONCLUIDO);
    }

    @Test
    void republicaDmsQuandoDestinoFinalDmsNaoTemXmlMesmoComLedgerConcluido() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        repositorio.salvar(RegistroImportacao.novo("25014360000173", "123", "CHAVE123",
                        "DGA ENERGIA", Instant.parse("2026-05-09T11:00:00Z"))
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comEstadoDms(EstadoDocumento.CONCLUIDO));
        Path entradaRest = tempDir.resolve("entrada-rest");
        Path dms = tempDir.resolve("dms");
        byte[] pdf = "%PDF-1.4\nexistente".getBytes(StandardCharsets.UTF_8);
        RegistroConsultaAdn registro = new RegistroConsultaAdn(repositorio, new PublicadorRespostaAdn(tempDir),
                new PublicadorRestEntrada(entradaRest), new PublicadorDmsDireto(),
                documento -> Optional.of(new ResultadoConsultaAdn(200, "application/pdf", pdf)), true);
        String xml = """
                <NFSe>
                  <infNFSe>
                    <dhEmi>2026-05-09T10:00:00-03:00</dhEmi>
                    <prest><CNPJ>25014360000173</CNPJ></prest>
                  </infNFSe>
                </NFSe>
                """;
        String json = """
                {
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "CHAVE123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s"
                    }
                  ]
                }
                """.formatted(base64Gzip(xml));
        EstadoDestinoNotas destino = new EstadoDestinoNotas(
                java.util.Set.of("CHAVE123"), java.util.Set.of("CHAVE123"), java.util.Set.of(), true, true);

        registro.registrar(empresaComDms(dms), "1",
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)),
                YearMonth.of(2026, 5), Instant.parse("2026-05-09T12:00:00Z"), destino);

        assertThat(Files.readString(dms.resolve("PN_25014360000173_NSU_123_202605.xml")))
                .isEqualTo(xml);
        RegistroImportacao salvo = repositorio.buscar("25014360000173", "123", "CHAVE123").orElseThrow();
        assertThat(salvo.estadoDms()).isEqualTo(EstadoDocumento.CONCLUIDO);
    }

    private static EmpresaImportacao empresa() {
        return empresaComDms(null);
    }

    private static EmpresaImportacao empresaComDms(Path dms) {
        return new EmpresaImportacao("DGA ENERGIA", "25014360000173",
                Optional.empty(), Optional.ofNullable(dms),
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
