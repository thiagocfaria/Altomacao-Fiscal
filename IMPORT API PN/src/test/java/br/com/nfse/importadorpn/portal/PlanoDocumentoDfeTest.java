package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanoDocumentoDfeTest {
    private static final String CHAVE = "12345678901234567890123456789012345678901234567890";
    private static final YearMonth MAIO = YearMonth.of(2026, 5);

    private final PlanejadorDocumentoDfe planejador = new PlanejadorDocumentoDfe();

    @Test
    void destinoCompletoNaoImporta() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(CHAVE), Set.of(CHAVE), Set.of(CHAVE)),
                documento(xmlComEmissao("2026-05-09T10:00:00-03:00")),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.COMPLETO);
        assertThat(plano.deveImportar()).isFalse();
        assertThat(plano.xmlRestFaltante()).isFalse();
        assertThat(plano.pdfRestFaltante()).isFalse();
        assertThat(plano.dmsFaltante()).isFalse();
    }

    @Test
    void faltaXmlRest() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(), Set.of(CHAVE), Set.of(CHAVE)),
                documento(xmlComEmissao("2026-05-09T10:00:00-03:00")),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.IMPORTAR);
        assertThat(plano.xmlRestFaltante()).isTrue();
        assertThat(plano.pdfRestFaltante()).isFalse();
        assertThat(plano.dmsFaltante()).isFalse();
    }

    @Test
    void faltaPdfRest() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(CHAVE), Set.of(), Set.of(CHAVE)),
                documento(xmlComEmissao("2026-05-09T10:00:00-03:00")),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.IMPORTAR);
        assertThat(plano.xmlRestFaltante()).isFalse();
        assertThat(plano.pdfRestFaltante()).isTrue();
        assertThat(plano.dmsFaltante()).isFalse();
    }

    @Test
    void faltaDms() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(CHAVE), Set.of(CHAVE), Set.of()),
                documento(xmlComEmissao("2026-05-09T10:00:00-03:00")),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.IMPORTAR);
        assertThat(plano.xmlRestFaltante()).isFalse();
        assertThat(plano.pdfRestFaltante()).isFalse();
        assertThat(plano.dmsFaltante()).isTrue();
        assertThat(plano.rotaDmsAusente()).isFalse();
    }

    @Test
    void canceladaSemPdfContaComoCompletaQuandoScannerMarcouPdfPresente() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(CHAVE), Set.of(CHAVE), Set.of(CHAVE)),
                documento(xmlComEmissao("2026-05-09T10:00:00-03:00")),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.COMPLETO);
        assertThat(plano.pdfRestFaltante()).isFalse();
    }

    @Test
    void xmlForaDoMesNaoImporta() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(), Set.of(), Set.of()),
                documento(xmlComEmissao("2026-04-30T23:00:00-03:00")),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.FORA_DO_MES);
        assertThat(plano.deveImportar()).isFalse();
        assertThat(plano.mensagem()).contains("fora do mes de trabalho 2026-05");
    }

    @Test
    void xmlSemDataNaoImporta() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(), Set.of(), Set.of()),
                documento("<NFSe><infNFSe/></NFSe>"),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.SEM_DATA_EMISSAO);
        assertThat(plano.deveImportar()).isFalse();
        assertThat(plano.mensagem()).contains("sem data de emissao");
    }

    @Test
    void rotaDmsAusenteApareceNoPlano() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(CHAVE), Set.of(CHAVE), Set.of()),
                documento(xmlComEmissao("2026-05-09T10:00:00-03:00")),
                (empresa, documento, mesComando) -> Optional.empty());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.IMPORTAR);
        assertThat(plano.dmsFaltante()).isTrue();
        assertThat(plano.rotaDmsAusente()).isTrue();
        assertThat(plano.mensagem()).contains("DMS sem rota");
    }

    @Test
    void xmlSemCnpjDaLinhaAtivaNaoImporta() {
        PlanoDocumentoDfe plano = planejar(
                estado(Set.of(), Set.of(), Set.of()),
                documento("""
                        <NFSe>
                          <infNFSe>
                            <dhEmi>2026-05-09T10:00:00-03:00</dhEmi>
                            <prest><CNPJ>99999999000191</CNPJ></prest>
                            <toma><CNPJ>88888888000192</CNPJ></toma>
                          </infNFSe>
                        </NFSe>
                        """),
                rotaPadrao());

        assertThat(plano.status()).isEqualTo(StatusPlanoDocumento.NAO_PERTENCE_EMPRESA);
        assertThat(plano.deveImportar()).isFalse();
        assertThat(plano.mensagem()).contains("CNPJ da linha ativa 25014360000173 nao aparece no XML");
    }

    private PlanoDocumentoDfe planejar(EstadoDestinoNotas estado, DocumentoDfeExtraido documento, RoteadorDms roteador) {
        return planejador.planejar(empresa(), documento, MAIO, estado, roteador);
    }

    private static EstadoDestinoNotas estado(Set<String> xmlRest, Set<String> pdfRest, Set<String> dms) {
        return new EstadoDestinoNotas(xmlRest, pdfRest, dms, true, true);
    }

    private static RoteadorDms rotaPadrao() {
        return (empresa, documento, mesComando) -> Optional.of(new DestinoDmsResolvido(
                Path.of("/tmp/dms"), empresa.cnpj(), mesComando));
    }

    private static DocumentoDfeExtraido documento(String xml) {
        return new DocumentoDfeExtraido("123", CHAVE, "NFSE", xml.getBytes(StandardCharsets.UTF_8));
    }

    private static String xmlComEmissao(String emissao) {
        return """
                <NFSe>
                  <infNFSe>
                    <dhEmi>%s</dhEmi>
                    <prest><CNPJ>25014360000173</CNPJ></prest>
                  </infNFSe>
                </NFSe>
                """.formatted(emissao);
    }

    private static EmpresaImportacao empresa() {
        return new EmpresaImportacao("DGA ENERGIA", "25014360000173",
                Optional.empty(), Optional.of(Path.of("/tmp/dms")),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), "CADASTRO MAIO", 54);
    }
}
