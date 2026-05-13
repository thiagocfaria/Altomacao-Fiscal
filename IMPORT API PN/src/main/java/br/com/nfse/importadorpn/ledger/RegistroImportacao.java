package br.com.nfse.importadorpn.ledger;

import java.time.Instant;

public record RegistroImportacao(
        String cnpj,
        String nsu,
        String chave,
        String empresa,
        EstadoDocumento estadoXml,
        EstadoDocumento estadoPdf,
        EstadoDocumento estadoDms,
        int tentativas,
        String ultimoErro,
        String caminhoRestFinal,
        String caminhoDmsFinal,
        Instant atualizadoEm
) {
    public static RegistroImportacao novo(String cnpj, String nsu, String chave, String empresa, Instant atualizadoEm) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, EstadoDocumento.PENDENTE, EstadoDocumento.PENDENTE,
                EstadoDocumento.PENDENTE, 0, "", "", "", atualizadoEm);
    }

    public boolean concluida() {
        return estadoXml == EstadoDocumento.CONCLUIDO
                && estadoPdf == EstadoDocumento.CONCLUIDO
                && estadoDms == EstadoDocumento.CONCLUIDO;
    }

    public RegistroImportacao comEstadoXml(EstadoDocumento estado) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estado, estadoPdf, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comEstadoPdf(EstadoDocumento estado) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estado, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comEstadoDms(EstadoDocumento estado) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estadoPdf, estado, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comTentativas(int tentativas) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estadoPdf, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comUltimoErro(String ultimoErro) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estadoPdf, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comCaminhoRestFinal(String caminhoRestFinal) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estadoPdf, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comCaminhoDmsFinal(String caminhoDmsFinal) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estadoPdf, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }

    public RegistroImportacao comAtualizadoEm(Instant atualizadoEm) {
        return new RegistroImportacao(cnpj, nsu, chave, empresa, estadoXml, estadoPdf, estadoDms, tentativas,
                ultimoErro, caminhoRestFinal, caminhoDmsFinal, atualizadoEm);
    }
}
