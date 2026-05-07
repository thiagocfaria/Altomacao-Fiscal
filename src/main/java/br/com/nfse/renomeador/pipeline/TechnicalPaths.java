package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.nio.file.Path;

final class TechnicalPaths {
    private TechnicalPaths() {
    }

    static Path companyRoot(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return routes.backendRoot()
                .resolve("empresas")
                .resolve(safeCompanyId(companyPath.company().id()));
    }

    static Path log(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("execucao.log");
    }

    static Path ledger(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("processados.idx");
    }

    static Path duplicateIndex(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("duplicadas.idx");
    }

    static Path splitWork(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("split-work");
    }

    static Path originals(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath) {
        return companyRoot(routes, companyPath).resolve("originais");
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
}
