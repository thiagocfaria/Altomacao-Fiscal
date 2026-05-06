package br.com.nfse.renomeador.config;

import java.nio.file.Path;
import java.util.List;

public record CompanyConfig(
        String id,
        boolean enabled,
        String customerTaxId,
        MonthStrategy monthStrategy,
        List<String> months,
        Path basePath,
        String monthSubfolder,
        CompanyFolders folders,
        boolean sourceOnly
) {
    public CompanyConfig(String id, boolean enabled, String customerTaxId, MonthStrategy monthStrategy,
                         List<String> months, Path basePath, String monthSubfolder,
                         CompanyFolders folders) {
        this(id, enabled, customerTaxId, monthStrategy, months, basePath, monthSubfolder, folders, false);
    }

    public CompanyConfig {
        months = months == null ? List.of() : List.copyOf(months);
    }
}
