package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.text.TextNormalizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PortalNacionalParser implements InvoiceParser {
    private static final Pattern NUMBER_AFTER_LABEL = Pattern.compile("(?s)N[uú]mero da NFS-e.*?\\R\\s*(\\d+)");

    @Override
    public InvoiceData parse(String text) {
        String emitente = ParserSupport.section(text, "EMITENTE DA NFS-e", "TOMADOR DO SERVICO");
        String tomador = ParserSupport.section(text, "TOMADOR DO SERVICO", "INTERMEDIARIO");
        String valores = ParserSupport.section(text, "VALOR TOTAL DA NFS-E", "TOTAIS APROXIMADOS");
        List<BigDecimal> money = ParserSupport.moneyValues(valores);
        BigDecimal serviceValue = money.isEmpty() ? null : money.get(0);
        BigDecimal netValue = money.isEmpty() ? null : money.get(money.size() - 1);

        return new InvoiceData(
                LayoutType.PORTAL_NACIONAL,
                findNumber(text),
                ParserSupport.firstDate(text).orElse(""),
                findPartyName(emitente),
                ParserSupport.firstCnpj(emitente).orElse(""),
                findPartyName(tomador),
                ParserSupport.firstCnpj(tomador).orElse(""),
                serviceValue,
                netValue,
                ParserSupport.hasPositiveRetention(text, serviceValue, netValue),
                ParserSupport.isCancelled(text)
        );
    }

    private static String findNumber(String text) {
        Matcher matcher = NUMBER_AFTER_LABEL.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String findPartyName(String section) {
        boolean nextLineIsName = false;
        for (String line : section.lines().toList()) {
            String normalized = TextNormalizer.normalize(line);
            if (nextLineIsName) {
                String value = TextNormalizer.firstColumn(line)
                        .replaceAll("\\s+\\S+@\\S+.*$", "")
                        .strip();
                if (!value.isBlank()) {
                    return ParserSupport.cleanName(value);
                }
            }
            if (normalized.contains("NOME / NOME EMPRESARIAL")) {
                nextLineIsName = true;
            }
        }
        return "";
    }
}
