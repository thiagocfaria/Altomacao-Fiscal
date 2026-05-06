package br.com.nfse.renomeador.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyRegistryValidatorTest {
    @TempDir
    Path tempDir;

    @Test
    void acceptsDirectCompanyWithDotInputAndSafeOutputFolders() throws Exception {
        Files.createDirectories(tempDir);

        CompanyRegistry registry = new CompanyRegistry(List.of(company("empresa_a", tempDir, ".")));

        assertThatCode(() -> new CompanyRegistryValidator().validate(registry)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateCompanyIds() {
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("empresa_a", tempDir.resolve("a"), "."),
                company("empresa_a", tempDir.resolve("b"), ".")
        ));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id duplicado");
    }

    @Test
    void rejectsDuplicateInputFoldersAcrossCompanies() throws Exception {
        Files.createDirectories(tempDir);
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("empresa_a", tempDir, "."),
                company("empresa_b", tempDir, ".")
        ));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasta de entrada duplicada");
    }

    @Test
    void rejectsInvalidCustomerTaxId() throws Exception {
        Files.createDirectories(tempDir);
        CompanyRegistry registry = new CompanyRegistry(List.of(company("empresa_a", tempDir, ".", "123")));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ invalido");
    }

    @Test
    void acceptsInvalidCustomerTaxIdForSourceOnlyCompany() throws Exception {
        Files.createDirectories(tempDir);
        CompanyConfig sourceOnly = company("pasta_errada", tempDir, ".", "123", true);
        CompanyRegistry registry = new CompanyRegistry(List.of(sourceOnly));

        assertThatCode(() -> new CompanyRegistryValidator().validate(registry)).doesNotThrowAnyException();
    }

    @Test
    void rejectsOutputFolderEqualToInputFolder() throws Exception {
        Files.createDirectories(tempDir);
        CompanyRegistry registry = new CompanyRegistry(List.of(company("empresa_a", tempDir, "processados")));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pasta de saida coincide");
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder) {
        return company(id, basePath, inputFolder, "25.014.360/0001-73");
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder, String taxId) {
        return company(id, basePath, inputFolder, taxId, false);
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder, String taxId,
                                         boolean sourceOnly) {
        return new CompanyConfig(
                id,
                true,
                taxId,
                MonthStrategy.DIRECT,
                List.of(),
                basePath,
                "{AAAA}/{MM}",
                new CompanyFolders(inputFolder, "processados", "revisar", "originais", "logs",
                        "revisar/canceladas", "logs/processados.idx"),
                sourceOnly
        );
    }
}
