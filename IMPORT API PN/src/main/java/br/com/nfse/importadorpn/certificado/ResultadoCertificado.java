package br.com.nfse.importadorpn.certificado;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public record ResultadoCertificado(
        boolean valido,
        String mensagem,
        int certificados,
        Optional<Instant> venceEm,
        Set<String> cnpjs) {
    public ResultadoCertificado {
        cnpjs = Set.copyOf(cnpjs);
    }

    public static ResultadoCertificado valido(String mensagem, int certificados, Optional<Instant> venceEm) {
        return valido(mensagem, certificados, venceEm, Set.of());
    }

    public static ResultadoCertificado valido(String mensagem, int certificados, Optional<Instant> venceEm,
                                              Set<String> cnpjs) {
        return new ResultadoCertificado(true, mensagem, certificados, venceEm, cnpjs);
    }

    public static ResultadoCertificado invalido(String mensagem) {
        return new ResultadoCertificado(false, mensagem, 0, Optional.empty(), Set.of());
    }
}
