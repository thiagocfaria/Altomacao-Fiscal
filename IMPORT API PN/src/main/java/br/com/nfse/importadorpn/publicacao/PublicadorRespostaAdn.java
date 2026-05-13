package br.com.nfse.importadorpn.publicacao;

import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;

public final class PublicadorRespostaAdn {
    private final Path backend;

    public PublicadorRespostaAdn(Path backend) {
        this.backend = backend;
    }

    public RespostaAdnPublicada publicar(String cnpj, String nsu, YearMonth mes,
            ResultadoConsultaAdn resultado) throws IOException {
        if (resultado.statusCode() != 200) {
            throw new IllegalArgumentException("Somente resposta HTTP 200 pode ser publicada");
        }
        if (resultado.bytes() == 0) {
            throw new IllegalArgumentException("Resposta ADN vazia nao pode ser publicada");
        }
        Path pasta = backend.resolve("respostas-adn").resolve(mes.toString()).resolve(cnpj);
        Files.createDirectories(pasta);
        Path destino = pasta.resolve("nsu-" + normalizarNomeArquivo(nsu) + extensao(resultado.contentType()));
        Files.write(destino, resultado.corpo(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new RespostaAdnPublicada(destino, resultado.bytes());
    }

    private static String extensao(String contentType) {
        String normalizado = contentType == null ? "" : contentType.toLowerCase();
        if (normalizado.contains("xml")) {
            return ".xml";
        }
        if (normalizado.contains("pdf")) {
            return ".pdf";
        }
        if (normalizado.contains("zip")) {
            return ".zip";
        }
        if (normalizado.contains("json")) {
            return ".json";
        }
        return ".bin";
    }

    private static String normalizarNomeArquivo(String valor) {
        String normalizado = valor == null ? "" : valor.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalizado.isBlank() ? "sem-nsu" : normalizado;
    }
}
