package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlanejadorConsultaAdnTest {
    @Test
    void montaSomenteGetsParaEmpresasAtivasSemExecutarRede() {
        EmpresaImportacao empresa = new EmpresaImportacao("DGA ENERGIA", "25014360000173",
                Optional.of(Path.of("/rest")), Optional.of(Path.of("/dms")),
                Optional.of(Path.of("/certs")), Optional.of("dga.pfx"), Optional.of("25014360000173"),
                Optional.of("123456"), Optional.of(LocalDate.of(2026, 9, 17)), "CADASTRO MAIO", 54);

        List<ConsultaAdnPlanejada> consultas = new PlanejadorConsultaAdn(
                new ClienteAdn(AmbienteAdn.PRODUCAO_RESTRITA, new PoliticaSomenteLeitura()))
                .planejarDfePorNsu(List.of(empresa), "0");

        assertThat(consultas).hasSize(1);
        ConsultaAdnPlanejada consulta = consultas.get(0);
        assertThat(consulta.empresa()).isEqualTo("DGA ENERGIA");
        assertThat(consulta.cnpj()).isEqualTo("25014360000173");
        assertThat(consulta.metodo()).isEqualTo("GET");
        assertThat(consulta.uri().toString())
                .isEqualTo("https://adn.producaorestrita.nfse.gov.br/contribuintes/DFe/0?cnpj=25014360000173");
    }
}
