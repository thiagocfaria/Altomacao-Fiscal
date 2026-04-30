package br.com.nfse.renomeador.config;

public record CompanyFolders(
        String input,
        String processed,
        String review,
        String originals,
        String logs,
        String cancelled,
        String ledger
) {
}
