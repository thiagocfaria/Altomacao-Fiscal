package br.com.nfse.importadorpn.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorioImportacaoTest {
    @TempDir
    Path tempDir;

    @Test
    void gravaLedgerMensalEConsultaNotaConcluidaParaEvitarDuplicidade() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        RegistroImportacao registro = registro("11222333000181", "10", "NFS2026050001")
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comEstadoDms(EstadoDocumento.CONCLUIDO);

        repositorio.salvar(registro);
        repositorio.salvar(registro.comTentativas(2));

        assertThat(repositorio.jaConcluida("11222333000181", "10", "NFS2026050001")).isTrue();
        assertThat(repositorio.listar()).hasSize(1);
        assertThat(repositorio.listar().get(0).tentativas()).isEqualTo(2);
        assertThat(Files.readString(tempDir.resolve("ledger").resolve("2026-05.tsv")))
                .contains("cnpj\tnsu\tchave");
    }

    @Test
    void mantemPendenteQuandoPdfOuDmsAindaFalta() throws Exception {
        RepositorioImportacao repositorio = new RepositorioImportacao(tempDir.resolve("ledger"), YearMonth.of(2026, 5));
        repositorio.salvar(registro("11222333000181", "11", "NFS2026050002")
                .comEstadoXml(EstadoDocumento.CONCLUIDO)
                .comEstadoPdf(EstadoDocumento.PENDENTE)
                .comEstadoDms(EstadoDocumento.PENDENTE));

        assertThat(repositorio.jaConcluida("11222333000181", "11", "NFS2026050002")).isFalse();
        assertThat(repositorio.buscar("11222333000181", "11", "NFS2026050002"))
                .get()
                .extracting(RegistroImportacao::estadoPdf)
                .isEqualTo(EstadoDocumento.PENDENTE);
    }

    private static RegistroImportacao registro(String cnpj, String nsu, String chave) {
        return RegistroImportacao.novo(cnpj, nsu, chave, "Cliente", Instant.parse("2026-05-08T12:00:00Z"));
    }
}
