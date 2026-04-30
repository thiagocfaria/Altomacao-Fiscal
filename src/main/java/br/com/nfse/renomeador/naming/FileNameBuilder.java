package br.com.nfse.renomeador.naming;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.processing.ProcessingStatus;

public final class FileNameBuilder {
    public String build(InvoiceData invoice, ProcessingStatus status) {
        String number = normalizeNumber(invoice.number());
        String date = normalizeDate(invoice.issueDate());
        String provider = sanitize(invoice.providerName().isBlank() ? "DESCONHECIDO" : invoice.providerName());

        String marker = switch (status) {
            case CANCELLED -> " ##CANCELADA##";
            case WRONG_COMPANY -> " CNPJ INCORRETO PARA REPOSITORIO";
            case UNSUPPORTED -> " MODELO NAO SUPORTADO";
            case MISSING_REQUIRED -> " DADOS OBRIGATORIOS AUSENTES";
            case OK -> invoice.retained() ? " ##RETIDO##" : "";
        };

        String name = "NF " + number + " " + provider + " " + date + marker + ".pdf";
        return name.replaceAll("\\s+", " ").strip();
    }

    private static String normalizeNumber(String number) {
        String digits = number == null ? "" : number.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return "DESCONHECIDA";
        }
        if (digits.length() >= 6) {
            return digits;
        }
        return "0".repeat(6 - digits.length()) + digits;
    }

    private static String normalizeDate(String date) {
        if (date == null || date.isBlank()) {
            return "sem-data";
        }
        return date.replace("/", ".");
    }

    private static String sanitize(String value) {
        return value.replaceAll("[\\\\/:*?\"<>|]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }
}
