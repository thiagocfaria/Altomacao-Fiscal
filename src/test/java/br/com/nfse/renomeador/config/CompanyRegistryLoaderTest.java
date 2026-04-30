package br.com.nfse.renomeador.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyRegistryLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsMultipleCompaniesFromExternalYaml() throws Exception {
        Path yaml = tempDir.resolve("empresas.yaml");
        Files.writeString(yaml, """
                empresas:
                  - id: empresa_a
                    habilitada: true
                    cnpjTomador: "25.014.360/0001-73"
                    estrategiaMes: "lista"
                    meses: ["2026-04", "2026-05"]
                    pastaBase: "/dados/EmpresaA/NFSe"
                    subpastaMes: "{AAAA}/{MM}"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                  - id: empresa_b
                    habilitada: false
                    cnpjTomador: "00.000.000/0001-00"
                    estrategiaMes: "direto"
                    meses: []
                    pastaBase: "/dados/EmpresaB/NFSe"
                    subpastaMes: "{AAAA}/{MM}"
                    pastas:
                      entrada: "entrada"
                      processados: "processados"
                      revisar: "revisar"
                      originais: "originais"
                      logs: "logs"
                      canceladas: "revisar/canceladas"
                      ledger: "logs/processados.idx"
                """);

        CompanyRegistry registry = new CompanyRegistryLoader().load(yaml);

        assertThat(registry.companies()).hasSize(2);
        CompanyConfig company = registry.companyById("empresa_a").orElseThrow();
        assertThat(company.enabled()).isTrue();
        assertThat(company.customerTaxId()).isEqualTo("25.014.360/0001-73");
        assertThat(company.monthStrategy()).isEqualTo(MonthStrategy.LIST);
        assertThat(company.months()).containsExactly("2026-04", "2026-05");
        assertThat(company.folders().ledger()).isEqualTo("logs/processados.idx");
    }

    @Test
    void rejectsCompanyWithoutRequiredCustomerTaxId() throws Exception {
        Path yaml = tempDir.resolve("empresas.yaml");
        Files.writeString(yaml, """
                empresas:
                  - id: empresa_a
                    habilitada: true
                    estrategiaMes: "direto"
                    pastaBase: "/dados/EmpresaA/NFSe"
                    pastas:
                      entrada: "entrada"
                """);

        assertThatThrownBy(() -> new CompanyRegistryLoader().load(yaml))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cnpjTomador");
    }
}
