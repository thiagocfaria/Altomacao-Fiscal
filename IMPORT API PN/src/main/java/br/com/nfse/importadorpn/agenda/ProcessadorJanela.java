package br.com.nfse.importadorpn.agenda;

@FunctionalInterface
public interface ProcessadorJanela {
    void processar(JanelaExecucao janela) throws Exception;
}
