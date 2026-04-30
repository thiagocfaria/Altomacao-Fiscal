package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutType;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AbrasfIssnetParser implements InvoiceParser {
    private static final Pattern NUMBER_AFTER_LABEL = Pattern.compile("(?s)N[uú]mero da Nota Fiscal\\s*\\R+\\s*(\\d+)");
    private static final Pattern CUSTOMER_NAME = Pattern.compile("(?m)Raz[aã]o Social\\s*:\\s*(.+)$");

    @Override
    public InvoiceData parse(String text) {
        String prestador = ParserSupport.section(text, "Dados do Prestador", "Identificacao da Nota Fiscal");
        if (prestador.isBlank()) {
            prestador = ParserSupport.section(text, "Dados do Prestador", "Identificação da Nota Fiscal");
        }
        String tomador = ParserSupport.section(text, "Dados do Tomador de Servicos", "Dados do Intermediario");
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(text, "Dados do Tomador de Serviços", "Dados do Intermediário");
        }
        List<BigDecimal> money = ParserSupport.moneyValues(ParserSupport.section(text, "Vl. Total dos Servicos", "Informacoes Adicionais"));
        BigDecimal serviceValue = money.isEmpty() ? null : money.get(0);
        BigDecimal netValue = money.isEmpty() ? null : money.get(money.size() - 1);
        ParserSupport.RetentionAnalysis retention = ParserSupport.retentionAnalysis(text, serviceValue, netValue);

        return new InvoiceData(
                LayoutType.ABRASF_ISSNET,
                findNumber(text),
                ParserSupport.firstDate(text).orElse(""),
                ParserSupport.firstMeaningfulLine(prestador),
                ParserSupport.firstCnpj(prestador).orElse(""),
                findCustomerName(tomador),
                ParserSupport.firstCnpj(tomador).orElse(""),
                serviceValue,
                netValue,
                retention.retained(),
                retention.conflict(),
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

    private static String findCustomerName(String tomador) {
        Matcher matcher = CUSTOMER_NAME.matcher(tomador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        return "";
    }
}
