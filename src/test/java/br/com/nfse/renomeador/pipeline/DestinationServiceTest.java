package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyFolders;
import br.com.nfse.renomeador.config.MonthStrategy;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DestinationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void sendsOkToProcessedAndMovesSourceInNormalMode() throws Exception {
        Path source = source("nota.pdf");

        DestinationResult result = new DestinationService().send(source, companyPath(), ProcessingStatus.OK,
                "NFSE_1.pdf", false);

        assertThat(result.destination()).isEqualTo(tempDir.resolve("processados").resolve("NFSE_1.pdf"));
        assertThat(result.destination()).exists();
        assertThat(source).doesNotExist();
    }

    @Test
    void sendsCancelledToCancelledReviewFolder() throws Exception {
        Path source = source("cancelada.pdf");

        DestinationResult result = new DestinationService().send(source, companyPath(), ProcessingStatus.CANCELLED,
                "NFSE_1_##CANCELADA##.pdf", true);

        assertThat(result.destination()).isEqualTo(tempDir.resolve("revisar").resolve("canceladas").resolve("NFSE_1_##CANCELADA##.pdf"));
        assertThat(result.destination()).exists();
        assertThat(source).exists();
    }

    @Test
    void sendsReviewStatusesToReviewAndAvoidsCollision() throws Exception {
        Path source = source("revisar.pdf");
        Path review = tempDir.resolve("revisar");
        Files.createDirectories(review);
        Files.writeString(review.resolve("NFSE_1.pdf"), "existente");

        DestinationResult result = new DestinationService().send(source, companyPath(), ProcessingStatus.UNSUPPORTED,
                "NFSE_1.pdf", true);

        assertThat(result.destination()).isEqualTo(review.resolve("NFSE_1_01.pdf"));
        assertThat(Files.readString(result.destination())).isEqualTo("conteudo");
    }

    private Path source(String name) throws Exception {
        Path source = tempDir.resolve("entrada").resolve(name);
        Files.createDirectories(source.getParent());
        Files.writeString(source, "conteudo");
        return source;
    }

    private ResolvedCompanyPath companyPath() {
        CompanyConfig company = new CompanyConfig(
                "empresa_a",
                true,
                "25.014.360/0001-73",
                MonthStrategy.DIRECT,
                List.of(),
                tempDir,
                "{AAAA}/{MM}",
                new CompanyFolders("entrada", "processados", "revisar", "originais", "logs", "revisar/canceladas", "logs/processados.idx")
        );
        return new ResolvedCompanyPath(company, tempDir, Optional.empty());
    }
}
