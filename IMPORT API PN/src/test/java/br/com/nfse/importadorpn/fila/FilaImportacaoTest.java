package br.com.nfse.importadorpn.fila;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.nfse.importadorpn.ledger.EstadoDocumento;
import br.com.nfse.importadorpn.ledger.RegistroImportacao;
import br.com.nfse.importadorpn.ledger.RepositorioImportacao;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilaImportacaoTest {
    @TempDir
    Path tempDir;

    @Test
    void naoEnfileiraNotaJaConcluidaENaoDuplicaNaMesmaRodada() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        repositorio.salvar(registro("10", "NFS1")
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comEstadoDms(EstadoDocumento.CONCLUIDO));

        List<NotaDescoberta> descobertas = List.of(
                new NotaDescoberta("Cliente", "11222333000181", "10", "NFS1"),
                new NotaDescoberta("Cliente", "11222333000181", "10", "NFS1"),
                new NotaDescoberta("Cliente", "11222333000181", "11", "NFS2"));

        List<ItemFila> fila = new FilaImportacao(repositorio).montar(descobertas);

        assertThat(fila).extracting(ItemFila::chave).containsExactly("NFS2");
        assertThat(fila.get(0).acao()).isEqualTo(AcaoFila.IMPORTAR_XML_E_PDF);
    }

    @Test
    void reenfileiraNotaPendenteSomenteParaComplementarPdfEDms() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        repositorio.salvar(registro("12", "NFS3")
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.PENDENTE)
                .comEstadoDms(EstadoDocumento.PENDENTE));

        List<ItemFila> fila = new FilaImportacao(repositorio).montar(List.of(
                new NotaDescoberta("Cliente", "11222333000181", "12", "NFS3")));

        assertThat(fila).hasSize(1);
        assertThat(fila.get(0).acao()).isEqualTo(AcaoFila.COMPLEMENTAR_PDF_E_DMS);
    }

    private static RegistroImportacao registro(String nsu, String chave) {
        return RegistroImportacao.novo("11222333000181", nsu, chave, "Cliente",
                Instant.parse("2026-05-08T12:00:00Z"));
    }
}
