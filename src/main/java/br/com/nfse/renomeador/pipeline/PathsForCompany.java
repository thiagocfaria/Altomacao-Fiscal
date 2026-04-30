package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.nio.file.Path;

final class PathsForCompany {
    private PathsForCompany() {
    }

    static Path input(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().input());
    }

    static Path processed(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().processed());
    }

    static Path review(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().review());
    }

    static Path originals(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().originals());
    }

    static Path logs(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().logs());
    }

    static Path cancelled(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().cancelled());
    }

    static Path ledger(ResolvedCompanyPath companyPath) {
        return companyPath.root().resolve(companyPath.company().folders().ledger());
    }
}
