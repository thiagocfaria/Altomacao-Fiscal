package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;

import java.nio.file.Path;

public record InputCandidate(ResolvedCompanyPath companyPath, Path source) {
}
