package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyFolders;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
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

        assertThat(result.destination()).isEqualTo(tempDir.resolve("PDF").resolve("processados").resolve("NFSE_1.pdf"));
        assertThat(result.destination()).exists();
        assertThat(source).doesNotExist();
    }

    @Test
    void sendsCancelledToCancelledReviewFolder() throws Exception {
        Path source = source("cancelada.pdf");

        DestinationResult result = new DestinationService().send(source, companyPath(), ProcessingStatus.CANCELLED,
                "NFSE_1_##CANCELADA##.pdf", true);

        assertThat(result.destination()).isEqualTo(tempDir.resolve("PDF").resolve("revisar").resolve("canceladas").resolve("NFSE_1_##CANCELADA##.pdf"));
        assertThat(result.destination()).exists();
        assertThat(source).exists();
    }

    @Test
    void sendsXmlOkToXmlProcessedFolder() throws Exception {
        Path source = source("nota.xml");

        DestinationResult result = new DestinationService().send(source, companyPath(), ProcessingStatus.OK,
                "NFSE_1.xml", false, false, DocumentType.XML, CompanyRouteDirectory.single(companyPath()));

        assertThat(result.destination()).isEqualTo(tempDir.resolve("XML").resolve("processados").resolve("NFSE_1.xml"));
        assertThat(result.destination()).exists();
        assertThat(source).doesNotExist();
    }

    @Test
    void sendsUnsupportedStatusToLocalUnsupportedFolderAndAvoidsCollision() throws Exception {
        Path source = source("revisar.pdf");
        Path unsupported = tempDir.resolve("PDF").resolve(DestinationService.UNSUPPORTED_FOLDER);
        Files.createDirectories(unsupported);
        Files.writeString(unsupported.resolve("NFSE_1.pdf"), "existente");

        DestinationResult result = new DestinationService().send(source, companyPath(), ProcessingStatus.UNSUPPORTED,
                "NFSE_1.pdf", true);

        assertThat(result.destination()).isEqualTo(unsupported.resolve("NFSE_1_01.pdf"));
        assertThat(Files.readString(result.destination())).isEqualTo("conteudo");
        assertThat(tempDir.resolve("backend").resolve("empresas").resolve("empresa_a").resolve("revisar"))
                .doesNotExist();
    }

    @Test
    void sendsXmlTechnicalErrorToLocalXmlUnsupportedFolder() throws Exception {
        Path source = source("erro.xml");

        DestinationResult result = new DestinationService().sendTechnicalError(source, companyPath(), false,
                DocumentType.XML, CompanyRouteDirectory.single(companyPath()));

        assertThat(result.destination()).isEqualTo(tempDir.resolve("XML")
                .resolve(DestinationService.UNSUPPORTED_FOLDER)
                .resolve("ERRO_PROCESSAMENTO_erro.xml"));
        assertThat(result.destination()).exists();
        assertThat(source).doesNotExist();
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
