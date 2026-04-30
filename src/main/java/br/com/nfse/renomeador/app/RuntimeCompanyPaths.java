package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyRegistry;
import br.com.nfse.renomeador.config.CompanyRegistryLoader;
import br.com.nfse.renomeador.config.MonthlyPathResolver;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

final class RuntimeCompanyPaths {
    private final CompanyRegistryLoader loader = new CompanyRegistryLoader();
    private final MonthlyPathResolver resolver = new MonthlyPathResolver();

    List<ResolvedCompanyPath> load(Path config, Optional<String> companyId, Optional<YearMonth> month) throws IOException {
        CompanyRegistry registry = loader.load(config);
        List<CompanyConfig> companies = selectedCompanies(registry, companyId);
        return companies.stream()
                .filter(CompanyConfig::enabled)
                .flatMap(company -> resolver.resolve(company, month, LocalDate.now()).stream())
                .toList();
    }

    private static List<CompanyConfig> selectedCompanies(CompanyRegistry registry, Optional<String> companyId) {
        if (companyId.isEmpty()) {
            return registry.companies();
        }
        return List.of(registry.companyById(companyId.orElseThrow())
                .orElseThrow(() -> new IllegalArgumentException("Empresa nao encontrada: " + companyId.orElseThrow())));
    }
}
