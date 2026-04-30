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
        if (containsAll(normalized,
                "NFS-E NOTA FISCAL",
                "SERVICO ELETRONICA",
                "COD. DE AUTENTICIDADE",
                "DETALHAMENTO DOS TRIBUTOS",
                "DADOS DO TOMADOR DE SERVICOS")) {
            return LayoutType.ABRASF_ISSNET;
        }
        return LayoutType.UNSUPPORTED;
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
