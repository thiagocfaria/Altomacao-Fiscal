package br.com.nfse.importadorpn.portal;

import java.util.Set;

public record EstadoDestinoNotas(
        Set<String> chavesXmlRest,
        Set<String> chavesPdfRest,
        Set<String> chavesDms,
        boolean restMonitorado,
        boolean dmsMonitorado) {

    public EstadoDestinoNotas {
        chavesXmlRest = Set.copyOf(chavesXmlRest);
        chavesPdfRest = Set.copyOf(chavesPdfRest);
        chavesDms = Set.copyOf(chavesDms);
    }

    public static EstadoDestinoNotas completoPara(Set<String> chaves) {
        return new EstadoDestinoNotas(chaves, chaves, chaves, true, true);
    }

    public static EstadoDestinoNotas semDestinoMonitorado() {
        return new EstadoDestinoNotas(Set.of(), Set.of(), Set.of(), false, false);
    }

    public boolean xmlRestPresente(String chave) {
        return !restMonitorado || chavesXmlRest.contains(chave);
    }

    public boolean pdfRestPresente(String chave) {
        return !restMonitorado || chavesPdfRest.contains(chave);
    }

    public boolean dmsPresente(String chave) {
        return !dmsMonitorado || chavesDms.contains(chave);
    }

    public boolean completo(String chave) {
        return xmlRestPresente(chave) && pdfRestPresente(chave) && dmsPresente(chave);
    }

    public Set<String> chavesCompletas() {
        java.util.HashSet<String> resultado = new java.util.HashSet<>(chavesXmlRest);
        if (restMonitorado) {
            resultado.retainAll(chavesPdfRest);
        }
        if (dmsMonitorado) {
            resultado.retainAll(chavesDms);
        }
        return Set.copyOf(resultado);
    }
}
