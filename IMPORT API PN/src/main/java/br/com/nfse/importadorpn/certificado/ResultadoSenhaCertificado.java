package br.com.nfse.importadorpn.certificado;

import java.util.Optional;

public record ResultadoSenhaCertificado(boolean encontrada, Optional<String> senha, String origem) {
    public static ResultadoSenhaCertificado encontrada(String senha, String origem) {
        return new ResultadoSenhaCertificado(true, Optional.of(senha), origem);
    }

    public static ResultadoSenhaCertificado ausente(String origem) {
        return new ResultadoSenhaCertificado(false, Optional.empty(), origem);
    }

    @Override
    public String toString() {
        return "ResultadoSenhaCertificado[encontrada=" + encontrada + ", origem=" + origem + "]";
    }
}
