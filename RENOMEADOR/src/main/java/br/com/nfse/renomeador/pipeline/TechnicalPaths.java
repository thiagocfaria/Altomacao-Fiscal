package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.nio.file.Path;
import java.time.YearMonth;

final class TechnicalPaths {
    private TechnicalPaths() {
    }

    static Path companyRoot(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return routes.backendRoot()
                .resolve("empresas")
                .resolve(safeCompanyId(companyPath.company().id()));
    }

    static Path log(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("execucao-" + YearMonth.now() + ".tsv");
    }

    static Path ledger(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return monthlyCompanyRoot(routes, companyPath, YearMonth.now()).resolve("processados.idx");
    }

    static Path duplicateIndex(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return monthlyCompanyRoot(routes, companyPath, YearMonth.now()).resolve("duplicadas.idx");
    }

    static Path splitWork(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("split-work");
    }

    static Path review(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("revisar");
    }

    private static String safeCompanyId(String value) {
        String safe = value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .strip();
        return safe.isBlank() ? "empresa_sem_id" : safe;
    }

    private static Path monthlyCompanyRoot(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath,
                                           YearMonth month) {
        return companyRoot(routes, companyPath).resolve(month.toString());
    }
}
