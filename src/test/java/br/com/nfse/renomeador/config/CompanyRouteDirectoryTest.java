package br.com.nfse.renomeador.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyRouteDirectoryTest {
    @Test
    void sourceOnlyCompanyIsMonitoredButNotUsedAsTaxIdDestination() {
        CompanyConfig sourceOnly = company("pasta_errada", "123", Path.of("/tmp/errada"), true);
        CompanyConfig target = company("cliente_correto", "25.014.360/0001-73", Path.of("/tmp/correta"), false);
        ResolvedCompanyPath sourcePath = new ResolvedCompanyPath(sourceOnly, sourceOnly.basePath(), Optional.empty());
        ResolvedCompanyPath targetPath = new ResolvedCompanyPath(target, target.basePath(), Optional.empty());

        CompanyRouteDirectory routes = CompanyRouteDirectory.from(
                new CompanyRegistry(List.of(sourceOnly, target)),
                List.of(sourcePath, targetPath),
                List.of(sourcePath, targetPath)
        );

        assertThat(routes.monitoredPaths()).contains(sourcePath);
        assertThat(routes.hasKnownCustomerTaxId("123")).isFalse();
        assertThat(routes.activePathForCustomerTaxId("123")).isEmpty();
        assertThat(routes.activePathForCustomerTaxId("25.014.360/0001-73")).contains(targetPath);
    }

    private static CompanyConfig company(String id, String taxId, Path path, boolean sourceOnly) {
        return new CompanyConfig(
                id,
                true,
                taxId,
                MonthStrategy.DIRECT,
                List.of(),
                path,
                "{AAAA}/{MM}",
                new CompanyFolders(".", "processados", "revisar", "originais", "logs",
                        "revisar/canceladas", "logs/processados.idx"),
                sourceOnly
        );
    }
}
