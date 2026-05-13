package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChavesPresentesNoDestinoTest {
    private static final String CHAVE =
            "12345678901234567890123456789012345678901234567890";

    @TempDir
    Path tempDir;

    @Test
    void retidoContaComoXmlEPdfPresentesNoDestinoRest() throws Exception {
        Path rest = tempDir.resolve("REST");
        Path xmlRetido = rest.resolve("XML").resolve("RETIDO");
        Path pdfRetido = rest.resolve("PDF").resolve("RETIDO");
        Files.createDirectories(xmlRetido);
        Files.createDirectories(pdfRetido);
        Files.writeString(xmlRetido.resolve("nota.xml"), "<NFSe><chNFSe>" + CHAVE + "</chNFSe></NFSe>");
        Files.writeString(pdfRetido.resolve("nota.pdf"), "%PDF-1.4");

        EstadoDestinoNotas estado = new ChavesPresentesNoDestino()
                .escanearEstado(empresa(rest, Optional.empty()), YearMonth.of(2026, 5));

        assertThat(estado.xmlRestPresente(CHAVE)).isTrue();
        assertThat(estado.pdfRestPresente(CHAVE)).isTrue();
        assertThat(estado.dmsPresente(CHAVE)).isTrue();
        assertThat(estado.completo(CHAVE)).isTrue();
    }

    @Test
    void xmlCanceladoContaComoPdfPresenteMesmoSemDanfse() throws Exception {
        Path rest = tempDir.resolve("REST");
        Path xmlCancelado = rest.resolve("XML").resolve("canceladas");
        Files.createDirectories(xmlCancelado);
        Files.writeString(xmlCancelado.resolve("cancelada.xml"), "<NFSe><chNFSe>" + CHAVE + "</chNFSe></NFSe>");

        EstadoDestinoNotas estado = new ChavesPresentesNoDestino()
                .escanearEstado(empresa(rest, Optional.empty()), YearMonth.of(2026, 5));

        assertThat(estado.xmlRestPresente(CHAVE)).isTrue();
        assertThat(estado.pdfRestPresente(CHAVE)).isTrue();
        assertThat(estado.completo(CHAVE)).isTrue();
    }

    @Test
    void dmsAusenteDeixaNotaIncompletaMesmoComRestCompleto() throws Exception {
        Path rest = tempDir.resolve("REST");
        Path dms = tempDir.resolve("DMS");
        Path xmlProc = rest.resolve("XML").resolve("processados");
        Path pdfProc = rest.resolve("PDF").resolve("processados");
        Files.createDirectories(xmlProc);
        Files.createDirectories(pdfProc);
        Files.createDirectories(dms);
        Files.writeString(xmlProc.resolve("nota.xml"), "<NFSe><chNFSe>" + CHAVE + "</chNFSe></NFSe>");
        Files.writeString(pdfProc.resolve("nota.pdf"), "%PDF-1.4");

        EstadoDestinoNotas estado = new ChavesPresentesNoDestino()
                .escanearEstado(empresa(rest, Optional.of(dms)), YearMonth.of(2026, 5));

        assertThat(estado.xmlRestPresente(CHAVE)).isTrue();
        assertThat(estado.pdfRestPresente(CHAVE)).isTrue();
        assertThat(estado.dmsPresente(CHAVE)).isFalse();
        assertThat(estado.completo(CHAVE)).isFalse();
    }

    @Test
    void pdfComSufixoDeDuplicidadeContaComoParDoXml() throws Exception {
        Path rest = tempDir.resolve("REST");
        Path xmlProc = rest.resolve("XML").resolve("processados");
        Path pdfProc = rest.resolve("PDF").resolve("processados");
        Files.createDirectories(xmlProc);
        Files.createDirectories(pdfProc);
        Files.writeString(xmlProc.resolve("nota.xml"), "<NFSe><chNFSe>" + CHAVE + "</chNFSe></NFSe>");
        Files.writeString(pdfProc.resolve("nota_01.pdf"), "%PDF-1.4");

        EstadoDestinoNotas estado = new ChavesPresentesNoDestino()
                .escanearEstado(empresa(rest, Optional.empty()), YearMonth.of(2026, 5));

        assertThat(estado.pdfRestPresente(CHAVE)).isTrue();
        assertThat(estado.completo(CHAVE)).isTrue();
    }

    private static EmpresaImportacao empresa(Path rest, Optional<Path> dms) {
        return new EmpresaImportacao("DGA ENERGIA", "25014360000173",
                Optional.of(rest), dms,
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), "CADASTRO MAIO", 54);
    }
}
