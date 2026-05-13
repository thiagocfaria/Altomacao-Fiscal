package br.com.nfse.importadorpn.publicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicadorRestEntradaTest {
    @TempDir
    Path tempDir;

    @Test
    void publicaXml200NaEntradaRestComNomeEstavel() throws Exception {
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(200, "application/xml", "<xml/>".getBytes());

        RestEntradaPublicada publicada = new PublicadorRestEntrada(tempDir)
                .publicar("25014360000173", "123", YearMonth.of(2026, 5), resultado);

        assertThat(publicada.caminho()).hasFileName("PN_25014360000173_NSU_123_202605.xml");
        assertThat(publicada.jaExistia()).isFalse();
        assertThat(Files.readString(publicada.caminho())).isEqualTo("<xml/>");
    }

    @Test
    void repetirMesmoConteudoNaoCriaDuplicidade() throws Exception {
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(200, "application/pdf", "PDF".getBytes());
        PublicadorRestEntrada publicador = new PublicadorRestEntrada(tempDir);

        RestEntradaPublicada primeira = publicador.publicar("25014360000173", "123",
                YearMonth.of(2026, 5), resultado);
        RestEntradaPublicada segunda = publicador.publicar("25014360000173", "123",
                YearMonth.of(2026, 5), resultado);

        assertThat(segunda.caminho()).isEqualTo(primeira.caminho());
        assertThat(segunda.jaExistia()).isTrue();
        assertThat(Files.list(tempDir)).hasSize(1);
    }

    @Test
    void naoPublicaJsonDeErroNaEntradaRest() {
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(404, "application/json", "{}".getBytes());

        assertThatThrownBy(() -> new PublicadorRestEntrada(tempDir)
                .publicar("25014360000173", "0", YearMonth.of(2026, 5), resultado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Somente resposta HTTP 200");
    }
}
