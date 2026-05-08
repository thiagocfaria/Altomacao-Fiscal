package br.com.nfse.renomeador.ledger;

import br.com.nfse.renomeador.layout.LayoutType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateInvoiceIndexTest {
    @TempDir
    Path tempDir;

    @Test
    void ignoresMalformedLinesAndKeepsThemForAudit() throws Exception {
        Path indexFile = tempDir.resolve("duplicadas.idx");
        Files.writeString(indexFile, String.join(System.lineSeparator(),
                "linha-quebrada",
                "empresa_a\tchave-fiscal\tPORTAL_NACIONAL\t/processados/NF 1.pdf\t2026-04-30T12:01:00Z"
        ));

        DuplicateInvoiceIndex index = new DuplicateInvoiceIndex(indexFile);

        assertThat(index.find("empresa_a", "chave-fiscal", LayoutType.PORTAL_NACIONAL))
                .isPresent();
        assertThat(indexFile.resolveSibling("duplicadas.idx.corrompidas"))
                .content()
                .contains("linha-quebrada");
        assertThat(indexFile)
                .content()
                .doesNotContain("linha-quebrada");
    }

    @Test
    void escapesTabsAndLineBreaksInIndexFields() throws Exception {
        Path indexFile = tempDir.resolve("duplicadas.idx");
        Path destination = Path.of("/processados/NF\t1.pdf");

        DuplicateInvoiceIndex index = new DuplicateInvoiceIndex(indexFile);
        index.record("empresa_a", "chave\tfiscal\ncomplexa", LayoutType.PORTAL_NACIONAL, destination);

        assertThat(Files.readString(indexFile))
                .doesNotContain("chave\tfiscal")
                .doesNotContain("complexa" + System.lineSeparator());
        assertThat(new DuplicateInvoiceIndex(indexFile).find("empresa_a", "chave\tfiscal\ncomplexa",
                LayoutType.PORTAL_NACIONAL))
                .isPresent()
                .get()
                .extracting(DuplicateInvoiceIndex.Entry::destination)
                .isEqualTo(destination);
    }
}
