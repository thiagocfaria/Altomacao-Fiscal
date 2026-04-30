package br.com.nfse.renomeador.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonthlyPathResolverTest {
    @Test
    void resolvesCurrentMonthFromExecutionDate() {
        CompanyConfig company = company(MonthStrategy.CURRENT, List.of());

        List<ResolvedCompanyPath> paths = new MonthlyPathResolver()
                .resolve(company, Optional.empty(), LocalDate.of(2026, 4, 30));

        assertThat(paths).extracting(ResolvedCompanyPath::root)
                .containsExactly(Path.of("/dados/EmpresaA/NFSe/2026/04"));
    }

    @Test
    void resolvesInformedMonthFromCliArgument() {
        CompanyConfig company = company(MonthStrategy.INFORMED, List.of());

        List<ResolvedCompanyPath> paths = new MonthlyPathResolver()
                .resolve(company, Optional.of(YearMonth.of(2026, 3)), LocalDate.of(2026, 4, 30));

        assertThat(paths).extracting(ResolvedCompanyPath::root)
                .containsExactly(Path.of("/dados/EmpresaA/NFSe/2026/03"));
    }

    @Test
    void rejectsInformedStrategyWithoutInformedMonth() {
        CompanyConfig company = company(MonthStrategy.INFORMED, List.of());

        assertThatThrownBy(() -> new MonthlyPathResolver().resolve(company, Optional.empty(), LocalDate.of(2026, 4, 30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mes informado");
    }

    @Test
    void resolvesConfiguredMonthList() {
        CompanyConfig company = company(MonthStrategy.LIST, List.of("2026-02", "2026-04"));

        List<ResolvedCompanyPath> paths = new MonthlyPathResolver()
                .resolve(company, Optional.empty(), LocalDate.of(2026, 4, 30));

        assertThat(paths).extracting(ResolvedCompanyPath::root)
                .containsExactly(
                        Path.of("/dados/EmpresaA/NFSe/2026/02"),
                        Path.of("/dados/EmpresaA/NFSe/2026/04")
                );
    }

    @Test
    void directStrategyUsesBasePathWithoutMonthlySubfolder() {
        CompanyConfig company = company(MonthStrategy.DIRECT, List.of("2026-04"));

        List<ResolvedCompanyPath> paths = new MonthlyPathResolver()
                .resolve(company, Optional.empty(), LocalDate.of(2026, 4, 30));

        assertThat(paths).extracting(ResolvedCompanyPath::root)
                .containsExactly(Path.of("/dados/EmpresaA/NFSe"));
    }

    private static CompanyConfig company(MonthStrategy strategy, List<String> months) {
        return new CompanyConfig(
                "empresa_a",
                true,
                "25.014.360/0001-73",
                strategy,
                months,
                Path.of("/dados/EmpresaA/NFSe"),
                "{AAAA}/{MM}",
                new CompanyFolders("entrada", "processados", "revisar", "originais", "logs", "revisar/canceladas", "logs/processados.idx")
        );
    }
}
