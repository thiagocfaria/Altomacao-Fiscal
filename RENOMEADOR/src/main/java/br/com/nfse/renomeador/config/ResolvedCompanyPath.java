package br.com.nfse.renomeador.config;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;

public record ResolvedCompanyPath(CompanyConfig company, Path root, Optional<YearMonth> month) {
}
