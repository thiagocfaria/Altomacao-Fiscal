package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.text.TextNormalizer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ParserSupport {
    private static final Pattern CNPJ = Pattern.compile("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final Pattern DATE = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
    private static final Pattern MONEY = Pattern.compile("R\\$\\s*([0-9.]+,[0-9]{2})");

    private ParserSupport() {
    }

    static Optional<String> firstCnpj(String text) {
        Matcher matcher = CNPJ.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    static Optional<String> firstDate(String text) {
        Matcher matcher = DATE.matcher(text);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    static List<BigDecimal> moneyValues(String text) {
        Matcher matcher = MONEY.matcher(text);
        List<BigDecimal> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(parseMoney(matcher.group(1)));
        }
        return values;
    }

    static BigDecimal parseMoney(String value) {
        return new BigDecimal(value.replace(".", "").replace(",", "."));
    }

    static String section(String text, String startMarker, String endMarker) {
        String normalized = TextNormalizer.normalize(text);
        int start = normalized.indexOf(TextNormalizer.normalize(startMarker));
        if (start < 0) {
            return "";
        }
        int end = normalized.indexOf(TextNormalizer.normalize(endMarker), start + 1);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(Math.min(start, text.length()), Math.min(end, text.length()));
    }

    static String firstMeaningfulLine(String text) {
        for (String line : text.lines().toList()) {
            String firstColumn = TextNormalizer.firstColumn(line);
            String normalized = TextNormalizer.normalize(firstColumn);
            if (firstColumn.isBlank()
                    || normalized.matches("\\d{2}/\\d{2}/\\d{4}.*")
                    || normalized.contains("DATA DE")
                    || normalized.contains("COD. DE")
                    || normalized.contains("CODIGO")
                    || normalized.contains("RESPONSAVEL")
                    || normalized.contains("DADOS DO PRESTADOR")
                    || normalized.contains("INSCRICAO MUNICIPAL")
                    || normalized.contains("CPF/CNPJ")
                    || normalized.contains("CEP ")
                    || normalized.contains("@")) {
                continue;
            }
            return cleanName(firstColumn);
        }
        return "";
    }

    static String cleanName(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("\\s+-\\s*$", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    static boolean isCancelled(String text) {
        String normalized = TextNormalizer.normalize(text);
        return normalized.contains("NOTA CANCELADA")
                || normalized.contains("SEM VALOR LEGAL")
                || normalized.contains("DATA DE CANCELAMENTO")
                || normalized.contains("JUSTIFICATIVA DE CANCELAMENTO");
    }

    static boolean hasPositiveRetention(String text, BigDecimal serviceValue, BigDecimal netValue) {
        String normalized = TextNormalizer.normalize(text);
        if (normalized.contains("NAO RETIDO") && (serviceValue == null || netValue == null || netValue.compareTo(serviceValue) == 0)) {
            return false;
        }
        if (normalized.contains("ISSQN RETIDO") && normalized.matches("(?s).*ISSQN RETIDO\\s+(SIM|R\\$\\s*[1-9].*).*")) {
            return true;
        }
        if (serviceValue != null && netValue != null && netValue.compareTo(serviceValue) < 0) {
            return true;
        }
        return false;
    }
}
