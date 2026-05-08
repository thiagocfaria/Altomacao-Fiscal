package br.com.nfse.renomeador.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record CompanyRegistry(List<CompanyConfig> companies, Optional<Path> backendRoot) {
    public CompanyRegistry(List<CompanyConfig> companies) {
        this(companies, Optional.empty());
    }

    public CompanyRegistry {
        companies = companies == null ? List.of() : List.copyOf(companies);
        backendRoot = backendRoot == null ? Optional.empty() : backendRoot;
    }

    public Optional<CompanyConfig> companyById(String id) {
        return companies.stream()
                .filter(company -> company.id().equals(id))
                .findFirst();
    }
}
