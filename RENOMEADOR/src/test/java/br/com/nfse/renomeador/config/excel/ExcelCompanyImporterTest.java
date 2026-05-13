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
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

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
    void importsCadastroSheetWhenDashboardIsFirstSheet() throws Exception {
        Path activeFolder = Files.createDirectories(tempDir.resolve("rest-ativo"));
        Path workbook = workbookWithDashboardAndCadastro(
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
    }

    @Test
    void importsMonthlyCadastroSheetForRequestedMonth() throws Exception {
        Path aprilFolder = Files.createDirectories(tempDir.resolve("abril"));
        Path mayFolder = Files.createDirectories(tempDir.resolve("maio"));
        Path workbook = monthlyCadastroWorkbook(aprilFolder.toString(), mayFolder.toString(), "");
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "", true,
                Optional.of(YearMonth.of(2026, 5)), LocalDate.of(2026, 4, 15));

        var company = new CompanyRegistryLoader().load(output).companies().get(0);
        assertThat(company.basePath()).isEqualTo(mayFolder);
    }

    @Test
    void importsMonthlyCadastroSheetForCurrentMonthWhenMonthIsNotForced() throws Exception {
        Path aprilFolder = Files.createDirectories(tempDir.resolve("abril-default"));
        Path mayFolder = Files.createDirectories(tempDir.resolve("maio-default"));
        Path juneFolder = Files.createDirectories(tempDir.resolve("junho-default"));
        Path workbook = monthlyCadastroWorkbook(aprilFolder.toString(), mayFolder.toString(), juneFolder.toString());
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "", true,
                Optional.empty(), LocalDate.of(2026, 6, 5));

        var company = new CompanyRegistryLoader().load(output).companies().get(0);
        assertThat(company.basePath()).isEqualTo(juneFolder);
    }

    @Test
    void importAllMonthsKeepsSingleTechnicalRestInputWhenRepeatedAcrossMonthlySheets() throws Exception {
        Path globalRest = Files.createDirectories(tempDir.resolve("entrada-rest-global"));
        Path aprilFolder = Files.createDirectories(tempDir.resolve("abril-cliente"));
        Path mayFolder = Files.createDirectories(tempDir.resolve("maio-cliente"));
        Path workbook = monthlyCadastroWorkbookWithTechnicalRest(globalRest, aprilFolder, mayFolder);
        Path output = tempDir.resolve("empresas-todos-meses.yaml");

        new ExcelCompanyImporter().importAllMonthsToYaml(workbook, output, true,
                LocalDate.of(2026, 5, 10));

        var companies = new CompanyRegistryLoader().load(output).companies();
        assertThat(companies).filteredOn(company -> company.sourceOnly()).hasSize(1);
        assertThat(companies).filteredOn(company -> company.sourceOnly()).first()
                .extracting(company -> company.basePath())
                .isEqualTo(globalRest);
        assertThat(companies).filteredOn(company -> !company.sourceOnly())
                .extracting(company -> company.basePath())
                .containsExactly(aprilFolder, mayFolder);
    }

    @Test
    void skipsSourceOnlyRowsWithoutPathInMonthlyCadastro() throws Exception {
        Path activeFolder = Files.createDirectories(tempDir.resolve("maio-ativo"));
        Path workbook = monthlyCadastroWorkbookWithRows(
                monthlyRow("Origem Ainda Sem Caminho", "25.014.360/0001-73", "", "SIM"),
                monthlyRow("Cliente Ativo Maio", "12.345.678/0001-95", activeFolder.toString(), "")
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "", true,
                Optional.of(YearMonth.of(2026, 5)), LocalDate.of(2026, 5, 7));

        var companies = new CompanyRegistryLoader().load(output).companies();
        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).id()).isEqualTo("cliente_ativo_maio");
        assertThat(companies.get(0).basePath()).isEqualTo(activeFolder);
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
                dashboardRow("Origem Errada", "123", wrongFolder.toString(), "SIM"),
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
    void importsTechnicalRestInputWithoutCnpjWhenMarkedSourceOnly() throws Exception {
        Path globalRestInput = Files.createDirectories(tempDir.resolve("entrada-rest-global"));
        Path targetFolder = Files.createDirectories(tempDir.resolve("destino-correto"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("IMPORT API PN ENTRADA REST", "", globalRestInput.toString(), "SIM"),
                dashboardRow("Cliente Correto", "25.014.360/0001-73", targetFolder.toString())
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var registry = new CompanyRegistryLoader().load(output);
        assertThat(registry.companies()).hasSize(2);
        var source = registry.companies().get(0);
        assertThat(source.id()).isEqualTo("import_api_pn_entrada_rest");
        assertThat(source.enabled()).isTrue();
        assertThat(source.sourceOnly()).isTrue();
        assertThat(source.customerTaxId()).isBlank();
        assertThat(source.basePath()).isEqualTo(globalRestInput);
    }

    @Test
    void ignoresDmsSourceOnlyRowBecauseRenomeadorOnlyConsumesRestInput() throws Exception {
        Path dmsInput = Files.createDirectories(tempDir.resolve("entrada-dms-global"));
        Path targetFolder = Files.createDirectories(tempDir.resolve("destino-rest-correto"));
        Path workbook = dashboardFiscalWorkbookWithDms(
                dashboardDmsRow("IMPORT API PN ENTRADA DMS", "", dmsInput.toString(), "", "SIM"),
                dashboardDmsRow("Cliente Correto", "25.014.360/0001-73", "", targetFolder.toString(), "")
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var companies = new CompanyRegistryLoader().load(output).companies();
        assertThat(companies).hasSize(1);
        assertThat(companies.get(0).id()).isEqualTo("cliente_correto");
        assertThat(companies.get(0).sourceOnly()).isFalse();
        assertThat(companies.get(0).basePath()).isEqualTo(targetFolder);
    }

    @Test
    void ignoresSourceOnlyMarkerWhenClientHasValidCnpjAndRestPath() throws Exception {
        Path targetFolder = Files.createDirectories(tempDir.resolve("destino-correto"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Cliente Correto", "25.014.360/0001-73", targetFolder.toString(), "SIM")
        );
        Path output = tempDir.resolve("empresas.yaml");

        new ExcelCompanyImporter().importToYaml(workbook, output, "");

        var company = new CompanyRegistryLoader().load(output).companies().get(0);
        assertThat(company.enabled()).isTrue();
        assertThat(company.sourceOnly()).isFalse();
        assertThat(company.basePath()).isEqualTo(targetFolder);
    }

    @Test
    void rejectsInvalidCnpjWithRestWhenSourceOnlyIsNotExplicit() throws Exception {
        Path wrongFolder = Files.createDirectories(tempDir.resolve("pasta-errada"));
        Path workbook = dashboardFiscalWorkbook(
                dashboardRow("Origem Errada", "123", wrongFolder.toString(), "")
        );

        assertThatThrownBy(() -> new ExcelCompanyImporter().importToYaml(workbook, tempDir.resolve("out.yaml"), ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOMENTE ORIGEM");
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
            header.createCell(18).setCellValue("SOMENTE ORIGEM");
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index + 2);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(3).setCellValue(rows[index][1]);
                row.createCell(17).setCellValue(rows[index][2]);
                if (rows[index].length > 3) {
                    row.createCell(18).setCellValue(rows[index][3]);
                }
            }
            wb.write(out);
        }
        return workbook;
    }

    private Path dashboardFiscalWorkbookWithDms(String[]... rows) throws Exception {
        Path workbook = tempDir.resolve("dashboard-dms.xlsm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet sheet = wb.createSheet("Dashboard Fiscal");
            sheet.createRow(0).createCell(0).setCellValue("DASHBOARD FISCAL");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(16).setCellValue("CAMINHO DMS\n(duplo-clique)");
            header.createCell(17).setCellValue("CAMINHO REST\n(duplo-clique)");
            header.createCell(22).setCellValue("SOMENTE ORIGEM");
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index + 2);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(3).setCellValue(rows[index][1]);
                row.createCell(16).setCellValue(rows[index][2]);
                row.createCell(17).setCellValue(rows[index][3]);
                row.createCell(22).setCellValue(rows[index][4]);
            }
            wb.write(out);
        }
        return workbook;
    }

    private Path workbookWithDashboardAndCadastro(String[]... rows) throws Exception {
        Path workbook = tempDir.resolve("dashboard-cadastro.xlsm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet dashboard = wb.createSheet("DASHBOARD");
            dashboard.createRow(0).createCell(0).setCellValue("RENOMEADOR NFS-e");
            Sheet cadastro = wb.createSheet("CADASTRO");
            cadastro.createRow(0).createCell(0).setCellValue("CADASTRO OPERACIONAL");
            Row header = cadastro.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(1).setCellValue("GRUPO ECONOMICO");
            header.createCell(2).setCellValue("CIDADE");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(17).setCellValue("CAMINHO REST\n(duplo-clique)");
            header.createCell(19).setCellValue("SOMENTE ORIGEM");
            for (int index = 0; index < rows.length; index++) {
                Row row = cadastro.createRow(index + 2);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(3).setCellValue(rows[index][1]);
                row.createCell(17).setCellValue(rows[index][2]);
                if (rows[index].length > 3) {
                    row.createCell(19).setCellValue(rows[index][3]);
                }
            }
            wb.write(out);
        }
        return workbook;
    }

    private Path monthlyCadastroWorkbook(String aprilPath, String mayPath, String junePath) throws Exception {
        Path workbook = tempDir.resolve("cadastro-mensal.xlsm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            monthlySheet(wb, "CADASTRO ABRIL", aprilPath);
            monthlySheet(wb, "CADASTRO MAIO", mayPath);
            monthlySheet(wb, "CADASTRO JUNHO", junePath);
            wb.write(out);
        }
        return workbook;
    }

    private Path monthlyCadastroWorkbookWithRows(String[]... rows) throws Exception {
        Path workbook = tempDir.resolve("cadastro-mensal-linhas.xlsm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            Sheet sheet = wb.createSheet("CADASTRO MAIO");
            sheet.createRow(0).createCell(0).setCellValue("CADASTRO MAIO");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("CLIENTE");
            header.createCell(2).setCellValue("CIDADE");
            header.createCell(3).setCellValue("CNPJ");
            header.createCell(17).setCellValue("CAMINHO REST\n(COLE OU SELECIONE A PASTA)");
            header.createCell(19).setCellValue("CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)");
            header.createCell(22).setCellValue("SOMENTE ORIGEM");
            for (int index = 0; index < rows.length; index++) {
                Row row = sheet.createRow(index + 2);
                row.createCell(0).setCellValue(rows[index][0]);
                row.createCell(3).setCellValue(rows[index][1]);
                row.createCell(17).setCellValue(rows[index][2]);
                row.createCell(19).setCellValue("/certificados/cliente.pfx");
                row.createCell(22).setCellValue(rows[index][3]);
            }
            wb.write(out);
        }
        return workbook;
    }

    private Path monthlyCadastroWorkbookWithTechnicalRest(Path globalRest, Path aprilPath, Path mayPath)
            throws Exception {
        Path workbook = tempDir.resolve("cadastro-mensal-origem-tecnica.xlsm");
        try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(workbook)) {
            monthlySheetWithTechnicalRest(wb, "CADASTRO ABRIL", globalRest, aprilPath,
                    "Cliente Abril", "25.014.360/0001-73");
            monthlySheetWithTechnicalRest(wb, "CADASTRO MAIO", globalRest, mayPath,
                    "Cliente Maio", "26.474.286/0002-11");
            wb.write(out);
        }
        return workbook;
    }

    private static void monthlySheetWithTechnicalRest(XSSFWorkbook wb, String sheetName, Path globalRest,
                                                       Path restPath, String cliente, String cnpj) {
        Sheet sheet = wb.createSheet(sheetName);
        sheet.createRow(0).createCell(0).setCellValue(sheetName);
        Row header = sheet.createRow(1);
        header.createCell(0).setCellValue("CLIENTE");
        header.createCell(3).setCellValue("CNPJ");
        header.createCell(17).setCellValue("CAMINHO REST\n(COLE OU SELECIONE A PASTA)");
        header.createCell(22).setCellValue("SOMENTE ORIGEM");

        Row origem = sheet.createRow(2);
        origem.createCell(0).setCellValue("IMPORT API PN ENTRADA REST");
        origem.createCell(17).setCellValue(globalRest.toString());
        origem.createCell(22).setCellValue("SIM");

        Row empresa = sheet.createRow(3);
        empresa.createCell(0).setCellValue(cliente);
        empresa.createCell(3).setCellValue(cnpj);
        empresa.createCell(17).setCellValue(restPath.toString());
    }

    private static void monthlySheet(XSSFWorkbook wb, String sheetName, String restPath) {
        Sheet sheet = wb.createSheet(sheetName);
        sheet.createRow(0).createCell(0).setCellValue(sheetName);
        Row header = sheet.createRow(1);
        header.createCell(0).setCellValue("CLIENTE");
        header.createCell(2).setCellValue("CIDADE");
        header.createCell(3).setCellValue("CNPJ");
        header.createCell(17).setCellValue("CAMINHO REST\n(COLE OU SELECIONE A PASTA)");
        header.createCell(18).setCellValue("CAMINHO ENTRADA/SAIDA\n(DUPLO-CLIQUE)");
        header.createCell(19).setCellValue("CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)");
        header.createCell(22).setCellValue("SOMENTE ORIGEM");
        Row row = sheet.createRow(2);
        row.createCell(0).setCellValue("Cliente Mensal");
        row.createCell(3).setCellValue("25.014.360/0001-73");
        row.createCell(17).setCellValue(restPath);
        row.createCell(19).setCellValue("/certificados/cliente.pfx");
    }

    private static String[] row(String name, String taxId, String path) {
        return new String[]{name, taxId, path};
    }

    private static String[] dashboardRow(String name, String taxId, String restPath) {
        return new String[]{name, taxId, restPath};
    }

    private static String[] dashboardRow(String name, String taxId, String restPath, String sourceOnly) {
        return new String[]{name, taxId, restPath, sourceOnly};
    }

    private static String[] dashboardDmsRow(String name, String taxId, String dmsPath, String restPath,
                                            String sourceOnly) {
        return new String[]{name, taxId, dmsPath, restPath, sourceOnly};
    }

    private static String[] monthlyRow(String name, String taxId, String restPath, String sourceOnly) {
        return new String[]{name, taxId, restPath, sourceOnly};
    }
}
