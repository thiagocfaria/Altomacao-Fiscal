package br.com.nfse.importadorpn.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LeitorPlanilhaFiscalTest {
    @TempDir
    Path tempDir;

    @Test
    void carregaClientesAtivosEntradaRestCaminhoDmsECertificado() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path certs = Files.createDirectories(tempDir.resolve("certs"));
        Files.writeString(certs.resolve("cliente-a.pfx"), "certificado falso para dry-run");
        Path restCliente = Files.createDirectories(tempDir.resolve("cliente-a-rest"));
        Path dmsCliente = Files.createDirectories(tempDir.resolve("cliente-a-dms"));
        Path planilha = workbook(List.of(
                row("CLIENTE", "CNPJ", "CAMINHO REST\n(COLE OU SELECIONE A PASTA)", "CAMINHO DMS\n(DUPLO-CLIQUE)", "SOMENTE ORIGEM",
                        "CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)", "NOME CERTIFICADO DIGITAL",
                        "VALIDADE CERTIFICADO DIGITAL", "SENHA CERTIFICADO DIGITAL\n(OPCIONAL)", "IMPORT API PN ATIVO"),
                row("IMPORT API PN ENTRADA REST", "", entradaRest.toString(), "", "SIM", "", "", "", "", "SIM"),
                row("Cliente A", "11.222.333/0001-81", restCliente.toString(), dmsCliente.toString(), "",
                        certs.toString(), "cliente-a.pfx", "31/12/2099", "senha-planilha", "SIM"),
                row("Cliente B", "22.333.444/0001-52", tempDir.resolve("rest-b").toString(), "", "",
                        "", "", "", "", "NAO")));

        CadastroImportacao cadastro = new LeitorPlanilhaFiscal().ler(planilha);

        assertThat(cadastro.entradaRest()).contains(entradaRest);
        assertThat(cadastro.empresas()).extracting(EmpresaImportacao::nome)
                .containsExactly("Cliente A");
        EmpresaImportacao cliente = cadastro.empresas().get(0);
        assertThat(cliente.cnpj()).isEqualTo("11222333000181");
        assertThat(cliente.caminhoRest()).contains(restCliente);
        assertThat(cliente.caminhoDms()).contains(dmsCliente);
        assertThat(cliente.certificadoPasta()).contains(certs);
        assertThat(cliente.certificadoArquivo()).contains("cliente-a.pfx");
        assertThat(cliente.certificadoAlias()).contains("11222333000181");
        assertThat(cliente.senhaCertificadoPlanilha()).contains("senha-planilha");
        assertThat(cliente.validadeCertificado()).contains(LocalDate.of(2099, 12, 31));
    }

    @Test
    void carregaAbaDoMesInformadoQuandoDiferenteDoMesAtual() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest-mensal"));
        Path certs = Files.createDirectories(tempDir.resolve("certs-mensal"));
        Files.writeString(certs.resolve("cliente-junho.pfx"), "certificado falso");
        Path restMaio = Files.createDirectories(tempDir.resolve("rest-maio"));
        Path restJunho = Files.createDirectories(tempDir.resolve("rest-junho"));
        Path dmsJunho = Files.createDirectories(tempDir.resolve("dms-junho"));
        Path planilha = workbookMensal(entradaRest, certs, restMaio, restJunho, dmsJunho);

        CadastroImportacao cadastro = new LeitorPlanilhaFiscal().ler(planilha, YearMonth.of(2026, 6));

        assertThat(cadastro.entradaRest()).contains(entradaRest);
        assertThat(cadastro.empresas()).extracting(EmpresaImportacao::nome)
                .containsExactly("Cliente Junho");
        assertThat(cadastro.empresas().get(0).caminhoRest()).contains(restJunho);
        assertThat(cadastro.empresas().get(0).caminhoDms()).contains(dmsJunho);
    }

    @Test
    void lerTodasAbasNaoIncluiRotaDmsInativa() throws Exception {
        Path entradaRest = Files.createDirectories(tempDir.resolve("entrada-rest"));
        Path certs = Files.createDirectories(tempDir.resolve("certs"));
        Files.writeString(certs.resolve("cliente-a.pfx"), "certificado falso para dry-run");
        Path restCliente = Files.createDirectories(tempDir.resolve("cliente-a-rest"));
        Path dmsCliente = Files.createDirectories(tempDir.resolve("cliente-a-dms"));
        Path dmsRota = Files.createDirectories(tempDir.resolve("cliente-b-dms"));
        Path planilha = workbook(List.of(
                row("CLIENTE", "CNPJ", "CAMINHO REST\n(COLE OU SELECIONE A PASTA)", "CAMINHO DMS\n(DUPLO-CLIQUE)", "SOMENTE ORIGEM",
                        "CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)", "NOME CERTIFICADO DIGITAL",
                        "VALIDADE CERTIFICADO DIGITAL", "SENHA CERTIFICADO DIGITAL\n(OPCIONAL)", "IMPORT API PN ATIVO"),
                row("IMPORT API PN ENTRADA REST", "", entradaRest.toString(), "", "SIM", "", "", "", "", "SIM"),
                row("Cliente A", "11.222.333/0001-81", restCliente.toString(), dmsCliente.toString(), "",
                        certs.toString(), "cliente-a.pfx", "31/12/2099", "senha-planilha", "SIM"),
                row("Cliente B", "22.333.444/0001-52", "", dmsRota.toString(), "",
                        "", "", "", "", "NAO")));

        CadastroImportacao ativoMes = new LeitorPlanilhaFiscal().ler(planilha, YearMonth.of(2026, 5));
        CadastroImportacao todasAbas = new LeitorPlanilhaFiscal().lerTodasAbas(planilha);

        assertThat(ativoMes.empresas()).extracting(EmpresaImportacao::nome)
                .containsExactly("Cliente A");
        assertThat(todasAbas.empresas()).extracting(EmpresaImportacao::nome)
                .containsExactly("Cliente A");
        assertThat(todasAbas.empresas()).noneMatch(empresa -> empresa.nome().equals("Cliente B"));
    }

    private Path workbook(List<List<String>> rows) throws IOException {
        Path path = tempDir.resolve("PLANILHA_FISCAL.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CADASTRO MAIO");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                List<String> values = rows.get(rowIndex);
                for (int col = 0; col < values.size(); col++) {
                    row.createCell(col).setCellValue(values.get(col));
                }
            }
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private Path workbookMensal(Path entradaRest, Path certs, Path restMaio, Path restJunho,
                                Path dmsJunho) throws IOException {
        Path path = tempDir.resolve("PLANILHA_FISCAL_MENSAL.xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet maio = workbook.createSheet("CADASTRO MAIO");
            escreverAbaMensal(maio, entradaRest, "Cliente Maio", "11.222.333/0001-81",
                    restMaio, tempDir.resolve("dms-maio"), certs, "cliente-maio.pfx");
            Sheet junho = workbook.createSheet("CADASTRO JUNHO");
            escreverAbaMensal(junho, entradaRest, "Cliente Junho", "22.333.444/0001-52",
                    restJunho, dmsJunho, certs, "cliente-junho.pfx");
            try (OutputStream output = Files.newOutputStream(path)) {
                workbook.write(output);
            }
        }
        return path;
    }

    private static void escreverAbaMensal(Sheet sheet, Path entradaRest, String cliente, String cnpj,
                                          Path restCliente, Path dmsCliente, Path certs,
                                          String certArquivo) {
        Row titulo = sheet.createRow(0);
        titulo.createCell(0).setCellValue(sheet.getSheetName());
        Row header = sheet.createRow(1);
        List<String> columns = row("CLIENTE", "CNPJ", "CAMINHO REST\n(COLE OU SELECIONE A PASTA)",
                "CAMINHO DMS\n(DUPLO-CLIQUE)", "SOMENTE ORIGEM",
                "CAMINHO CERTIFICADO DIGITAL\n(DUPLO-CLIQUE)", "NOME CERTIFICADO DIGITAL",
                "VALIDADE CERTIFICADO DIGITAL", "SENHA CERTIFICADO DIGITAL\n(OPCIONAL)",
                "IMPORT API PN ATIVO");
        for (int col = 0; col < columns.size(); col++) {
            header.createCell(col).setCellValue(columns.get(col));
        }
        Row origem = sheet.createRow(2);
        origem.createCell(0).setCellValue("IMPORT API PN ENTRADA REST");
        origem.createCell(2).setCellValue(entradaRest.toString());
        origem.createCell(4).setCellValue("SIM");
        origem.createCell(9).setCellValue("SIM");
        Row empresa = sheet.createRow(3);
        empresa.createCell(0).setCellValue(cliente);
        empresa.createCell(1).setCellValue(cnpj);
        empresa.createCell(2).setCellValue(restCliente.toString());
        empresa.createCell(3).setCellValue(dmsCliente.toString());
        empresa.createCell(5).setCellValue(certs.toString());
        empresa.createCell(6).setCellValue(certArquivo);
        empresa.createCell(7).setCellValue("31/12/2099");
        empresa.createCell(8).setCellValue("senha");
        empresa.createCell(9).setCellValue("SIM");
    }

    private static List<String> row(String... values) {
        return List.of(values);
    }
}
