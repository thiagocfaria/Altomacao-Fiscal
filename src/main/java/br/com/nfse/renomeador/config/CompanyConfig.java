package br.com.nfse.renomeador.config;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public record CompanyConfig(
        String id,
        boolean enabled,
        String customerTaxId,
        MonthStrategy monthStrategy,
        List<String> months,
        Path basePath,
        String monthSubfolder,
        CompanyFolders folders,
        boolean sourceOnly,
        Optional<YearMonth> importedMonth
) {
    public CompanyConfig(String id, boolean enabled, String customerTaxId, MonthStrategy monthStrategy,
                         List<String> months, Path basePath, String monthSubfolder,
                         CompanyFolders folders) {
        this(id, enabled, customerTaxId, monthStrategy, months, basePath, monthSubfolder, folders, false, Optional.empty());
    }

    public CompanyConfig(String id, boolean enabled, String customerTaxId, MonthStrategy monthStrategy,
                         List<String> months, Path basePath, String monthSubfolder,
                         CompanyFolders folders, boolean sourceOnly) {
        this(id, enabled, customerTaxId, monthStrategy, months, basePath, monthSubfolder, folders, sourceOnly, Optional.empty());
    }

    public CompanyConfig {
        months = months == null ? List.of() : List.copyOf(months);
        importedMonth = importedMonth == null ? Optional.empty() : importedMonth;
    }
}
