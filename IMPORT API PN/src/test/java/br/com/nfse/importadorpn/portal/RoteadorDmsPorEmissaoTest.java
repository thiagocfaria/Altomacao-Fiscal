package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoteadorDmsPorEmissaoTest {
    @Test
    void resolveDmsPelaLinhaAtivaMesmoQuandoTomadorNaoTemRota() {
        Path dmsMaio = Path.of("/tmp/dms-maio");
        RoteadorDmsPorEmissao roteador = new RoteadorDmsPorEmissao(new CadastroImportacao(List.of(
                empresa("DGA ENERGIA", "25014360000173", dmsMaio, "CADASTRO MAIO")
        ), Optional.empty()), 2026);
        DocumentoDfeExtraido documento = new DocumentoDfeExtraido("123", "CHAVE123", "NFSE", """
                <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse">
                  <DPS>
                    <infDPS>
                      <dhEmi>2026-05-15T10:30:00-03:00</dhEmi>
                      <prest><CNPJ>25014360000173</CNPJ></prest>
                      <toma><CNPJ>99999999000191</CNPJ></toma>
                    </infDPS>
                  </DPS>
                </NFSe>
                """.getBytes(StandardCharsets.UTF_8));

        Optional<DestinoDmsResolvido> destino = roteador.resolver(
                empresa("DGA ENERGIA", "25014360000173", dmsMaio, "CADASTRO MAIO"),
                documento, YearMonth.of(2026, 5));

        assertThat(destino).contains(new DestinoDmsResolvido(dmsMaio, "25014360000173", YearMonth.of(2026, 5)));
    }

    @Test
    void naoResolveDmsQuandoXmlEDeAnoSemAbaNaPlanilhaAtual() {
        Path dmsMaio = Path.of("/tmp/dms-maio");
        RoteadorDmsPorEmissao roteador = new RoteadorDmsPorEmissao(new CadastroImportacao(List.of(
                empresa("DGA ENERGIA", "25014360000173", dmsMaio, "CADASTRO MAIO")
        ), Optional.empty()), 2026);
        DocumentoDfeExtraido documento = new DocumentoDfeExtraido("123", "CHAVE123", "NFSE", """
                <NFSe>
                  <DPS>
                    <infDPS>
                      <dhEmi>2025-05-15T10:30:00-03:00</dhEmi>
                      <toma><CNPJ>25014360000173</CNPJ></toma>
                    </infDPS>
                  </DPS>
                </NFSe>
                """.getBytes(StandardCharsets.UTF_8));

        Optional<DestinoDmsResolvido> destino = roteador.resolver(
                empresa("DGA ENERGIA", "25014360000173", dmsMaio, "CADASTRO MAIO"),
                documento, YearMonth.of(2026, 5));

        assertThat(destino).isEmpty();
    }

    private static EmpresaImportacao empresa(String nome, String cnpj, Path dms, String origemPlanilha) {
        return new EmpresaImportacao(nome, cnpj,
                Optional.empty(), Optional.of(dms), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), origemPlanilha, 54);
    }
}
