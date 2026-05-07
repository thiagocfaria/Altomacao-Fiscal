package br.com.nfse.renomeador.naming;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileNameBuilder {
    private static final int MAX_FILE_NAME_LENGTH = 150;
    private static final Pattern BRAZILIAN_DATE = Pattern.compile("^(\\d{2})[./-](\\d{2})[./-](\\d{4})$");
    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})[./-](\\d{2})[./-](\\d{2})$");
    private static final Pattern DATE_SEPARATOR = Pattern.compile("[/\\-]");

    public String build(InvoiceData invoice, ProcessingStatus status) {
        String number = normalizeNumber(invoice.number());
        String date = normalizeDate(invoice.issueDate());
        String provider = sanitize(invoice.providerName().isBlank() ? "DESCONHECIDO" : invoice.providerName());
        String value = normalizeValue(invoice.serviceValue());

        return switch (status) {
            case CANCELLED -> withProvider("NFSE_" + number + "_", provider, "_" + date + "_##CANCELADA##.pdf");
            case WRONG_COMPANY -> withProvider("NFSE_" + number + "_CNPJ_INCORRETO_", provider, "_" + date + ".pdf");
            case UNSUPPORTED -> "NFSE_" + number + "_MODELO_NAO_SUPORTADO_" + date + ".pdf";
            case MISSING_REQUIRED -> "NFSE_" + number + "_DADOS_AUSENTES_" + date + ".pdf";
            case RETENTION_CONFLICT -> withProvider("NFSE_" + number + "_RETENCAO_CONFLITANTE_", provider, "_" + date + ".pdf");
            case DUPLICATE -> withProvider("NFSE_" + number + "_DUPLICADA_", provider, "_" + date + ".pdf");
            case OK -> withProvider("NFSE_" + number + "_", provider, "_" + date + "_" + value
                    + (invoice.retained() ? "_##IR_RETIDO##" : "") + ".pdf");
        };
    }

    public String buildMissingCustomerPath(InvoiceData invoice) {
        String number = normalizeNumber(invoice.number());
        String customer = sanitize(invoice.customerName().isBlank() ? "TOMADOR_DESCONHECIDO" : invoice.customerName());
        String customerTaxId = digits(invoice.customerTaxId());
        String taxId = customerTaxId.isBlank() ? "CNPJ_DESCONHECIDO" : customerTaxId;
        String value = normalizeValue(invoice.serviceValue());
        String suffix = "_CNPJ_" + taxId + "_VALOR_" + value + ".pdf";
        return withProvider("NFSE_" + number + "_", customer, suffix);
    }

    private static String withProvider(String prefix, String provider, String suffix) {
        int maxProviderLength = MAX_FILE_NAME_LENGTH - prefix.length() - suffix.length();
        String safeProvider = provider;
        if (maxProviderLength > 0 && provider.length() > maxProviderLength) {
            safeProvider = provider.substring(0, maxProviderLength).replaceFirst("_+$", "");
        }
        return prefix + safeProvider + suffix;
    }

    private static String normalizeNumber(String number) {
        String digits = number == null ? "" : number.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return "DESCONHECIDA";
        }
        String stripped = digits.replaceFirst("^0+", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static String normalizeDate(String date) {
        if (date == null || date.isBlank()) {
            return "sem-data";
        }
        String value = date.strip();
        Matcher brazilian = BRAZILIAN_DATE.matcher(value);
        if (brazilian.matches()) {
            return "%s.%s.%s".formatted(brazilian.group(1), brazilian.group(2), brazilian.group(3));
        }
        Matcher iso = ISO_DATE.matcher(value);
        if (iso.matches()) {
            return "%s.%s.%s".formatted(iso.group(3), iso.group(2), iso.group(1));
        }
        return DATE_SEPARATOR.matcher(value).replaceAll(".");
    }

    private static String normalizeValue(BigDecimal value) {
        if (value == null) {
            return "0,00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace(".", ",");
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .strip();
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }
}
