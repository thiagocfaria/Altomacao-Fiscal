package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.processing.ProcessingStatus;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static br.com.nfse.renomeador.TestSamples.samplePdf;
import static org.assertj.core.api.Assertions.assertThat;

class BatchModeRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void batchProcessesMixedLotInHomologationModeAndPreservesInput() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Files.copy(samplePdf("NF 55034 OK.pdf"), input.resolve("NF 55034 OK.pdf"));
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(summary.count(ProcessingStatus.UNSUPPORTED)).isEqualTo(1);
        assertThat(input.resolve("NF 9 OK.pdf")).exists();
        assertThat(input.resolve("NF 55034 OK.pdf")).exists();
        assertThat(Files.list(tempDir.resolve("processados")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_9_") && name.endsWith(".pdf"));
        assertThat(Files.list(backendCompany("empresa_piloto").resolve("revisar")).map(path -> path.getFileName().toString()))
                .anyMatch(name -> name.startsWith("NFSE_DESCONHECIDA_MODELO_NAO_SUPORTADO_") && name.endsWith(".pdf"));
        assertThat(backendCompany("empresa_piloto").resolve("processados.idx")).exists();
        assertThat(operationalLog("empresa_piloto")).exists();
        assertThat(Files.readString(operationalLog("empresa_piloto")))
                .contains("duracaoMs=");
    }

    @Test
    void batchKeepsTechnicalFilesUnderBackendInsteadOfClientRest() throws Exception {
        Path systemRoot = Files.createDirectories(tempDir.resolve("sistema"));
        Path companyRoot = Files.createDirectories(tempDir.resolve("cliente"));
        Path input = Files.createDirectories(companyRoot.resolve("entrada"));
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfigAt(systemRoot.resolve("empresas.yaml"), "25.014.360/0001-73", companyRoot);

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(companyRoot.resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
        assertThat(companyRoot.resolve("logs")).doesNotExist();
        assertThat(companyRoot.resolve("originais")).doesNotExist();
        Path backendCompany = systemRoot.resolve("backend").resolve("empresas").resolve("empresa_piloto");
        assertThat(operationalLog(backendCompany)).exists();
        assertThat(backendCompany.resolve("processados.idx")).exists();
        assertThat(backendCompany.resolve("originais")).doesNotExist();
    }

    @Test
    void batchSendsRetainedInvoicesToRetidoFolder() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        writeTextPdf(input.resolve("retida.pdf"),
                portalDuplicateText().replace("Valor Liquido da NFS-e R$ 100,00", "Valor Liquido da NFS-e R$ 90,00"));
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("RETIDO")).isDirectoryContaining(path ->
                path.getFileName().toString().contains("##IR_RETIDO##"));
        assertThat(tempDir.resolve("processados")).doesNotExist();
    }

    @Test
    void batchUsesLedgerToSkipSecondRun() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig("25.014.360/0001-73");

        new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);
        var secondRun = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(secondRun.skipped()).isEqualTo(1);
        assertThat(secondRun.total()).isZero();
    }

    @Test
    void batchMovesOversizedPdfToReviewBeforePdfExtraction() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Path largePdf = input.resolve("gigante.pdf");
        try (RandomAccessFile file = new RandomAccessFile(largePdf.toFile(), "rw")) {
            file.setLength(br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline.MAX_FILE_SIZE_BYTES + 1);
        }
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.errors()).isEqualTo(1);
        assertThat(input.resolve("gigante.pdf")).doesNotExist();
        assertThat(backendCompany("empresa_piloto").resolve("revisar"))
                .isDirectoryContaining(path -> path.getFileName().toString()
                        .equals("ARQUIVO_MUITO_GRANDE_gigante.pdf"));
        assertThat(Files.readString(operationalLog("empresa_piloto")))
                .contains("Arquivo excede limite de 500MB");
    }

    @Test
    void batchRoutesWrongCompanyToReview() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        Files.copy(samplePdf("NF 9 OK.pdf"), input.resolve("NF 9 OK.pdf"));
        Path config = writeConfig("12.345.678/0001-95");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), true);

        assertThat(summary.count(ProcessingStatus.WRONG_COMPANY)).isEqualTo(1);
        assertThat(tempDir.resolve("TOMADOR NAO ENCONTRADO")).isDirectoryContaining(path ->
                path.getFileName().toString().contains("25014360000173"));
    }

    @Test
    void batchMovesInvoiceFromWrongFolderToCorrectKnownRestFolder() throws Exception {
        Path wrongInput = Files.createDirectories(tempDir.resolve("empresa_errada").resolve("entrada"));
        Files.createDirectories(tempDir.resolve("empresa_correta").resolve("entrada"));
        Files.copy(samplePdf("NF 9 OK.pdf"), wrongInput.resolve("NF 9 OK.pdf"));
        Path config = writeTwoCompanyConfig(true);

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("empresa_correta").resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
        assertThat(tempDir.resolve("empresa_errada").resolve("revisar")).doesNotExist();
        assertThat(wrongInput.resolve("NF 9 OK.pdf")).doesNotExist();
        assertThat(Files.readString(operationalLog("empresa_errada")))
                .contains("empresa_correta")
                .contains("NF 9 OK.pdf");
        assertThat(Files.readString(operationalLog("empresa_correta")))
                .contains("empresa_correta")
                .contains("NF 9 OK.pdf")
                .contains("SUMMARY\ttotal=1\tok=1");
        assertThat(Files.readString(backendCompany("empresa_correta").resolve("processados.idx")))
                .contains("empresa_correta")
                .contains("NF 9 OK.pdf");
    }

    @Test
    void batchRoutesSourceOnlyFolderByInvoiceCustomerTaxId() throws Exception {
        Path sourceInput = Files.createDirectories(tempDir.resolve("origem_generica").resolve("entrada"));
        Files.createDirectories(tempDir.resolve("empresa_correta").resolve("entrada"));
        Files.copy(samplePdf("NF 9 OK.pdf"), sourceInput.resolve("NF 9 OK.pdf"));
        Path config = writeSourceOnlyConfig();

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(tempDir.resolve("empresa_correta").resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
        assertThat(tempDir.resolve("origem_generica").resolve("TOMADOR NAO ENCONTRADO")).doesNotExist();
        assertThat(sourceInput.resolve("NF 9 OK.pdf")).doesNotExist();
        assertThat(Files.readString(operationalLog("empresa_correta")))
                .contains("SUMMARY\ttotal=1\tok=1");
    }

    @Test
    void batchKeepsInvoiceInReviewWhenCorrectCustomerHasNoRestFolder() throws Exception {
        Path wrongInput = Files.createDirectories(tempDir.resolve("empresa_errada").resolve("entrada"));
        Files.copy(samplePdf("NF 9 OK.pdf"), wrongInput.resolve("NF 9 OK.pdf"));
        Path config = writeTwoCompanyConfig(false);

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.errors()).isZero();
        assertThat(tempDir.resolve("empresa_errada").resolve("TOMADOR NAO ENCONTRADO")).isDirectoryContaining(path ->
                path.getFileName().toString().contains("25014360000173"));
        assertThat(wrongInput.resolve("NF 9 OK.pdf")).doesNotExist();
    }

    @Test
    void batchKeepsUnroutableWrongFolderInvoiceInTomadorNaoEncontrado() throws Exception {
        Path wrongInput = Files.createDirectories(tempDir.resolve("empresa_errada").resolve("entrada"));
        writeTextPdf(wrongInput.resolve("nota-sem-rest.pdf"), portalDuplicateText());
        Path config = writeTwoCompanyConfig(false);

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.errors()).isZero();
        Path folder = tempDir.resolve("empresa_errada").resolve("TOMADOR NAO ENCONTRADO");
        assertThat(folder).isDirectory();
        assertThat(pdfNames(folder)).singleElement().satisfies(name -> assertThat(name)
                .contains("CLIENTE_TESTE_LTDA")
                .contains("25014360000173")
                .contains("100,00"));
        assertThat(wrongInput.resolve("nota-sem-rest.pdf")).doesNotExist();
    }

    @Test
    void batchRecoversTomadorNaoEncontradoWhenCustomerRestPathBecomesActive() throws Exception {
        Path sourceRoot = tempDir.resolve("origem_generica");
        Path targetRoot = tempDir.resolve("empresa_correta");
        Files.createDirectories(sourceRoot.resolve("entrada"));
        Files.createDirectories(targetRoot.resolve("entrada"));
        Path pendingFolder = Files.createDirectories(sourceRoot.resolve("TOMADOR NAO ENCONTRADO"));
        Files.copy(samplePdf("NF 9 OK.pdf"), pendingFolder.resolve("pendente.pdf"));
        Path config = writeSourceOnlyConfig();

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(targetRoot.resolve("processados")).isDirectoryContaining(path ->
                path.getFileName().toString().startsWith("NFSE_9_"));
        assertThat(sourceRoot.resolve("TOMADOR NAO ENCONTRADO")).doesNotExist();
        assertThat(Files.readString(operationalLog("empresa_correta")))
                .contains("pendente.pdf")
                .contains("SUMMARY\ttotal=1\tok=1");
    }

    @Test
    void batchDeletesTomadorNaoEncontradoDuplicateWhenSamePdfWasAlreadyProcessedInTarget() throws Exception {
        Path sourceRoot = tempDir.resolve("origem_generica");
        Path targetRoot = tempDir.resolve("empresa_correta");
        Files.createDirectories(sourceRoot.resolve("entrada"));
        Path targetInput = Files.createDirectories(targetRoot.resolve("entrada"));
        Path config = writeSourceOnlyConfig();
        Files.copy(samplePdf("NF 9 OK.pdf"), targetInput.resolve("NF 9 OK.pdf"));
        new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);
        Path pendingFolder = Files.createDirectories(sourceRoot.resolve("TOMADOR NAO ENCONTRADO"));
        Files.copy(samplePdf("NF 9 OK.pdf"), pendingFolder.resolve("pendente.pdf"));

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.skipped()).isEqualTo(1);
        assertThat(sourceRoot.resolve("TOMADOR NAO ENCONTRADO")).doesNotExist();
        assertThat(pdfNames(targetRoot.resolve("processados"))).hasSize(1);
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
        assertThat(backendCompany("empresa_piloto").resolve("originais")).doesNotExist();
        assertThat(Files.readString(operationalLog("empresa_piloto")))
                .contains("DUPLICATE")
                .contains("ABRASF duplicada descartada; Portal Nacional equivalente ja existe");
    }

    @Test
    void batchDiscardsSameLayoutFiscalDuplicate() throws Exception {
        Path input = tempDir.resolve("entrada");
        Files.createDirectories(input);
        writeTextPdf(input.resolve("01-portal.pdf"), portalDuplicateText());
        writeTextPdf(input.resolve("02-portal-copia.pdf"), portalDuplicateText());
        Path config = writeConfig("25.014.360/0001-73");

        var summary = new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(summary.count(ProcessingStatus.OK)).isEqualTo(1);
        assertThat(summary.count(ProcessingStatus.DUPLICATE)).isEqualTo(1);
        assertThat(pdfNames(tempDir.resolve("processados")))
                .containsExactly("NFSE_123_FORNECEDOR_TESTE_LTDA_02.04.2026_100,00.pdf");
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
        assertThat(Files.readString(operationalLog("empresa_piloto")))
                .contains("ABRASF duplicada anterior removida por Portal Nacional equivalente");
    }

    @Test
    void batchDoesNotDeleteDuplicateIndexDestinationOutsideCompanyRoot() throws Exception {
        Path companyRoot = Files.createDirectories(tempDir.resolve("empresa"));
        Path input = companyRoot.resolve("entrada");
        Files.createDirectories(input);
        writeTextPdf(input.resolve("01-abrasf.pdf"), abrasfDuplicateText());
        Path config = writeConfig("25.014.360/0001-73", companyRoot);
        new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        Path outside = Files.createDirectories(tempDir.resolve("fora-da-empresa")).resolve("arquivo-externo.pdf");
        Files.writeString(outside, "nao deve ser apagado");
        rewriteDuplicateIndexDestination(companyRoot, outside);
        writeTextPdf(input.resolve("02-portal.pdf"), portalDuplicateText());

        new BatchModeRunner().run(config, Optional.empty(), Optional.<YearMonth>empty(), false);

        assertThat(outside).exists();
        assertThat(Files.readString(operationalLog("empresa_piloto")))
                .contains("fora da pasta da empresa");
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
        return writeConfig(taxId, tempDir);
    }

    private Path writeConfig(String taxId, Path root) throws Exception {
        Path config = tempDir.resolve("empresas.yaml");
        return writeConfigAt(config, taxId, root);
    }

    private Path writeConfigAt(Path config, String taxId, Path root) throws Exception {
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
                """.formatted(taxId, root.toString().replace("\\", "/")));
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

    private static void rewriteDuplicateIndexDestination(Path root, Path destination) throws Exception {
        Path index = root.getParent().resolve("backend").resolve("empresas")
                .resolve("empresa_piloto").resolve("duplicadas.idx");
        String line = Files.readString(index).lines().findFirst().orElseThrow();
        String[] parts = line.split("\t", -1);
        parts[3] = destination.toString();
        Files.writeString(index, String.join("\t", parts) + System.lineSeparator());
    }

    private Path backendCompany(String id) {
        return tempDir.resolve("backend").resolve("empresas").resolve(id);
    }

    private Path operationalLog(String id) {
        return operationalLog(backendCompany(id));
    }

    private static Path operationalLog(Path backendCompany) {
        return backendCompany.resolve("execucao-" + YearMonth.now() + ".tsv");
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

    private Path writeSourceOnlyConfig() throws Exception {
        Path config = tempDir.resolve("empresas_origem.yaml");
        Files.writeString(config, """
                empresas:
                  - id: origem_generica
                    habilitada: true
                    somenteOrigem: true
                    cnpjTomador: "000"
                    estrategiaMes: "direto"
                    pastaBase: "%s/origem_generica"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
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
                """.formatted(
                tempDir.toString().replace("\\", "/"),
                tempDir.toString().replace("\\", "/")));
        return config;
    }
}
