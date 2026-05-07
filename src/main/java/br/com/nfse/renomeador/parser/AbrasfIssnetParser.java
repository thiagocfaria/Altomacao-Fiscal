package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.text.TextNormalizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AbrasfIssnetParser implements InvoiceParser {
    private static final Pattern NUMBER_AFTER_LABEL = Pattern.compile("(?s)N[uú]mero da Nota Fiscal\\s*\\R+\\s*(\\d+)");
    private static final Pattern MUNICIPAL_HEADER_NUMBER = Pattern.compile("(?s)NOTA FISCAL ELETRONICA.*?\\bN[º°O]?\\s*(?:DA\\s+)?(\\d+)\\b");
    private static final Pattern MUNICIPAL_NUMBER_AFTER_DA = Pattern.compile("(?m)\\bN[º°O]?\\s+DA\\s+(\\d+)\\b");
    private static final Pattern MUNICIPAL_STANDALONE_NUMBER = Pattern.compile("(?m)^\\s*N[º°O]?\\s*(\\d+)\\s*$");
    private static final Pattern MUNICIPAL_NUMERO_DA_NOTA = Pattern.compile("(?s)NUMERO DA NOTA\\D{0,80}?(\\d{4,})");
    private static final Pattern CUSTOMER_NAME = Pattern.compile("(?m)Raz[aã]o Social\\s*:\\s*(.+)$");
    private static final Pattern NAME_AFTER_NOME_RAZAO = Pattern.compile("(?m)Nome/Raz[aã]o:\\s*(.+)$");
    private static final Pattern RAZAO_SOCIAL_UPPER = Pattern.compile("(?m)RAZAO SOCIAL:\\s*(.+)$");
    private static final Pattern NOME_RAZAO_SOCIAL_UPPER = Pattern.compile("(?m)NOME/RAZAO SOCIAL:\\s*(.+)$");
    private static final Pattern MUNICIPAL_SERVICE_VALUE = Pattern.compile("VALOR DOS SERVICOS\\s*R\\$\\s*([0-9.]+,[0-9]{2})");
    private static final Pattern MUNICIPAL_NET_VALUE = Pattern.compile("VALOR LIQUIDO\\s*R\\$\\s*([0-9.]+,[0-9]{2})");

    @Override
    public InvoiceData parse(String text) {
        String normalized = TextNormalizer.normalize(text);
        String prestador = ParserSupport.section(text, "Dados do Prestador", "Identificacao da Nota Fiscal");
        if (prestador.isBlank()) {
            prestador = ParserSupport.section(text, "Dados do Prestador", "Identificação da Nota Fiscal");
        }
        if (prestador.isBlank()) {
            prestador = ParserSupport.section(text, "PRESTADOR DE SERVICOS", "TOMADOR DE SERVICOS");
        }
        if (prestador.isBlank()) {
            prestador = ParserSupport.section(normalized, "PRESTADOR DE SERVICOS", "TOMADOR DE SERVICOS");
        }
        String tomador = ParserSupport.section(text, "Dados do Tomador de Servicos", "Dados do Intermediario");
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(text, "Dados do Tomador de Serviços", "Dados do Intermediário");
        }
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(text, "TOMADOR DE SERVICOS", "Discriminacao dos servicos prestados");
        }
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(normalized, "TOMADOR DE SERVICOS", "DISCRIMINACAO DOS SERVICOS");
        }
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(normalized, "TOMADOR DE SERVICOS", "RETENCOES FEDERAIS");
        }
        List<BigDecimal> money = ParserSupport.moneyValues(ParserSupport.section(text, "Vl. Total dos Servicos", "Informacoes Adicionais"));
        BigDecimal serviceValue = money.isEmpty() ? null : money.get(0);
        BigDecimal netValue = money.isEmpty() ? null : money.get(money.size() - 1);
        if (serviceValue == null) {
            serviceValue = findMoney(MUNICIPAL_SERVICE_VALUE, normalized);
        }
        if (netValue == null) {
            netValue = findMoney(MUNICIPAL_NET_VALUE, normalized);
        }
        ParserSupport.RetentionAnalysis retention = ParserSupport.retentionAnalysis(text, serviceValue, netValue);

        return new InvoiceData(
                LayoutType.ABRASF_ISSNET,
                findNumber(text),
                ParserSupport.firstDate(text).orElse(""),
                findProviderName(prestador),
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
        String normalized = TextNormalizer.normalize(text);
        matcher = MUNICIPAL_HEADER_NUMBER.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_NUMBER_AFTER_DA.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_STANDALONE_NUMBER.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_NUMERO_DA_NOTA.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String findProviderName(String prestador) {
        String provider = findNameAfterNomeRazao(prestador);
        if (!provider.isBlank()) {
            return provider;
        }
        Matcher matcher = RAZAO_SOCIAL_UPPER.matcher(prestador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        return ParserSupport.firstMeaningfulLine(prestador);
    }

    private static String findCustomerName(String tomador) {
        Matcher matcher = CUSTOMER_NAME.matcher(tomador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        String result = findNameAfterNomeRazao(tomador);
        if (!result.isBlank()) {
            return result;
        }
        matcher = NOME_RAZAO_SOCIAL_UPPER.matcher(tomador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        return "";
    }

    private static String findNameAfterNomeRazao(String text) {
        Matcher matcher = NAME_AFTER_NOME_RAZAO.matcher(text);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        return "";
    }

    private static BigDecimal findMoney(Pattern pattern, String normalizedText) {
        Matcher matcher = pattern.matcher(normalizedText);
        if (matcher.find()) {
            return ParserSupport.parseMoney(matcher.group(1));
        }
        return null;
    }
}
