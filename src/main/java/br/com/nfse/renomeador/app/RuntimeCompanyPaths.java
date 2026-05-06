package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyConfig;
import br.com.nfse.renomeador.config.CompanyRegistry;
import br.com.nfse.renomeador.config.CompanyRegistryLoader;
import br.com.nfse.renomeador.config.CompanyRegistryValidator;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.CompanySelector;
import br.com.nfse.renomeador.config.MonthlyPathResolver;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.List;

final class RuntimeCompanyPaths {
    private final CompanyRegistryLoader loader = new CompanyRegistryLoader();
    private final MonthlyPathResolver resolver = new MonthlyPathResolver();
    private final CompanyRegistryValidator validator = new CompanyRegistryValidator();
    private final CompanySelector selector = new CompanySelector();

    List<ResolvedCompanyPath> load(Path config, Optional<String> companyId, Optional<YearMonth> month) throws IOException {
        return loadRoutes(config, companyId, month).monitoredPaths();
    }

    CompanyRouteDirectory loadRoutes(Path config, Optional<String> companyId, Optional<YearMonth> month) throws IOException {
        CompanyRegistry registry = loader.load(config);
        validator.validateBasics(registry);
        List<ResolvedCompanyPath> allActivePaths = registry.companies().stream()
                .filter(CompanyConfig::enabled)
                .flatMap(company -> resolver.resolve(company, month, LocalDate.now()).stream())
                .toList();
        validator.validateResolvedPaths(allActivePaths);
        List<CompanyConfig> selected = selector.select(registry, companyId);
        List<ResolvedCompanyPath> monitoredPaths = selected.stream()
                .filter(CompanyConfig::enabled)
                .flatMap(company -> resolver.resolve(company, month, LocalDate.now()).stream())
                .toList();
        return CompanyRouteDirectory.from(registry, monitoredPaths, allActivePaths);
    }
}
