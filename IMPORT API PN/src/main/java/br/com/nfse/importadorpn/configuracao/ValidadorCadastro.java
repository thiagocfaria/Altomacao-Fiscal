package br.com.nfse.importadorpn.configuracao;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ValidadorCadastro {
    public ResultadoValidacao validar(CadastroImportacao cadastro) {
        List<ErroValidacao> erros = new ArrayList<>();
        if (cadastro.entradaRest().isEmpty()) {
            erros.add(new ErroValidacao("cadastro", "Linha tecnica IMPORT API PN ENTRADA REST/entrada-rest nao encontrada"));
        } else if (!Files.isDirectory(cadastro.entradaRest().get())) {
            erros.add(new ErroValidacao("entrada-rest", "Pasta entrada-rest nao existe: " + cadastro.entradaRest().get()));
        }
        Map<String, Path> dmsPorCnpj = new HashMap<>();
        for (EmpresaImportacao empresa : cadastro.empresas()) {
            validarEmpresa(empresa, erros, dmsPorCnpj);
        }
        if (cadastro.empresas().isEmpty()) {
            erros.add(new ErroValidacao("cadastro", "Nenhuma empresa ativa do IMPORT API PN foi encontrada"));
        }
        return new ResultadoValidacao(cadastro.empresas().size(), erros);
    }

    private static void validarEmpresa(EmpresaImportacao empresa, List<ErroValidacao> erros,
                                       Map<String, Path> dmsPorCnpj) {
        String origem = empresa.aba() + ":" + empresa.linha() + " " + empresa.nome();
        if (empresa.cnpj().length() != 14) {
            erros.add(new ErroValidacao(origem, "CNPJ deve ter 14 digitos: " + empresa.cnpj()));
        }
        validarDiretorio(empresa.caminhoRest(), origem, "CAMINHO REST", erros);
        validarDiretorio(empresa.caminhoDms(), origem, "CAMINHO DMS", erros);
        empresa.caminhoDms().ifPresent(path -> {
            Path previous = dmsPorCnpj.putIfAbsent(empresa.cnpj(), path.normalize());
            if (previous != null && !previous.equals(path.normalize())) {
                erros.add(new ErroValidacao(origem, "CNPJ com CAMINHO DMS conflitante: " + empresa.cnpj()));
            }
        });
        validarCertificado(empresa, origem, erros);
    }

    private static void validarDiretorio(Optional<Path> path, String origem, String campo, List<ErroValidacao> erros) {
        if (path.isEmpty()) {
            erros.add(new ErroValidacao(origem, campo + " nao informado"));
            return;
        }
        if (!Files.isDirectory(path.get())) {
            erros.add(new ErroValidacao(origem, campo + " nao existe ou nao e pasta: " + path.get()));
        }
    }

    private static void validarCertificado(EmpresaImportacao empresa, String origem, List<ErroValidacao> erros) {
        if (empresa.certificadoPasta().isEmpty()) {
            erros.add(new ErroValidacao(origem, "Pasta do certificado nao informada"));
            return;
        }
        if (empresa.certificadoArquivo().isEmpty()) {
            erros.add(new ErroValidacao(origem, "Arquivo do certificado nao informado"));
            return;
        }
        Path arquivo = empresa.certificadoPasta().get().resolve(empresa.certificadoArquivo().get());
        if (!Files.isRegularFile(arquivo)) {
            erros.add(new ErroValidacao(origem, "Arquivo de certificado nao encontrado: " + arquivo));
        }
        if (empresa.validadeCertificado().isEmpty()) {
            erros.add(new ErroValidacao(origem, "Validade do certificado nao informada"));
        } else if (empresa.validadeCertificado().get().isBefore(LocalDate.now())) {
            erros.add(new ErroValidacao(origem, "Certificado vencido em " + empresa.validadeCertificado().get()));
        }
    }
}
