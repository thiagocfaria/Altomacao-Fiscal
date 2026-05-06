package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyFolders;
import br.com.nfse.renomeador.config.MonthStrategy;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WatchFolderProcessorTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

    @TempDir
    Path tempDir;

    @Test
    void processesExistingPdfsWhenWatcherStarts() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));

        var summary = new WatchFolderProcessor().processExisting(List.of(companyPath), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("logs").resolve("execucao.log")).exists();
    }

    @Test
    void writesStartupSummaryPerCompanyPath() throws Exception {
        ResolvedCompanyPath first = companyPath("empresa_piloto", tempDir.resolve("empresa_a"));
        ResolvedCompanyPath second = companyPath("empresa_vazia", tempDir.resolve("empresa_b"));
        Files.createDirectories(first.root().resolve("entrada"));
        Files.createDirectories(second.root().resolve("entrada"));
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), first.root().resolve("entrada").resolve("NF 9 OK.pdf"));

        new WatchFolderProcessor().processExisting(List.of(first, second), true);

        assertThat(Files.readString(first.root().resolve("logs").resolve("execucao.log")))
                .contains("SUMMARY\ttotal=1");
        assertThat(Files.readString(second.root().resolve("logs").resolve("execucao.log")))
                .contains("SUMMARY\ttotal=0");
    }


    @Test
    void rescansInputFolderWhenWatchServiceOverflows() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));

        var summary = new WatchFolderProcessor().processEvents(input, companyPath,
                List.of(overflowEvent()), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
    }

    @Test
    void processesCreatedAndModifiedPdfEventsOnly() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Files.writeString(input.resolve("ignorado.txt"), "nao processar");

        var summary = new WatchFolderProcessor().processEvents(input, companyPath, List.of(
                event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("ignorado.txt")),
                event(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("NF 9 OK.pdf"))
        ), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
    }

    private ResolvedCompanyPath companyPath() {
        return companyPath("empresa_piloto", tempDir);
    }

    private ResolvedCompanyPath companyPath(String id, Path root) {
        return new ResolvedCompanyPath(
                new CompanyConfig(
                        id,
                        true,
                        "25.014.360/0001-73",
                        MonthStrategy.DIRECT,
                        List.of(),
                        root,
                        "{AAAA}/{MM}",
                        new CompanyFolders("entrada", "processados", "revisar", "originais", "logs",
                                "revisar/canceladas", "logs/processados.idx")
                ),
                root,
                Optional.empty()
        );
    }

    private static WatchEvent<Path> event(WatchEvent.Kind<Path> kind, Path context) {
        return new WatchEvent<>() {
            @Override
            public Kind<Path> kind() {
                return kind;
            }

            @Override
            public int count() {
                return 1;
            }

            @Override
            public Path context() {
                return context;
            }
        };
    }

    private static WatchEvent<?> overflowEvent() {
        return new WatchEvent<>() {
            @Override
            public Kind<Object> kind() {
                return StandardWatchEventKinds.OVERFLOW;
            }

            @Override
            public int count() {
                return 1;
            }

            @Override
            public Object context() {
                return null;
            }
        };
    }
}
