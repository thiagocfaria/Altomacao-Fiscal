package br.com.nfse.renomeador.naming;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.processing.ProcessingStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FileNameBuilder {
    public String build(InvoiceData invoice, ProcessingStatus status) {
        String number = normalizeNumber(invoice.number());
        String date = normalizeDate(invoice.issueDate());
        String provider = sanitize(invoice.providerName().isBlank() ? "DESCONHECIDO" : invoice.providerName());
        String value = normalizeValue(invoice.serviceValue());

        return switch (status) {
            case CANCELLED -> "NFSE_" + number + "_" + provider + "_" + date + "_##CANCELADA##.pdf";
            case WRONG_COMPANY -> "NFSE_" + number + "_CNPJ_INCORRETO_" + provider + "_" + date + ".pdf";
            case UNSUPPORTED -> "NFSE_" + number + "_MODELO_NAO_SUPORTADO_" + date + ".pdf";
            case MISSING_REQUIRED -> "NFSE_" + number + "_DADOS_AUSENTES_" + date + ".pdf";
            case RETENTION_CONFLICT -> "NFSE_" + number + "_RETENCAO_CONFLITANTE_" + provider + "_" + date + ".pdf";
            case OK -> "NFSE_" + number + "_" + provider + "_" + date + "_" + value
                    + (invoice.retained() ? "_##RETIDO##" : "") + ".pdf";
        };
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
        // DD/MM/YYYY -> YYYYMMDD
        String[] parts = date.split("/");
        if (parts.length == 3 && parts[2].length() == 4) {
            return parts[2] + parts[1] + parts[0];
        }
        return date.replaceAll("[/\\-]", "");
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
}
