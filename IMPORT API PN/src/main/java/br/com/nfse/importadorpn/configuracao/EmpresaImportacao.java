package br.com.nfse.importadorpn.configuracao;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

public record EmpresaImportacao(
        String nome,
        String cnpj,
        Optional<Path> caminhoRest,
        Optional<Path> caminhoDms,
        Optional<Path> certificadoPasta,
        Optional<String> certificadoArquivo,
        Optional<String> certificadoAlias,
        Optional<String> senhaCertificadoPlanilha,
        Optional<LocalDate> validadeCertificado,
        String aba,
        int linha
) {
}
