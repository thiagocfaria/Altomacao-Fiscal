package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.text.TextNormalizer;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AbrasfIssnetParser implements InvoiceParser {
    private static final Pattern NUMBER_AFTER_LABEL = Pattern.compile("(?m)N[uú]mero da Nota(?: Fiscal)?\\s*\\R\\s*(\\d{1,12})\\s*$");
    private static final Pattern NUMBER_AT_END_OF_NEXT_LINE = Pattern.compile("(?m)N[uú]mero da Nota(?: Fiscal)?\\s*\\R[^\\r\\n]*?\\b(\\d{1,12})\\s*$");
    private static final Pattern BARUERI_NUMBER = Pattern.compile("(?s)CODIGO AUTENTICIDADE\\s+NUMERO DA NOTA\\s+SERIE DA NOTA.*?\\b(\\d{4,12})\\b.*?NUMERO RPS");
    private static final Pattern NUMBER_BEFORE_GENERATION_DATE = Pattern.compile("(?s)NUMERO DA NOTA FISCAL.*\\b(\\d{1,12})\\s+DATA DE GERACAO");
    private static final Pattern MUNICIPAL_HEADER_NUMBER = Pattern.compile("(?s)NOTA FISCAL ELETRONICA.*?\\bN[º°O]?\\s*(?:DA\\s+)?(\\d+)\\b");
    private static final Pattern MUNICIPAL_NUMBER_AFTER_DA = Pattern.compile("(?m)\\bN[º°O]?\\s+DA\\s+(\\d+)\\b");
    private static final Pattern MUNICIPAL_STANDALONE_NUMBER = Pattern.compile("(?m)^\\s*N[º°O]?\\s*(\\d+)\\s*$");
    private static final Pattern MUNICIPAL_NUMERO_DA_NOTA = Pattern.compile("(?s)NUMERO DA NOTA\\D{0,80}?(\\d{4,})");
    private static final Pattern CUSTOMER_NAME = Pattern.compile("(?mi)Raz[aã]o Social\\s*:\\s*(.+)$");
    private static final Pattern NAME_AFTER_NOME_RAZAO = Pattern.compile("(?mi)Nome/Raz[aã]o(?: Social)?\\s*:\\s*(.+)$");
    private static final Pattern PRESTADOR_DE_SERVICOS_NAME = Pattern.compile("(?mi)^\\s*Prestador de Servi[cç]os\\s+(.+)$");
    private static final Pattern LEGAL_ENTITY_LINE_NAME = Pattern.compile("(?mi)^\\s*(.+?\\b(?:LTDA\\.?|S\\.A\\.?))\\b.*$");
    private static final Pattern BARUERI_CUSTOMER_NAME = Pattern.compile("(?is)Nome Tomador de Servi[cç]os\\s+CPF/CNPJ\\s+(.+?)\\s+\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}");
    private static final Pattern CNPJ_THEN_NAME = Pattern.compile("(?m)\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\s+(.+?)(?:\\s+\\d{3,}\\s*$|\\s*$)");
    private static final Pattern NAME_THEN_CNPJ = Pattern.compile("(?m)^\\s*(.+?)\\s+\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\s*$");
    private static final Pattern RAZAO_SOCIAL_UPPER = Pattern.compile("(?m)RAZAO SOCIAL:\\s*(.+)$");
    private static final Pattern RAZAO_SOCIAL_MIXED = Pattern.compile("(?m)Raz[aã]o Social\\s*:\\s*(.+)$");
    private static final Pattern NOME_RAZAO_SOCIAL_UPPER = Pattern.compile("(?m)NOME/RAZAO SOCIAL:\\s*(.+)$");
    private static final Pattern MUNICIPAL_SERVICE_VALUE = Pattern.compile("VALOR DOS SERVICOS\\s*R\\$\\s*([0-9.]+,[0-9]{2})");
    private static final Pattern MUNICIPAL_NET_VALUE = Pattern.compile("VALOR LIQUIDO\\s*R\\$\\s*([0-9.]+,[0-9]{2})");
    private static final Pattern TOTAL_SERVICE_VALUE = Pattern.compile("VALOR TOTAL DO SERVICO\\s*=\\s*R\\$\\s*([0-9.]+,[0-9]{2})");
    private static final Pattern SERVICE_VALUE = Pattern.compile("VL\\. DO SERVICO:\\s*R\\$\\s*([0-9.]+,[0-9]{2})");
    private static final Pattern TOTAL_INVOICE_VALUE = Pattern.compile("VALOR TOTAL DA NOTA\\s+(?:R\\$\\s*)?([0-9.]+,[0-9]{2})");
    private static final Pattern TOTAL_NET_VALUE = Pattern.compile("VALOR TOTAL LIQUIDO\\s+R\\$\\s*([0-9.]+,[0-9]{2})");

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
        if (prestador.isBlank()) {
            prestador = ParserSupport.section(text, "Identificacao do Prestador", "Identificacao do Tomador");
        }
        if (prestador.isBlank()) {
            prestador = ParserSupport.section(text, "PREFEITURA MUNICIPAL", "Nota Fiscal de Servico Eletronica");
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
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(text, "Dados do Tomador", "Descricao dos Servicos");
        }
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(text, "Nome Tomador de Servicos", "Qtde");
        }
        if (tomador.isBlank()) {
            tomador = ParserSupport.section(text, "Identificacao do Tomador", "Intermediario");
        }
        List<BigDecimal> money = firstMoneyValues(
                ParserSupport.section(text, "Vl. Total dos Servicos", "Informacoes Adicionais"),
                ParserSupport.section(text, "Valor Total Servicos", "Outras Informacoes"),
                ParserSupport.section(text, "Valor Total do Servico", "INSS"),
                ParserSupport.section(text, "Vl. do Servico", "Imposto Sobre Servico"),
                ParserSupport.section(text, "Detalhamento dos Tributos", "Informacoes Adicionais")
        );
        BigDecimal serviceValue = money.isEmpty() ? null : money.get(0);
        BigDecimal netValue = money.isEmpty() ? null : money.get(money.size() - 1);
        if (serviceValue == null) {
            serviceValue = findMoney(MUNICIPAL_SERVICE_VALUE, normalized);
        }
        if (serviceValue == null) {
            serviceValue = findMoney(TOTAL_SERVICE_VALUE, normalized);
        }
        if (serviceValue == null) {
            serviceValue = findMoney(SERVICE_VALUE, normalized);
        }
        if (serviceValue == null) {
            serviceValue = findMoney(TOTAL_INVOICE_VALUE, normalized);
        }
        if (netValue == null) {
            netValue = findMoney(MUNICIPAL_NET_VALUE, normalized);
        }
        if (netValue == null) {
            netValue = findMoney(TOTAL_NET_VALUE, normalized);
        }
        if (netValue == null && serviceValue != null) {
            netValue = serviceValue;
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
        matcher = NUMBER_AT_END_OF_NEXT_LINE.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String normalized = TextNormalizer.normalize(text);
        matcher = BARUERI_NUMBER.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = NUMBER_BEFORE_GENERATION_DATE.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_NUMERO_DA_NOTA.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_STANDALONE_NUMBER.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_HEADER_NUMBER.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = MUNICIPAL_NUMBER_AFTER_DA.matcher(normalized);
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
        matcher = RAZAO_SOCIAL_MIXED.matcher(prestador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        matcher = PRESTADOR_DE_SERVICOS_NAME.matcher(prestador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        matcher = LEGAL_ENTITY_LINE_NAME.matcher(prestador);
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
        matcher = BARUERI_CUSTOMER_NAME.matcher(tomador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        matcher = CNPJ_THEN_NAME.matcher(tomador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        matcher = NAME_THEN_CNPJ.matcher(tomador);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        return ParserSupport.firstMeaningfulLine(tomador);
    }

    private static String findNameAfterNomeRazao(String text) {
        Matcher matcher = NAME_AFTER_NOME_RAZAO.matcher(text);
        if (matcher.find()) {
            return ParserSupport.cleanName(matcher.group(1));
        }
        return "";
    }

    private static List<BigDecimal> firstMoneyValues(String... sections) {
        for (String section : sections) {
            List<BigDecimal> values = ParserSupport.moneyValues(section);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private static BigDecimal findMoney(Pattern pattern, String normalizedText) {
        Matcher matcher = pattern.matcher(normalizedText);
        if (matcher.find()) {
            return ParserSupport.parseMoney(matcher.group(1));
        }
        return null;
    }
}
