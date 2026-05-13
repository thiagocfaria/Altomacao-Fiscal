package br.com.nfse.importadorpn.portal;

import java.util.Arrays;

public record ResultadoConsultaAdn(int statusCode, String contentType, byte[] corpo) {
    public ResultadoConsultaAdn {
        corpo = corpo == null ? new byte[0] : Arrays.copyOf(corpo, corpo.length);
    }

    public int bytes() {
        return corpo.length;
    }

    public byte[] corpo() {
        return Arrays.copyOf(corpo, corpo.length);
    }

    public String resumo() {
        return "HTTP " + statusCode + ", content-type=" + contentType + ", bytes=" + bytes();
    }
}
