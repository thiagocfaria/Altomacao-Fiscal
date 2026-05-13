package br.com.nfse.importadorpn.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContextoSslCertificadoTest {
    @TempDir
    Path tempDir;

    @Test
    void criaContextoSslAPartirDePkcs12Valido() throws Exception {
        Path arquivo = tempDir.resolve("cliente.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, "123456".toCharArray());
        try (OutputStream output = Files.newOutputStream(arquivo)) {
            keyStore.store(output, "123456".toCharArray());
        }

        SSLContext contexto = new ContextoSslCertificado().criar(arquivo, "123456".toCharArray());

        assertThat(contexto.getProtocol()).isEqualTo("TLS");
    }
}
