package br.com.nfse.renomeador.naming;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.processing.ProcessingStatus;

public final class FileNameBuilder {
    public String build(InvoiceData invoice, ProcessingStatus status) {
        String number = normalizeNumber(invoice.number());
        String date = normalizeDate(invoice.issueDate());
        String provider = sanitize(invoice.providerName().isBlank() ? "DESCONHECIDO" : invoice.providerName());

        String name = switch (status) {
            case CANCELLED -> "NF " + number + " " + provider + " " + date + " ##CANCELADA##.pdf";
            case WRONG_COMPANY -> "NF " + number + " CNPJ INCORRETO PARA REPOSITORIO " + provider + " " + date + ".pdf";
            case UNSUPPORTED -> "NF " + number + " MODELO NAO SUPORTADO " + date + ".pdf";
            case MISSING_REQUIRED -> "NF " + number + " DADOS OBRIGATORIOS AUSENTES " + date + ".pdf";
            case RETENTION_CONFLICT -> "NF " + number + " RETENCAO CONFLITANTE " + provider + " " + date + ".pdf";
            case OK -> "NF " + number + " " + provider + " " + date + (invoice.retained() ? " ##RETIDO##" : "") + ".pdf";
        };

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
