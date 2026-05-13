package br.com.nfse.importadorpn.publicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicadorRespostaAdnTest {
    @TempDir
    Path tempDir;

    @Test
    void gravaResposta200EmPastaTecnicaPorMesECnpj() throws Exception {
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(200, "application/xml", "<xml/>".getBytes());

        RespostaAdnPublicada publicada = new PublicadorRespostaAdn(tempDir)
                .publicar("25014360000173", "10/20", YearMonth.of(2026, 5), resultado);

        assertThat(publicada.caminho()).hasFileName("nsu-10_20.xml");
        assertThat(publicada.bytes()).isEqualTo(6);
        assertThat(Files.readString(publicada.caminho())).isEqualTo("<xml/>");
        assertThat(publicada.caminho().toString()).contains("respostas-adn/2026-05/25014360000173");
    }

    @Test
    void naoPublicaRespostaNaoEncontrada() {
        ResultadoConsultaAdn resultado = new ResultadoConsultaAdn(404, "application/json", "{}".getBytes());

        assertThatThrownBy(() -> new PublicadorRespostaAdn(tempDir)
                .publicar("25014360000173", "0", YearMonth.of(2026, 5), resultado))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Somente resposta HTTP 200");
    }
}
