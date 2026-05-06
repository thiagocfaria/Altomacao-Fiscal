package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BatchModeRunnerTest {
    private static final Path SAMPLES = Path.of("NF MODELO ABRASP E PORTAL NACIONAL");

    @TempDir
    Path tempDir;

    @Test
    void batchProcessesMixedLotInHomologationModeAndPreservesInput() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Files.copy(SAMPLES.resolve("NF 55034 OK.pdf"), input.resolve("NF 55034 OK.pdf"));
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(summary.count(ProcessingStatus.UNSUPPORTED)).isEqualTo(1);
        assertThat(input.resolve("NF 9 OK.pdf")).exists();
        assertThat(input.resolve("NF 55034 OK.pdf")).exists();
        assertThat(Files.list(tempDir.resolve("processados")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_9_") && name.endsWith(".pdf"));
        assertThat(Files.list(tempDir.resolve("revisar")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_DESCONHECIDA_MODELO_NAO_SUPORTADO_") && name.endsWith(".pdf"));
        assertThat(tempDir.resolve("logs").resolve("processados.idx")).exists();
        assertThat(tempDir.resolve("logs").resolve("execucao.log")).exists();
        assertThat(Files.readString(tempDir.resolve("logs").resolve("execucao.log")))
                .contains("duracaoMs=");
    }

    @Test
    void batchUsesLedgerToSkipSecondRun() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig("25.014.360/0001-73");

        new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);
        var secondRun = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(secondRun.skipped()).isEqualTo(1);
        assertThat(secondRun.total()).isZero();
    }

    @Test
    void batchRoutesWrongCompanyToReview() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig("12.345.678/0001-95");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.WRONG_COMPANY)).isEqualTo(1);
        assertThat(Files.list(tempDir.resolve("revisar")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_9_CNPJ_INCORRETO_") && name.endsWith(".pdf"));
    }

    @Test
    void batchMovesInvoiceFromWrongFolderToCorrectKnownRestFolder() throws Exception {
        Path wrongInput = Files.createDirectories(tempDir.resolve("empresa_errada").resolve("entrada"));
        Files.createDirectories(tempDir.resolve("empresa_correta").resolve("entrada"));
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), wrongInput.resolve("NF 9 OK.pdf"));
        Path config = writeTwoCompanyConfig(true);

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("empresa_correta").resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
        assertThat(tempDir.resolve("empresa_errada").resolve("revisar")).doesNotExist();
        assertThat(wrongInput.resolve("NF 9 OK.pdf")).doesNotExist();
        assertThat(Files.readString(tempDir.resolve("empresa_errada").resolve("logs").resolve("execucao.log")))
                .contains("empresa_correta")
                .contains("NF 9 OK.pdf");
        assertThat(Files.readString(tempDir.resolve("empresa_correta").resolve("logs").resolve("execucao.log")))
                .contains("empresa_correta")
                .contains("NF 9 OK.pdf");
        assertThat(Files.readString(tempDir.resolve("empresa_correta").resolve("logs").resolve("processados.idx")))
                .contains("empresa_correta")
                .contains("NF 9 OK.pdf");
    }

    @Test
    void batchKeepsInvoiceInReviewWhenCorrectCustomerHasNoRestFolder() throws Exception {
        Path wrongInput = Files.createDirectories(tempDir.resolve("empresa_errada").resolve("entrada"));
        Files.copy(SAMPLES.resolve("NF 9 OK.pdf"), wrongInput.resolve("NF 9 OK.pdf"));
        Path config = writeTwoCompanyConfig(false);

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.errors()).isZero();
        assertThat(tempDir.resolve("empresa_errada").resolve("revisar")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NF_PASTA_INCORRETA_CNPJ_NF_25014360000173_CNPJ_PASTA_12345678000195"));
        assertThat(wrongInput.resolve("NF 9 OK.pdf")).doesNotExist();
    }

    @Test
    void batchKeepsPortalNacionalAndDiscardsMatchingAbrasfDuplicate() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        writeTextPdf(input.resolve("01-portal.pdf"), portalDuplicateText());
        writeTextPdf(input.resolve("02-abrasf.pdf"), abrasfDuplicateText());
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(summary.count(ProcessingStatus.DUPLICATE)).isEqualTo(1);
        assertThat(input).isEmptyDirectory();
        assertThat(pdfNames(tempDir.resolve("processados")))
                .containsExactly("NFSE_123_FORNECEDOR_TESTE_LTDA_02.04.2026_100,00.pdf");
        assertThat(tempDir.resolve("revisar")).doesNotExist();
        assertThat(pdfNames(tempDir.resolve("originais"))).containsExactlyInAnyOrder("01-portal.pdf", "02-abrasf.pdf");
        assertThat(Files.readString(tempDir.resolve("logs").resolve("execucao.log")))
                .contains("DUPLICATE")
                .contains("ABRASF duplicada descartada; Portal Nacional equivalente ja existe");
    }

    @Test
    void batchRemovesPreviouslyProcessedAbrasfWhenMatchingPortalArrivesLater() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        writeTextPdf(input.resolve("01-abrasf.pdf"), abrasfDuplicateText());
        writeTextPdf(input.resolve("02-portal.pdf"), portalDuplicateText());
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(2);
        assertThat(input).isEmptyDirectory();
        assertThat(pdfNames(tempDir.resolve("processados")))
                .containsExactly("NFSE_123_FORNECEDOR_TESTE_LTDA_02.04.2026_100,00.pdf");
        assertThat(Files.readString(tempDir.resolve("logs").resolve("execucao.log")))
                .contains("ABRASF duplicada anterior removida por Portal Nacional equivalente");
    }

    @Test
    void batchDoesNotDiscardAbrasfWhenFiscalDuplicateValueDiffers() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        writeTextPdf(input.resolve("01-portal.pdf"), portalDuplicateText());
        writeTextPdf(input.resolve("02-abrasf.pdf"), abrasfDuplicateText().replace("R$ 100,00", "R$ 101,00"));
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(2);
        assertThat(summary.count(ProcessingStatus.DUPLICATE)).isZero();
        assertThat(pdfNames(tempDir.resolve("processados")))
                .containsExactly(
                        "NFSE_123_FORNECEDOR_TESTE_LTDA_02.04.2026_100,00.pdf",
                        "NFSE_123_FORNECEDOR_TESTE_LTDA_02.04.2026_101,00.pdf"
                );
    }

    private Path writeConfig(String taxId) throws Exception {
        Path config = tempDir.resolve("empresas.yaml");
        Files.writeString(config, """
                empresas:
                  - id: empresa_piloto
                    habilitada: true
                    cnpjTomador: "%s"
                    estrategiaMes: "direto"
                    pastaBase: "%s"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                """.formatted(taxId, tempDir.toString().replace("\\", "/")));
        return config;
    }

    private static List<String> pdfNames(Path directory) throws Exception {
        try (var stream = Files.list(directory)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
                    .sorted()
                    .toList();
        }
    }

    private static void writeTextPdf(Path output, String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                content.setLeading(12);
                content.newLineAtOffset(40, 760);
                for (String line : text.lines().toList()) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(output.toFile());
        }
    }

    private static String portalDuplicateText() {
        return """
                DANFSe v1.0
                Numero da DPS
                EMITENTE DA NFS-e
                NOME / NOME EMPRESARIAL
                FORNECEDOR TESTE LTDA
                CNPJ / CPF / NIF
                11.111.111/0001-11
                TOMADOR DO SERVICO
                NOME / NOME EMPRESARIAL
                CLIENTE TESTE LTDA
                CNPJ / CPF / NIF
                25.014.360/0001-73
                INTERMEDIARIO
                Numero da NFS-e
                123
                Data e Hora da emissao da NFS-e
                02/04/2026
                VALOR TOTAL DA NFS-E
                Valor do Servico R$ 100,00
                Valor Liquido da NFS-e R$ 100,00
                TOTAIS APROXIMADOS
                """;
    }

    private static String abrasfDuplicateText() {
        return """
                NFS-e Nota Fiscal
                Servico Eletronica
                Cod. de Autenticidade
                Detalhamento dos Tributos
                Dados do Prestador
                FORNECEDOR TESTE LTDA
                CPF/CNPJ
                11.111.111/0001-11
                Identificacao da Nota Fiscal
                Numero da Nota Fiscal
                123
                Data de Geracao da NFS-e
                02/04/2026
                Dados do Tomador de Servicos
                Razao Social : CLIENTE TESTE LTDA
                CNPJ/CPF : 25.014.360/0001-73
                Dados do Intermediario
                Vl. Total dos Servicos
                R$ 100,00
                Vl. Liquido da NotaFiscal
                R$ 100,00
                Informacoes Adicionais
                """;
    }

    private Path writeTwoCompanyConfig(boolean correctCompanyHasRest) throws Exception {
        Path config = tempDir.resolve("empresas_duas.yaml");
        String correctCompany = correctCompanyHasRest
                ? """
                  - id: empresa_correta
                    habilitada: true
                    cnpjTomador: "25.014.360/0001-73"
                    estrategiaMes: "direto"
                    pastaBase: "%s/empresa_correta"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                """.formatted(tempDir.toString().replace("\\", "/"))
                : """
                  - id: empresa_correta
                    habilitada: false
                    cnpjTomador: "25.014.360/0001-73"
                    estrategiaMes: "direto"
                    pastaBase: "."
                    pastas:
                      entrada: "."
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                """;
        Files.writeString(config, """
                empresas:
                  - id: empresa_errada
                    habilitada: true
                    cnpjTomador: "12.345.678/0001-95"
                    estrategiaMes: "direto"
                    pastaBase: "%s/empresa_errada"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                %s
                """.formatted(tempDir.toString().replace("\\", "/"), correctCompany));
        return config;
    }
}
