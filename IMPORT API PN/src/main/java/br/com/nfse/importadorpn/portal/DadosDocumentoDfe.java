package br.com.nfse.importadorpn.portal;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public record DadosDocumentoDfe(
        String cnpjPrestador,
        String cnpjTomador,
        String cnpjIntermediario,
        Optional<YearMonth> mesEmissao) {
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    public DadosDocumentoDfe {
        cnpjPrestador = digits(cnpjPrestador);
        cnpjTomador = digits(cnpjTomador);
        cnpjIntermediario = digits(cnpjIntermediario);
        mesEmissao = mesEmissao == null ? Optional.empty() : mesEmissao;
    }

    public boolean pertenceAoCnpj(String cnpj) {
        String normalizado = digits(cnpj);
        return !normalizado.isBlank()
                && (normalizado.equals(cnpjPrestador)
                || normalizado.equals(cnpjTomador)
                || normalizado.equals(cnpjIntermediario));
    }

    public static DadosDocumentoDfe fromXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Element root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml))
                    .getDocumentElement();
            return new DadosDocumentoDfe(
                    firstCnpjInsideAny(root, List.of("prest", "prestador", "prestadorServico", "emit", "emitente")),
                    firstCnpjInsideAny(root, List.of("toma", "tomador", "tomadorServico", "tomadorServicos")),
                    firstCnpjInsideAny(root, List.of("interm", "intermediario", "intermediarioServico")),
                    firstEmissionMonth(root));
        } catch (Exception ignored) {
            return new DadosDocumentoDfe("", "", "", Optional.empty());
        }
    }

    private static String firstCnpjInsideAny(Element root, List<String> parentLocalNames) {
        for (String parentLocalName : parentLocalNames) {
            String cnpj = firstCnpjInside(root, parentLocalName);
            if (!cnpj.isBlank()) {
                return cnpj;
            }
        }
        return "";
    }

    private static String firstCnpjInside(Element root, String parentLocalName) {
        NodeList parents = root.getElementsByTagNameNS("*", parentLocalName);
        if (parents.getLength() == 0) {
            parents = root.getElementsByTagName(parentLocalName);
        }
        for (int i = 0; i < parents.getLength(); i++) {
            Node parent = parents.item(i);
            if (parent instanceof Element parentElement) {
                String cnpj = firstText(parentElement, "CNPJ");
                if (!cnpj.isBlank()) {
                    return digits(cnpj);
                }
            }
        }
        return "";
    }

    private static Optional<YearMonth> firstEmissionMonth(Element root) {
        for (String tag : java.util.List.of("dhEmi", "dEmi", "dCompet")) {
            String text = firstText(root, tag);
            Optional<YearMonth> month = parseMonth(text);
            if (month.isPresent()) {
                return month;
            }
        }
        return Optional.empty();
    }

    private static String firstText(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = root.getElementsByTagName(localName);
        }
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private static Optional<YearMonth> parseMonth(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(YearMonth.from(OffsetDateTime.parse(value)));
        } catch (Exception ignored) {
        }
        if (value.length() >= 7) {
            try {
                return Optional.of(YearMonth.parse(value.substring(0, 7)));
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    private static String digits(String value) {
        return NON_DIGITS.matcher(value == null ? "" : value).replaceAll("");
    }
}
