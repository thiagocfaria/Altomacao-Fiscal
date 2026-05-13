package br.com.nfse.importadorpn.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidadorCertificadoTest {
    @TempDir
    Path tempDir;

    @Test
    void validaArquivoPkcs12ComSenhaCorreta() throws Exception {
        Path arquivo = pkcs12Vazio("123456");

        ResultadoCertificado resultado = new ValidadorCertificado().validar(arquivo, "123456".toCharArray());

        assertThat(resultado.valido()).isTrue();
        assertThat(resultado.mensagem()).contains("PKCS12 carregado");
    }

    @Test
    void rejeitaSenhaErradaSemMostrarSenhaNaMensagem() throws Exception {
        Path arquivo = pkcs12Vazio("123456");

        ResultadoCertificado resultado = new ValidadorCertificado().validar(arquivo, "senha-errada".toCharArray());

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.mensagem()).contains("nao foi possivel abrir");
        assertThat(resultado.mensagem()).doesNotContain("senha-errada");
    }

    @Test
    void rejeitaArquivoInexistente() {
        ResultadoCertificado resultado = new ValidadorCertificado()
                .validar(tempDir.resolve("cliente.pfx"), "123456".toCharArray());

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.mensagem()).contains("nao encontrado");
    }

    @Test
    void extraiCnpjDeCertificadoPkcs12Real() throws Exception {
        Path arquivo = pkcs12ComCnpj("11222333000181", "123456");

        ResultadoCertificado resultado = new ValidadorCertificado().validar(arquivo, "123456".toCharArray());

        assertThat(resultado.valido()).isTrue();
        assertThat(resultado.cnpjs()).contains("11222333000181");
    }

    @Test
    void extraiCnpjDoCnDoSubject() throws Exception {
        Path arquivo = pkcs12ComCnpjNoCommonName("25014360000173", "123456");

        ResultadoCertificado resultado = new ValidadorCertificado().validar(arquivo, "123456".toCharArray());

        assertThat(resultado.valido()).isTrue();
        assertThat(resultado.cnpjs()).contains("25014360000173");
    }

    private Path pkcs12Vazio(String senha) throws Exception {
        Path arquivo = tempDir.resolve("cliente.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, senha.toCharArray());
        try (OutputStream output = Files.newOutputStream(arquivo)) {
            keyStore.store(output, senha.toCharArray());
        }
        return arquivo;
    }

    private Path pkcs12ComCnpj(String cnpj, String senha) throws Exception {
        if (!comandoExiste("openssl")) {
            org.junit.jupiter.api.Assumptions.abort("openssl indisponivel para gerar certificado de teste");
        }
        Path config = tempDir.resolve("openssl.cnf");
        Path key = tempDir.resolve("key.pem");
        Path cert = tempDir.resolve("cert.pem");
        Path pkcs12 = tempDir.resolve("cliente-com-cnpj.p12");
        Files.writeString(config, """
                [req]
                distinguished_name=dn
                x509_extensions=v3_req
                prompt=no
                [dn]
                CN=Teste
                [v3_req]
                subjectAltName=@alt_names
                [alt_names]
                otherName.1=2.16.76.1.3.3;UTF8:%s
                """.formatted(cnpj));
        executar(List.of("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                "-keyout", key.toString(), "-out", cert.toString(), "-days", "30",
                "-config", config.toString()));
        executar(List.of("openssl", "pkcs12", "-export", "-out", pkcs12.toString(),
                "-inkey", key.toString(), "-in", cert.toString(), "-passout", "pass:" + senha));
        return pkcs12;
    }

    private Path pkcs12ComCnpjNoCommonName(String cnpj, String senha) throws Exception {
        if (!comandoExiste("openssl")) {
            org.junit.jupiter.api.Assumptions.abort("openssl indisponivel para gerar certificado de teste");
        }
        Path config = tempDir.resolve("openssl-cn.cnf");
        Path key = tempDir.resolve("key-cn.pem");
        Path cert = tempDir.resolve("cert-cn.pem");
        Path pkcs12 = tempDir.resolve("cliente-com-cnpj-cn.p12");
        Files.writeString(config, """
                [req]
                distinguished_name=dn
                prompt=no
                [dn]
                CN=Empresa Teste:%s
                """.formatted(cnpj));
        executar(List.of("openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
                "-keyout", key.toString(), "-out", cert.toString(), "-days", "30",
                "-config", config.toString()));
        executar(List.of("openssl", "pkcs12", "-export", "-out", pkcs12.toString(),
                "-inkey", key.toString(), "-in", cert.toString(), "-passout", "pass:" + senha));
        return pkcs12;
    }

    private static boolean comandoExiste(String comando) {
        try {
            return new ProcessBuilder(comando, "version").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void executar(List<String> comando) throws Exception {
        Process processo = new ProcessBuilder(comando).redirectErrorStream(true).start();
        String saida = new String(processo.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int exitCode = processo.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("Comando falhou (" + exitCode + "): " + String.join(" ", comando)
                    + "\n" + saida);
        }
    }
}
