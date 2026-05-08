package br.com.nfse.renomeador.parser;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.text.TextNormalizer;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class PortalNacionalXmlParser {
    private static final DateTimeFormatter BRAZILIAN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> NUMBER_TAGS = List.of("nnfse", "numero", "numeronfse", "numeronfs-e");
    private static final List<String> ISSUE_DATE_TAGS = List.of("dhemi", "demi", "dataemissao", "dataemissaonfse");
    private static final List<String> PROVIDER_TAGS = List.of("prest", "prestador", "prestadorservico", "emit", "emitente");
    private static final List<String> CUSTOMER_TAGS = List.of("toma", "tomador", "tomadorservico", "tomadorservicos");
    private static final List<String> TAX_ID_TAGS = List.of("cnpj", "cpf", "nif");
    private static final List<String> NAME_TAGS = List.of("xnome", "nome", "razaosocial", "xrazaosocial");
    private static final List<String> SERVICE_VALUE_TAGS = List.of("vserv", "valorservico", "valorservicos", "valortotalservicos");
    private static final List<String> NET_VALUE_TAGS = List.of("vliq", "vliquido", "valorliquido", "valorliquidonfse", "vliqnfse");
    private static final Set<String> RETENTION_VALUE_TAGS = Set.of(
            "vretpis", "vretcofins", "vretinss", "vretir", "vretcsll", "vretissqn", "vissret", "valorissretido"
    );
    private static final Set<String> RETENTION_FLAG_TAGS = Set.of("issretido", "retido", "indissretido");

    public InvoiceData parse(Path xml) throws IOException {
        Document document = parseDocument(xml);
        Element root = document.getDocumentElement();
        BigDecimal serviceValue = findMoney(root, SERVICE_VALUE_TAGS).orElse(null);
        BigDecimal netValue = findMoney(root, NET_VALUE_TAGS).orElse(serviceValue);
        boolean retained = isRetained(root, serviceValue, netValue);

        return new InvoiceData(
                LayoutType.PORTAL_NACIONAL,
                findText(root, NUMBER_TAGS).orElse(""),
                findText(root, ISSUE_DATE_TAGS).map(PortalNacionalXmlParser::normalizeDate).orElse(""),
                partyName(root, PROVIDER_TAGS),
                partyTaxId(root, PROVIDER_TAGS),
                partyName(root, CUSTOMER_TAGS),
                partyTaxId(root, CUSTOMER_TAGS),
                serviceValue,
                netValue,
                retained,
                false,
                isCancelled(root)
        );
    }

    private static Document parseDocument(Path xml) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        try (var input = Files.newInputStream(xml)) {
            return factory.newDocumentBuilder().parse(input);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IOException("XML NFS-e invalido ou nao suportado: " + xml.getFileName(), exception);
        }
    }

    private static void setFeature(DocumentBuilderFactory factory, String feature, boolean value) throws IOException {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException exception) {
            throw new IOException("Parser XML sem suporte ao recurso de seguranca: " + feature, exception);
        }
    }

    private static String partyTaxId(Element root, List<String> partyTags) {
        return firstElement(root, partyTags)
                .flatMap(element -> findText(element, TAX_ID_TAGS))
                .orElse("");
    }

    private static String partyName(Element root, List<String> partyTags) {
        return firstElement(root, partyTags)
                .flatMap(element -> findText(element, NAME_TAGS))
                .map(ParserSupport::cleanName)
                .orElse("");
    }

    private static Optional<BigDecimal> findMoney(Element root, List<String> tags) {
        return findText(root, tags).flatMap(PortalNacionalXmlParser::parseDecimal);
    }

    private static boolean isRetained(Element root, BigDecimal serviceValue, BigDecimal netValue) {
        boolean explicitPositive = elements(root).stream()
                .filter(element -> RETENTION_VALUE_TAGS.contains(localName(element)))
                .map(Element::getTextContent)
                .map(PortalNacionalXmlParser::parseDecimal)
                .flatMap(Optional::stream)
                .anyMatch(value -> value.compareTo(BigDecimal.ZERO) > 0);
        boolean explicitFlag = elements(root).stream()
                .filter(element -> RETENTION_FLAG_TAGS.contains(localName(element)))
                .map(Element::getTextContent)
                .map(TextNormalizer::normalize)
                .anyMatch(value -> value.equals("1") || value.equals("S") || value.equals("SIM") || value.equals("TRUE"));
        boolean netLowerThanService = serviceValue != null && netValue != null && netValue.compareTo(serviceValue) < 0;
        return explicitPositive || explicitFlag || netLowerThanService;
    }

    private static boolean isCancelled(Element root) {
        String normalizedText = TextNormalizer.normalize(root.getTextContent());
        if (normalizedText.contains("CANCELAD")) {
            return true;
        }
        return elements(root).stream()
                .map(PortalNacionalXmlParser::localName)
                .anyMatch(name -> name.contains("cancel"));
    }

    private static Optional<Element> firstElement(Element root, List<String> tags) {
        return elements(root).stream()
                .filter(element -> tags.contains(localName(element)))
                .findFirst();
    }

    private static Optional<String> findText(Element root, List<String> tags) {
        return elements(root).stream()
                .filter(element -> tags.contains(localName(element)))
                .map(Element::getTextContent)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private static List<Element> elements(Element root) {
        java.util.ArrayList<Element> result = new java.util.ArrayList<>();
        collectElements(root, result);
        return List.copyOf(result);
    }

    private static void collectElements(Node node, List<Element> result) {
        if (node instanceof Element element) {
            result.add(element);
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectElements(child, result);
        }
    }

    private static String localName(Element element) {
        String local = element.getLocalName();
        if (local == null || local.isBlank()) {
            local = element.getNodeName();
        }
        int colon = local.indexOf(':');
        if (colon >= 0) {
            local = local.substring(colon + 1);
        }
        return local.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String normalizeDate(String value) {
        String stripped = value.strip();
        if (stripped.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return stripped;
        }
        try {
            return OffsetDateTime.parse(stripped).toLocalDate().format(BRAZILIAN_DATE);
        } catch (java.time.DateTimeException ignored) {
        }
        String dateOnly = stripped.length() >= 10 ? stripped.substring(0, 10) : stripped;
        try {
            return LocalDate.parse(dateOnly).format(BRAZILIAN_DATE);
        } catch (java.time.DateTimeException ignored) {
            return stripped;
        }
    }

    private static Optional<BigDecimal> parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String cleaned = value.strip().replaceAll("[^0-9,.-]", "");
        if (cleaned.isBlank() || cleaned.equals("-")) {
            return Optional.empty();
        }
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        try {
            return Optional.of(new BigDecimal(cleaned));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
