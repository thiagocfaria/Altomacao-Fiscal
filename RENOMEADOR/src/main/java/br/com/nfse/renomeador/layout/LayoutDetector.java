package br.com.nfse.renomeador.layout;

import br.com.nfse.renomeador.text.TextNormalizer;

public final class LayoutDetector {
    private static final int DEFAULT_MIN_TEXT_CHARS = 20;

    private final int minTextChars;

    public LayoutDetector() {
        this(DEFAULT_MIN_TEXT_CHARS);
    }

    public LayoutDetector(int minTextChars) {
        this.minTextChars = minTextChars;
    }

    public LayoutType detect(String text) {
        String normalized = TextNormalizer.normalize(text).replace('\u00A0', ' ').replaceAll("\\s+", " ");
        if (normalized.replaceAll("\\s+", "").length() < minTextChars) {
            return LayoutType.NO_TEXT;
        }
        if (containsAll(normalized, "DANFSE V1.0", "NUMERO DA DPS", "TOMADOR DO SERVICO")) {
            return LayoutType.PORTAL_NACIONAL;
        }
        if (isAbrasfIssnet(normalized) || isMunicipalAbrasf(normalized)) {
            return LayoutType.ABRASF_ISSNET;
        }
        return LayoutType.UNSUPPORTED;
    }

    private static boolean isAbrasfIssnet(String normalized) {
        return containsAll(normalized, "NFS-E NOTA FISCAL", "SERVICO ELETRONICA")
                && hasAuthenticationCode(normalized)
                && (normalized.contains("DADOS DO TOMADOR DE SERVICOS")
                || normalized.contains("DADOS DO TOMADOR")
                || normalized.contains("IDENTIFICACAO DO TOMADOR"));
    }

    private static boolean isMunicipalAbrasf(String normalized) {
        boolean hasInvoiceTitle = normalized.contains("NOTA FISCAL DE SERVICOS ELETRONICO")
                || normalized.contains("NOTA FISCAL ELETRONICA DE SERVICOS")
                || normalized.contains("NOTA FISCAL DE SERVICOS ELETRONICA")
                || normalized.contains("NOTA FISCAL DE SERVICO ELETRONICA")
                || (normalized.contains("NOTA FISCAL DE SERVICO") && normalized.contains("NFS-E"));
        return hasInvoiceTitle && hasAuthenticationCode(normalized) && hasProviderAndCustomer(normalized);
    }

    private static boolean hasAuthenticationCode(String normalized) {
        return normalized.contains("COD. DE AUTENTICIDADE")
                || normalized.contains("CODIGO DE AUTENTICIDADE")
                || normalized.contains("CODIGO AUTENTICIDADE")
                || normalized.contains("CODIGO VERIFICACAO")
                || normalized.contains("CODIGO DE VERIFICACAO");
    }

    private static boolean hasProviderAndCustomer(String normalized) {
        return containsAll(normalized, "PRESTADOR DE SERVICOS", "TOMADOR DE SERVICOS")
                || containsAll(normalized, "DADOS DO PRESTADOR", "DADOS DO TOMADOR")
                || containsAll(normalized, "DADOS DO PRESTADOR DE SERVICO", "DADOS DO TOMADOR DE SERVICOS")
                || containsAll(normalized, "IDENTIFICACAO DO PRESTADOR", "IDENTIFICACAO DO TOMADOR")
                || containsAll(normalized, "PRESTADOR DE SERVICOS", "NOME TOMADOR DE SERVICOS")
                || containsAll(normalized, "PREFEITURA MUNICIPAL", "DADOS DO TOMADOR", "CPF / CNPJ");
    }

    private static boolean containsAll(String text, String... markers) {
        for (String marker : markers) {
            if (!text.contains(marker)) {
                return false;
            }
        }
        return true;
    }
}
