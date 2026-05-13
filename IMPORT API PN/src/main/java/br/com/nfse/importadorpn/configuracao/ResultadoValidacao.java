package br.com.nfse.importadorpn.configuracao;

import java.util.List;

public record ResultadoValidacao(int totalEmpresas, List<ErroValidacao> erros) {
    public ResultadoValidacao {
        erros = List.copyOf(erros);
    }

    public boolean aprovado() {
        return erros.isEmpty() && totalEmpresas > 0;
    }
}
