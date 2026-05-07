package br.com.nfse.renomeador.text;

import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String noAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toUpperCase(Locale.ROOT);
    }

    public static String firstColumn(String line) {
        if (line == null) {
            return "";
        }
        String[] columns = line.strip().split("\\s{2,}");
        return columns.length == 0 ? "" : columns[0].strip();
    }

    public static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
