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
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.nio.file.WatchEvent;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static br.com.nfse.renomeador.TestSamples.samplePdf;
import static org.assertj.core.api.Assertions.assertThat;

class WatchFolderProcessorTest {
    @TempDir
    Path tempDir;

    @Test
    void processesExistingPdfsWhenWatcherStarts() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));

        var summary = new WatchFolderProcessor().processExisting(List.of(companyPath), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("backend").resolve("empresas")
                .resolve("empresa_piloto").resolve("execucao-" + YearMonth.now() + ".tsv")).exists();
    }

    @Test
    void writesStartupSummaryPerCompanyPath() throws Exception {
        ResolvedCompanyPath first = companyPath("empresa_piloto", tempDir.resolve("empresa_a"));
        ResolvedCompanyPath second = companyPath("empresa_vazia", tempDir.resolve("empresa_b"));
        Files.createDirectories(first.root().resolve("entrada"));
        Files.createDirectories(second.root().resolve("entrada"));
        Files.copy(samplePdf("NF 9 OK.pdf"), first.root().resolve("entrada").resolve("NF 9 OK.pdf"));

        new WatchFolderProcessor().processExisting(List.of(first, second), true);

        assertThat(Files.readString(operationalLog("empresa_piloto")))
                .contains("SUMMARY\ttotal=1");
        assertThat(Files.readString(operationalLog("empresa_vazia")))
                .contains("SUMMARY\ttotal=0");
    }


    @Test
    void rescansInputFolderWhenWatchServiceOverflows() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));

        var summary = new WatchFolderProcessor().processEvents(input, companyPath,
                List.of(overflowEvent()), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
    }

    @Test
    void processesCreatedAndModifiedSupportedDocumentEventsOnly() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Files.writeString(input.resolve("nota.xml"), portalNacionalXml());
        Files.writeString(input.resolve("ignorado.txt"), "nao processar");

        var summary = new WatchFolderProcessor().processEvents(input, companyPath, List.of(
                event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("ignorado.txt")),
                event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("nota.xml")),
                event(StandardWatchEventKinds.ENTRY_MODIFY, Path.of("NF 9 OK.pdf"))
        ), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(2);
        assertThat(tempDir.resolve("XML").resolve("processados"))
                .isDirectoryContaining(path -> path.getFileName().toString()
                        .equals("NFSE_123_FORNECEDOR_TESTE_LTDA_02.04.2026_100,00.xml"));
    }

    @Test
    void retriesPdfEventUntilFileBecomesStable() throws Exception {
        ResolvedCompanyPath companyPath = companyPath();
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Path pdf = input.resolve("NF 9 OK.pdf");
        Files.copy(samplePdf("NF 9 OK.pdf"), pdf);

        Thread toucher = touchFor(pdf, Duration.ofMillis(650));
        var summary = new WatchFolderProcessor().processEvents(input, companyPath,
                List.of(event(StandardWatchEventKinds.ENTRY_CREATE, Path.of("NF 9 OK.pdf"))), true);
        toucher.join();

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(summary.skipped()).isZero();
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

    private Path backendCompany(String id) {
        return tempDir.resolve("backend").resolve("empresas").resolve(id);
    }

    private Path operationalLog(String id) {
        return backendCompany(id).resolve("execucao-" + YearMonth.now() + ".tsv");
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

    private static Thread touchFor(Path file, Duration duration) {
        Thread thread = new Thread(() -> {
            Instant end = Instant.now().plus(duration);
            while (Instant.now().isBefore(end)) {
                try {
                    Files.setLastModifiedTime(file, FileTime.from(Instant.now()));
                    Thread.sleep(50);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        });
        thread.start();
        return thread;
    }

    private static String portalNacionalXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <NFSe xmlns="http://www.sped.fazenda.gov.br/nfse">
                  <nNFSe>123</nNFSe>
                  <dhEmi>2026-04-02T10:15:00-03:00</dhEmi>
                  <prest>
                    <CNPJ>11222333000144</CNPJ>
                    <xNome>FORNECEDOR TESTE LTDA</xNome>
                  </prest>
                  <toma>
                    <CNPJ>25014360000173</CNPJ>
                    <xNome>DGA ENERGIA E AUTOMACAO LTDA</xNome>
                  </toma>
                  <vServ>100.00</vServ>
                  <vLiq>100.00</vLiq>
                </NFSe>
                """;
    }
}
