package br.com.nfse.importadorpn.portal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

public final class ExtratorLoteDfeJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public List<DocumentoDfeExtraido> extrair(ResultadoConsultaAdn resultado) throws IOException {
        if (resultado.statusCode() != 200 || !contem(resultado.contentType(), "json") || resultado.bytes() == 0) {
            return List.of();
        }
        JsonNode raiz = MAPPER.readTree(resultado.corpo());
        JsonNode lote = raiz.path("LoteDFe");
        if (!lote.isArray()) {
            return List.of();
        }
        List<DocumentoDfeExtraido> documentos = new ArrayList<>();
        for (JsonNode item : lote) {
            String arquivoXml = texto(item, "ArquivoXml");
            if (arquivoXml.isBlank()) {
                continue;
            }
            byte[] xml = decodificarXml(arquivoXml);
            if (xml.length == 0 || !pareceXml(xml)) {
                continue;
            }
            documentos.add(new DocumentoDfeExtraido(
                    texto(item, "NSU"),
                    texto(item, "ChaveAcesso"),
                    texto(item, "TipoDocumento"),
                    xml));
        }
        return List.copyOf(documentos);
    }

    private static byte[] decodificarXml(String valor) throws IOException {
        byte[] bytes = Base64.getMimeDecoder().decode(valor);
        if (ehGzip(bytes)) {
            return descompactarGzip(bytes);
        }
        return bytes;
    }

    private static byte[] descompactarGzip(byte[] bytes) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes));
                ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            gzip.transferTo(saida);
            return saida.toByteArray();
        }
    }

    private static boolean ehGzip(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    private static boolean pareceXml(byte[] bytes) {
        String inicio = new String(bytes, 0, Math.min(bytes.length, 64), StandardCharsets.UTF_8)
                .replace("\uFEFF", "")
                .trim();
        return inicio.startsWith("<");
    }

    private static String texto(JsonNode item, String campo) {
        JsonNode valor = item.path(campo);
        return valor.isMissingNode() || valor.isNull() ? "" : valor.asText("");
    }

    private static boolean contem(String texto, String trecho) {
        return texto != null && texto.toLowerCase().contains(trecho);
    }
}
