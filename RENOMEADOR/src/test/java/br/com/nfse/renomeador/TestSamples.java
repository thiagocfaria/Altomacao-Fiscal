package br.com.nfse.renomeador;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

public final class TestSamples {
    private TestSamples() {
    }

    public static Path samplePdf(String fileName) {
        URL resource = TestSamples.class.getClassLoader().getResource("nfse-modelos/" + fileName);
        if (resource == null) {
            throw new IllegalArgumentException("PDF de teste nao encontrado: " + fileName);
        }
        try {
            return Path.of(resource.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Caminho invalido para PDF de teste: " + fileName, exception);
        }
    }
}
