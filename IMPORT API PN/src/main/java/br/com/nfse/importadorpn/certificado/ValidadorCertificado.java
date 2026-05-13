package br.com.nfse.importadorpn.certificado;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class ValidadorCertificado {
    private final ExtratorCnpjCertificado extratorCnpj = new ExtratorCnpjCertificado();

    public ResultadoCertificado validar(Path arquivo, char[] senha) {
        if (!Files.isRegularFile(arquivo)) {
            return ResultadoCertificado.invalido("Arquivo de certificado nao encontrado: " + arquivo);
        }
        try (InputStream input = Files.newInputStream(arquivo)) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(input, senha);
            int certificados = 0;
            Optional<Instant> menorValidade = Optional.empty();
            Set<String> cnpjs = new LinkedHashSet<>();
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                Certificate certificate = keyStore.getCertificate(aliases.nextElement());
                if (certificate instanceof X509Certificate x509) {
                    certificados++;
                    x509.checkValidity();
                    cnpjs.addAll(extratorCnpj.extrair(x509));
                    Instant validade = x509.getNotAfter().toInstant();
                    if (menorValidade.isEmpty() || validade.isBefore(menorValidade.get())) {
                        menorValidade = Optional.of(validade);
                    }
                }
            }
            return ResultadoCertificado.valido("PKCS12 carregado com sucesso", certificados, menorValidade, cnpjs);
        } catch (Exception exception) {
            return ResultadoCertificado.invalido("Certificado nao foi possivel abrir ou validar; confira arquivo e senha");
        }
    }
}
