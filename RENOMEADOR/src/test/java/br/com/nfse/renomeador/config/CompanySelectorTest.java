package br.com.nfse.renomeador.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanySelectorTest {
    @Test
    void selectsOnlyEnabledCompaniesWhenNoIdIsProvided() {
        CompanyRegistry registry = new CompanyRegistry(List.of(
                company("empresa_a", true),
                company("empresa_b", false),
                company("empresa_c", true)
        ));

        List<CompanyConfig> selected = new CompanySelector().select(registry, Optional.empty());

        assertThat(selected).extracting(CompanyConfig::id).containsExactly("empresa_a", "empresa_c");
    }

    @Test
    void selectsRequestedEnabledCompany() {
        CompanyRegistry registry = new CompanyRegistry(List.of(company("empresa_a", true), company("empresa_b", true)));

        List<CompanyConfig> selected = new CompanySelector().select(registry, Optional.of("empresa_b"));

        assertThat(selected).extracting(CompanyConfig::id).containsExactly("empresa_b");
    }

    @Test
    void rejectsRequestedDisabledCompany() {
        CompanyRegistry registry = new CompanyRegistry(List.of(company("empresa_a", false)));

        assertThatThrownBy(() -> new CompanySelector().select(registry, Optional.of("empresa_a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desabilitada");
    }

    private static CompanyConfig company(String id, boolean enabled) {
        return new CompanyConfig(
                id,
                enabled,
                "25.014.360/0001-73",
                MonthStrategy.DIRECT,
                List.of(),
                Path.of("/dados/" + id),
                "{AAAA}/{MM}",
                new CompanyFolders("entrada", "processados", "revisar", "originais", "logs", "revisar/canceladas", "logs/processados.idx")
        );
    }
}
