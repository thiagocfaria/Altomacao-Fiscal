package br.com.nfse.renomeador.config;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public final class MonthlyPathResolver {
    public List<ResolvedCompanyPath> resolve(CompanyConfig company, Optional<YearMonth> informedMonth, LocalDate executionDate) {
        return switch (company.monthStrategy()) {
            case CURRENT -> List.of(resolveMonth(company, YearMonth.from(executionDate)));
            case INFORMED -> List.of(resolveMonth(company, informedMonth.orElseThrow(
                    () -> new IllegalArgumentException("mes informado e obrigatorio para estrategia informado"))));
            case LIST -> company.months().stream()
                    .map(MonthlyPathResolver::parseMonth)
                    .map(month -> resolveMonth(company, month))
                    .toList();
            case DIRECT -> List.of(new ResolvedCompanyPath(company, company.basePath(), company.importedMonth()));
        };
    }

    private static ResolvedCompanyPath resolveMonth(CompanyConfig company, YearMonth month) {
        String relative = company.monthSubfolder()
                .replace("{AAAA}", "%04d".formatted(month.getYear()))
                .replace("{MM}", "%02d".formatted(month.getMonthValue()));
        Path root = company.basePath().resolve(relative);
        return new ResolvedCompanyPath(company, root, Optional.of(month));
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("mes invalido: " + value, exception);
        }
    }
}
