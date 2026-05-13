package br.com.nfse.importadorpn.configuracao;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record CadastroImportacao(List<EmpresaImportacao> empresas, Optional<Path> entradaRest) {
    public CadastroImportacao {
        empresas = List.copyOf(empresas);
    }
}
