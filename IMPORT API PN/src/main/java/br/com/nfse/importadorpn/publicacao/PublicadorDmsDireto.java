package br.com.nfse.importadorpn.publicacao;

import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.Arrays;

public final class PublicadorDmsDireto {
    public DmsDiretoPublicado publicar(Path caminhoDms, String cnpj, String nsu, YearMonth mes,
            ResultadoConsultaAdn resultado) throws IOException {
        if (resultado.statusCode() != 200) {
            throw new IllegalArgumentException("Somente resposta HTTP 200 pode ser publicada no DMS");
        }
        if (resultado.bytes() == 0) {
            throw new IllegalArgumentException("XML vazio nao pode ser publicado no DMS");
        }
        if (!contentTypeXml(resultado.contentType())) {
            throw new IllegalArgumentException("Somente XML pode ser publicado no DMS: " + resultado.contentType());
        }
        Files.createDirectories(caminhoDms);
        Path destino = caminhoDms.resolve(nomeArquivo(cnpj, nsu, mes));
        byte[] corpo = resultado.corpo();
        if (Files.exists(destino)) {
            byte[] existente = Files.readAllBytes(destino);
            if (!Arrays.equals(existente, corpo)) {
                throw new IllegalStateException("Arquivo DMS ja existe com conteudo diferente: " + destino);
            }
            return new DmsDiretoPublicado(destino, corpo.length, true);
        }
        Files.write(destino, corpo, StandardOpenOption.CREATE_NEW);
        return new DmsDiretoPublicado(destino, corpo.length, false);
    }

    private static boolean contentTypeXml(String contentType) {
        return contentType != null && contentType.toLowerCase().contains("xml");
    }

    private static String nomeArquivo(String cnpj, String nsu, YearMonth mes) {
        return "PN_" + normalizar(cnpj) + "_NSU_" + normalizar(nsu)
                + "_" + mes.toString().replace("-", "") + ".xml";
    }

    private static String normalizar(String valor) {
        String normalizado = valor == null ? "" : valor.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalizado.isBlank() ? "sem-valor" : normalizado;
    }
}
