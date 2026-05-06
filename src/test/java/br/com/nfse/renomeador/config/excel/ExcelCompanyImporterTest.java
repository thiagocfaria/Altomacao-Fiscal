package br.com.nfse.renomeador.config.excel;

import br.com.nfse.renomeador.config.CompanyRegistryLoader;
import br.com.nfse.renomeador.config.MonthStrategy;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelCompanyImporterTest {
    @TempDir
    Path tempDir;

    @Test
    void importsCompaniesFromTextPathCellsToYaml() throws Exception {
        Path companyFolder = Files.createDirectories(tempDir.resolve("Empresa A"));
        Path workbook = workbookWithRows(row("Empresa A", "25.014.360/0001-73", companyFolder.toString()));
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var registry = new CompanyRegistryLoader().load(output);
        assertThat(registry.companies()).hasSize(1);
        var company = registry.companies().get(0);
        assertThat(company.id()).isEqualTo("empresa_a");
        assertThat(company.customerTaxId()).isEqualTo("25.014.360/0001-73");
        assertThat(company.monthStrategy()).isEqualTo(MonthStrategy.DIRECT);
        assertThat(company.basePath()).isEqualTo(companyFolder);
        assertThat(company.folders().input()).isEqualTo(".");
        assertThat(company.folders().processed()).isEqualTo("processados");
        assertThat(company.folders().review()).isEqualTo("revisar");
    }

    @Test
    void importsPathFromCellHyperlinkWhenPresent() throws Exception {
        Path companyFolder = Files.createDirectories(tempDir.resolve("Empresa Link"));
        Path workbook = workbookWithHyperlink("Empresa Link", "25.014.360/0001-73", companyFolder);
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var company = new CompanyRegistryLoader().load(output).companies().get(0);
        assertThat(company.basePath()).isEqualTo(companyFolder);
    }

    @Test
    void importsDashboardFiscalUsingRestColumnAndHeaderOnSecondRow() throws Exception {
        Path activeFolder = Files.createDirectories(tempDir.resolve("rest-ativo"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Cliente Ativo", "25.014.360/0001-73", activeFolder.toString()),
                dashboardRow("Cliente Sem Rest", "12.345.678/0001-95", "")
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var registry = new CompanyRegistryLoader().load(output);
        assertThat(registry.companies()).hasSize(2);
        assertThat(registry.companies().get(0).id()).isEqualTo("cliente_ativo");
        assertThat(registry.companies().get(0).enabled()).isTrue();
        assertThat(registry.companies().get(0).basePath()).isEqualTo(activeFolder);
        assertThat(registry.companies().get(1).id()).isEqualTo("cliente_sem_rest");
        assertThat(registry.companies().get(1).enabled()).isFalse();
        assertThat(registry.companies().get(1).basePath()).isEqualTo(Path.of("."));
    }

    @Test
    void padsValidThirteenDigitCnpjFromDashboard() throws Exception {
        Path activeFolder = Files.createDirectories(tempDir.resolve("rest"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Escola Bilingue", "8587897000103", activeFolder.toString())
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var company = new CompanyRegistryLoader().load(output).companies().get(0);
        assertThat(company.customerTaxId()).isEqualTo("08.587.897/0001-03");
    }

    @Test
    void skipsDashboardRowsWithoutCnpjWhenRestIsBlank() throws Exception {
        Path activeFolder = Files.createDirectories(tempDir.resolve("rest"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Cliente Ativo", "25.014.360/0001-73", activeFolder.toString()),
                dashboardRow("Cliente Sem Cnpj", "", "")
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        assertThat(new CompanyRegistryLoader().load(output).companies()).hasSize(1);
    }

    @Test
    void rejectsDashboardRowsWithoutCnpjWhenRestIsFilled() throws Exception {
        Path activeFolder = Files.createDirectories(tempDir.resolve("rest"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Cliente Sem Cnpj", "", activeFolder.toString())
        );

        assertThatThrownBy(() -> new ExcelCompanyImporter().importToYaml(workbook, tempDir.resolve("out.yaml"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Linha incompleta");
    }

    @Test
    void importsDashboardRowsWithInvalidCnpjAndRestAsSourceOnly() throws Exception {
        Path wrongFolder = Files.createDirectories(tempDir.resolve("pasta-errada"));
        Path targetFolder = Files.createDirectories(tempDir.resolve("destino-correto"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Origem Errada", "123", wrongFolder.toString()),
                dashboardRow("Cliente Correto", "25.014.360/0001-73", targetFolder.toString())
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var registry = new CompanyRegistryLoader().load(output);
        assertThat(registry.companies()).hasSize(2);
        var source = registry.companies().get(0);
        assertThat(source.id()).isEqualTo("origem_errada");
        assertThat(source.enabled()).isTrue();
        assertThat(source.sourceOnly()).isTrue();
        assertThat(source.customerTaxId()).isEqualTo("123");
        assertThat(source.basePath()).isEqualTo(wrongFolder);
        assertThat(registry.companies().get(1).sourceOnly()).isFalse();
    }

    @Test
    void createsUniqueIdsForRepeatedClientNames() throws Exception {
        Path firstFolder = Files.createDirectories(tempDir.resolve("rest1"));
        Path secondFolder = Files.createDirectories(tempDir.resolve("rest2"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Cliente Repetido", "25.014.360/0001-73", firstFolder.toString()),
                dashboardRow("Cliente Repetido", "12.345.678/0001-95", secondFolder.toString())
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        assertThat(new CompanyRegistryLoader().load(output).companies())
                .extracting(company -> company.id())
                .containsExactly("cliente_repetido", "cliente_repetido_2");
    }

    @Test
    void rejectsMissingRequiredHeaders() throws Exception {
        Path workbook = tempDir.resolve("empresas.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet sheet = wb.createSheet("Empresas");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Empresa");
            wb.write(out);
        }

        assertThatThrownBy(() -> new ExcelCompanyImporter().importToYaml(workbook, tempDir.resolve("out.yaml"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cabecalho obrigatorio");
    }

    @Test
    void refusesToOverwriteExistingYamlByDefault() throws Exception {
        Path companyFolder = Files.createDirectories(tempDir.resolve("Empresa A"));
        Path workbook = workbookWithRows(row("Empresa A", "25.014.360/0001-73", companyFolder.toString()));
        Path output = tempDir.resolve("empresas.yaml");
        Files.writeString(output, "existente");

        assertThatThrownBy(() -> new ExcelCompanyImporter().importToYaml(workbook, output, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ja existe");
    }

    private Path workbookWithRows(String[]... rows) throws Exception {
        Path workbook = tempDir.resolve("empresas.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet sheet = wb.createSheet("Empresas");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Empresa");
            header.createCell(1).setCellValue("CNPJ");
            header.createCell(2).setCellValue("Caminho");
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index + 1);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(1).setCellValue(rows[index][1]);
                row.createCell(2).setCellValue(rows[index][2]);
            }
            wb.write(out);
        }
        return workbook;
    }

    private Path workbookWithHyperlink(String name, String taxId, Path path) throws Exception {
        Path workbook = tempDir.resolve("empresas_link.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet sheet = wb.createSheet("Empresas");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Empresa");
            header.createCell(1).setCellValue("CNPJ");
            header.createCell(2).setCellValue("Caminho");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(name);
            row.createCell(1).setCellValue(taxId);
            Cell pathCell = row.createCell(2);
            pathCell.setCellValue("abrir pasta");
            CreationHelper helper = wb.getCreationHelper();
            var hyperlink = helper.createHyperlink(HyperlinkType.FILE);
            hyperlink.setAddress(path.toUri().toString());
            pathCell.setHyperlink(hyperlink);
            wb.write(out);
        }
        return workbook;
    }

    private Path dashboardFiscalWorkbook(String[]... rows) throws Exception {
        Path workbook = tempDir.resolve("dashboard.xlsm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet sheet = wb.createSheet("Dashboard Fiscal");
            sheet.createRow(0).createCell(0).setCellValue("DASHBOARD FISCAL");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(1).setCellValue("GRUPO ECONOMICO");
            header.createCell(2).setCellValue("EMPRESA");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(17).setCellValue("CAMINHO REST\n(duplo-clique)");
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index + 2);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(3).setCellValue(rows[index][1]);
                row.createCell(17).setCellValue(rows[index][2]);
            }
            wb.write(out);
        }
        return workbook;
    }

    private static String[] row(String name, String taxId, String path) {
        return new String[]{name, taxId, path};
    }

    private static String[] dashboardRow(String name, String taxId, String restPath) {
        return new String[]{name, taxId, restPath};
    }
}
