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
    private static final Pattern POSITIVE_RETENTION_VALUE = Pattern.compile(
            "(ISSQN RETIDO|VL\\. ISSQN RETIDO|TOTAL DAS RETENCOES FEDERAIS|PIS|COFINS|INSS|IRRF|CSLL|OUTRAS RETENCOES)[^\\r\\n]{0,120}R\\$\\s*(?!0+(?:\\.0+)*(?:,0{2,4})\\b)([0-9.]+,[0-9]{2,4})"
    );
    private static final Pattern MUNICIPAL_TAX_WITHHELD_YES = Pattern.compile("IMPOSTO RETIDO PELO TOMADOR\\s*:\\s*SIM");
    private static final Pattern MUNICIPAL_TAX_WITHHELD_NO = Pattern.compile("IMPOSTO RETIDO PELO TOMADOR\\s*:\\s*NAO");

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
        if (text == null || text.isEmpty()) {
            return "";
        }
        String normalizedStart = TextNormalizer.normalize(startMarker);
        String normalizedEnd = TextNormalizer.normalize(endMarker);
        int start = -1;
        int end = text.length();

        int offset = 0;
        while (offset < text.length()) {
            int lineEnd = lineEnd(text, offset);
            int nextOffset = nextLineOffset(text, lineEnd);
            String line = text.substring(offset, lineEnd);
            String normalizedLine = TextNormalizer.normalize(line);

            if (start < 0 && normalizedLine.contains(normalizedStart)) {
                start = offset;
            } else if (start >= 0 && normalizedLine.contains(normalizedEnd)) {
                end = offset;
                break;
            }

            offset = nextOffset;
        }
        if (start < 0) {
            return "";
        }
        return text.substring(start, end);
    }

    private static int lineEnd(String text, int offset) {
        int index = offset;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\n' || current == '\r') {
                return index;
            }
            index++;
        }
        return text.length();
    }

    private static int nextLineOffset(String text, int lineEnd) {
        if (lineEnd >= text.length()) {
            return text.length();
        }
        if (text.charAt(lineEnd) == '\r' && lineEnd + 1 < text.length() && text.charAt(lineEnd + 1) == '\n') {
            return lineEnd + 2;
        }
        return lineEnd + 1;
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

    static RetentionAnalysis retentionAnalysis(String text, BigDecimal serviceValue, BigDecimal netValue) {
        String normalized = TextNormalizer.normalize(text);
        boolean notRetained = normalized.contains("NAO RETIDO")
                || MUNICIPAL_TAX_WITHHELD_NO.matcher(normalized).find();
        boolean explicitPositive = normalized.matches("(?s).*ISSQN RETIDO\\s+SIM.*")
                || MUNICIPAL_TAX_WITHHELD_YES.matcher(normalized).find()
                || POSITIVE_RETENTION_VALUE.matcher(normalized).find();
        boolean netLowerThanService = serviceValue != null && netValue != null && netValue.compareTo(serviceValue) < 0;

        if (notRetained && (explicitPositive || netLowerThanService)) {
            return new RetentionAnalysis(false, true);
        }
        if (notRetained) {
            return new RetentionAnalysis(false, false);
        }
        if (explicitPositive) {
            return new RetentionAnalysis(true, false);
        }
        if (netLowerThanService && !hasPositiveDiscountOrDeduction(normalized)) {
            return new RetentionAnalysis(true, false);
        }
        return new RetentionAnalysis(false, false);
    }

    static boolean hasPositiveRetention(String text, BigDecimal serviceValue, BigDecimal netValue) {
        return retentionAnalysis(text, serviceValue, netValue).retained();
    }

    private static boolean hasPositiveDiscountOrDeduction(String normalized) {
        return normalized.matches("(?s).*(DESCONTO|DEDUCAO|DEDUCOES)[^\\r\\n]{0,120}R\\$\\s*(?!0+(?:,00)?)([0-9.]+,[0-9]{2}).*");
    }

    record RetentionAnalysis(boolean retained, boolean conflict) {
    }
}
