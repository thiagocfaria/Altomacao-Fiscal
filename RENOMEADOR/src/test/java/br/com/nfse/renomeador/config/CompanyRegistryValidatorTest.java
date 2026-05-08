package br.com.nfse.renomeador.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

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
                company("empresa_a", tempDir, ".", "25.014.360/0001-73"),
                company("empresa_b", tempDir, ".", "12.345.678/0001-95")
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
    void rejectsDuplicateCustomerTaxIdsAcrossEnabledDestinationCompanies() throws Exception {
        Files.createDirectories(tempDir.resolve("empresa_a"));
        Files.createDirectories(tempDir.resolve("empresa_b"));
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("empresa_a", tempDir.resolve("empresa_a"), ".", "25.014.360/0001-73"),
                company("empresa_b", tempDir.resolve("empresa_b"), ".", "25.014.360/0001-73")
        ));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ duplicado");
    }

    @Test
    void acceptsSameCustomerTaxIdForDifferentImportedMonths() throws Exception {
        Files.createDirectories(tempDir.resolve("abril"));
        Files.createDirectories(tempDir.resolve("maio"));
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("empresa_a_abril", tempDir.resolve("abril"), ".", "25.014.360/0001-73",
                        false, Optional.of(YearMonth.of(2026, 4))),
                company("empresa_a_maio", tempDir.resolve("maio"), ".", "25.014.360/0001-73",
                        false, Optional.of(YearMonth.of(2026, 5)))
        ));

        assertThatCode(() -> new CompanyRegistryValidator().validate(registry)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSameCustomerTaxIdTwiceForSameImportedMonth() throws Exception {
        Files.createDirectories(tempDir.resolve("maio_a"));
        Files.createDirectories(tempDir.resolve("maio_b"));
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("empresa_a_maio", tempDir.resolve("maio_a"), ".", "25.014.360/0001-73",
                        false, Optional.of(YearMonth.of(2026, 5))),
                company("empresa_b_maio", tempDir.resolve("maio_b"), ".", "25.014.360/0001-73",
                        false, Optional.of(YearMonth.of(2026, 5)))
        ));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CNPJ duplicado");
    }

    @Test
    void acceptsSameCustomerTaxIdForSourceOnlyCompany() throws Exception {
        Files.createDirectories(tempDir.resolve("origem"));
        Files.createDirectories(tempDir.resolve("destino"));
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("origem_generica", tempDir.resolve("origem"), ".", "25.014.360/0001-73", true),
                company("empresa_destino", tempDir.resolve("destino"), ".", "25.014.360/0001-73")
        ));

        assertThatCode(() -> new CompanyRegistryValidator().validate(registry)).doesNotThrowAnyException();
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

    @Test
    void rejectsFolderThatEscapesCompanyRoot() throws Exception {
        Files.createDirectories(tempDir.resolve("entrada"));
        CompanyRegistry registry = new CompanyRegistry(List.of(new CompanyConfig(
                "empresa_a",
                true,
                "25.014.360/0001-73",
                MonthStrategy.DIRECT,
                List.of(),
                tempDir,
                "{AAAA}/{MM}",
                new CompanyFolders("entrada", "../fora", "revisar", "originais", "logs",
                        "canceladas", "logs/processados.idx")
        )));

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("caminho inseguro")
                .hasMessageContaining("processados");
    }

    @Test
    void rejectsBackendRootInsideCompanyRestFolder() throws Exception {
        Files.createDirectories(tempDir);
        CompanyRegistry registry = new CompanyRegistry(
                List.of(company("empresa_a", tempDir, ".")),
                Optional.of(tempDir.resolve("backend"))
        );

        assertThatThrownBy(() -> new CompanyRegistryValidator().validate(registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backendRoot")
                .hasMessageContaining("REST");
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder) {
        return company(id, basePath, inputFolder, "25.014.360/0001-73");
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder, String taxId) {
        return company(id, basePath, inputFolder, taxId, false);
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder, String taxId,
                                         boolean sourceOnly) {
        return company(id, basePath, inputFolder, taxId, sourceOnly, Optional.empty());
    }

    private static CompanyConfig company(String id, Path basePath, String inputFolder, String taxId,
                                         boolean sourceOnly, Optional<YearMonth> importedMonth) {
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
                sourceOnly,
                importedMonth
        );
    }
}
