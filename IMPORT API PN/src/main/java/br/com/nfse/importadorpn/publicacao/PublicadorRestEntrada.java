package br.com.nfse.importadorpn.publicacao;

import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.util.Arrays;

public final class PublicadorRestEntrada {
    private final Path entradaRest;

    public PublicadorRestEntrada(Path entradaRest) {
        this.entradaRest = entradaRest;
    }

    public RestEntradaPublicada publicar(String cnpj, String nsu, YearMonth mes,
            ResultadoConsultaAdn resultado) throws IOException {
        if (resultado.statusCode() != 200) {
            throw new IllegalArgumentException("Somente resposta HTTP 200 pode ser publicada na entrada REST");
        }
        if (resultado.bytes() == 0) {
            throw new IllegalArgumentException("Resposta ADN vazia nao pode ser publicada na entrada REST");
        }
        String extensao = extensaoSuportada(resultado.contentType());
        Files.createDirectories(entradaRest);
        Path destino = entradaRest.resolve(nomeArquivo(cnpj, nsu, mes, extensao));
        byte[] corpo = resultado.corpo();
        if (Files.exists(destino)) {
            byte[] existente = Files.readAllBytes(destino);
            if (!Arrays.equals(existente, corpo)) {
                throw new IllegalStateException("Arquivo REST ja existe com conteudo diferente: " + destino);
            }
            return new RestEntradaPublicada(destino, corpo.length, true);
        }
        Files.write(destino, corpo, StandardOpenOption.CREATE_NEW);
        return new RestEntradaPublicada(destino, corpo.length, false);
    }

    public Path caminhoEsperado(String cnpj, String nsu, YearMonth mes, String contentType) {
        return entradaRest.resolve(nomeArquivo(cnpj, nsu, mes, extensaoSuportada(contentType)));
    }

    private static String nomeArquivo(String cnpj, String nsu, YearMonth mes, String extensao) {
        return "PN_" + normalizar(cnpj) + "_NSU_" + normalizar(nsu)
                + "_" + mes.toString().replace("-", "") + extensao;
    }

    private static String extensaoSuportada(String contentType) {
        String normalizado = contentType == null ? "" : contentType.toLowerCase();
        if (normalizado.contains("xml")) {
            return ".xml";
        }
        if (normalizado.contains("pdf")) {
            return ".pdf";
        }
        throw new IllegalArgumentException("Content-type nao suportado para entrada REST: " + contentType);
    }

    private static String normalizar(String valor) {
        String normalizado = valor == null ? "" : valor.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalizado.isBlank() ? "sem-valor" : normalizado;
    }
}
