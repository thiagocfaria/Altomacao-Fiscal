package br.com.nfse.importadorpn.portal;

import java.util.Arrays;

public record DocumentoDfeExtraido(String nsu, String chaveAcesso, String tipoDocumento, byte[] xml) {
    public DocumentoDfeExtraido {
        nsu = nsu == null ? "" : nsu;
        chaveAcesso = chaveAcesso == null ? "" : chaveAcesso;
        tipoDocumento = tipoDocumento == null ? "" : tipoDocumento;
        xml = xml == null ? new byte[0] : Arrays.copyOf(xml, xml.length);
    }

    public byte[] xml() {
        return Arrays.copyOf(xml, xml.length);
    }
}
