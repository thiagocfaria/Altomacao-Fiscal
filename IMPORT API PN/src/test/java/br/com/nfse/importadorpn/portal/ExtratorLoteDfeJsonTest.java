package br.com.nfse.importadorpn.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class ExtratorLoteDfeJsonTest {
    @Test
    void extraiXmlCompactadoDoLoteDfe() throws Exception {
        String xml = "<NFSe><InfNFSe Id=\"1\"/></NFSe>";
        String json = """
                {
                  "StatusProcessamento": "PROCESSADO_COM_SUCESSO",
                  "LoteDFe": [
                    {
                      "NSU": 123,
                      "ChaveAcesso": "ABC123",
                      "TipoDocumento": "NFSE",
                      "ArquivoXml": "%s",
                      "DataHoraGeracao": "2026-05-09T12:00:00Z"
                    }
                  ]
                }
                """.formatted(base64Gzip(xml));

        List<DocumentoDfeExtraido> documentos = new ExtratorLoteDfeJson().extrair(
                new ResultadoConsultaAdn(200, "application/json; charset=utf-8",
                        json.getBytes(StandardCharsets.UTF_8)));

        assertThat(documentos).hasSize(1);
        assertThat(documentos.get(0).nsu()).isEqualTo("123");
        assertThat(documentos.get(0).chaveAcesso()).isEqualTo("ABC123");
        assertThat(new String(documentos.get(0).xml(), StandardCharsets.UTF_8)).isEqualTo(xml);
    }

    private static String base64Gzip(String texto) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
