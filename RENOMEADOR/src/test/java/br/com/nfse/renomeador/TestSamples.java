package br.com.nfse.renomeador;

import java.net.URISyntaxException;
import java.nio.file.Files;
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

    public static Path unsupportedPdf(String fileName) {
        Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path root = base.getFileName() != null && base.getFileName().toString().equals("RENOMEADOR")
                ? base.getParent()
                : base;
        Path pdf = root.resolve("PDF NAO SUPORTADO RENOMEADOR").resolve(fileName);
        if (!Files.isRegularFile(pdf)) {
            throw new IllegalArgumentException("PDF nao suportado de teste nao encontrado: " + fileName);
        }
        return pdf;
    }
}
