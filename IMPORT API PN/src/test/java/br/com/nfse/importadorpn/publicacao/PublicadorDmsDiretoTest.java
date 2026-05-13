package br.com.nfse.importadorpn.publicacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicadorDmsDiretoTest {
    @TempDir
    Path tempDir;

    @Test
    void publicaXmlNoCaminhoDmsDaEmpresaComNomeEstavel() throws Exception {
        PublicadorDmsDireto publicador = new PublicadorDmsDireto();
        ResultadoConsultaAdn xml = new ResultadoConsultaAdn(200, "application/xml", "<NFSe/>".getBytes());

        DmsDiretoPublicado publicado = publicador.publicar(tempDir, "26474286000211", "123",
                YearMonth.of(2026, 5), xml);

        Path esperado = tempDir.resolve("PN_26474286000211_NSU_123_202605.xml");
        assertThat(publicado.caminho()).isEqualTo(esperado);
        assertThat(publicado.bytes()).isEqualTo(7);
        assertThat(publicado.jaExistia()).isFalse();
        assertThat(Files.readString(esperado)).isEqualTo("<NFSe/>");
    }

    @Test
    void naoSobrescreveXmlDmsComConteudoDiferente() throws Exception {
        PublicadorDmsDireto publicador = new PublicadorDmsDireto();
        ResultadoConsultaAdn original = new ResultadoConsultaAdn(200, "application/xml", "<NFSe/>".getBytes());
        ResultadoConsultaAdn diferente = new ResultadoConsultaAdn(200, "application/xml", "<Outra/>".getBytes());

        publicador.publicar(tempDir, "26474286000211", "123", YearMonth.of(2026, 5), original);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        publicador.publicar(tempDir, "26474286000211", "123", YearMonth.of(2026, 5), diferente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Arquivo DMS ja existe com conteudo diferente");
    }
}
