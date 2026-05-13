package br.com.nfse.importadorpn.prevoo;

import br.com.nfse.importadorpn.certificado.ResolvedorSenhaCertificado;
import br.com.nfse.importadorpn.certificado.ResultadoCertificado;
import br.com.nfse.importadorpn.certificado.ResultadoSenhaCertificado;
import br.com.nfse.importadorpn.certificado.ValidadorCertificado;
import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class VerificacaoCertificadosPrevoo {
    private final ResolvedorSenha resolvedorSenha;
    private final ValidadorArquivoCertificado validadorCertificado;

    public VerificacaoCertificadosPrevoo() {
        ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado();
        ValidadorCertificado validador = new ValidadorCertificado();
        this.resolvedorSenha = resolvedor::resolver;
        this.validadorCertificado = validador::validar;
    }

    VerificacaoCertificadosPrevoo(ResolvedorSenha resolvedorSenha,
                                  ValidadorArquivoCertificado validadorCertificado) {
        this.resolvedorSenha = resolvedorSenha;
        this.validadorCertificado = validadorCertificado;
    }

    public ResultadoPrevoo verificar(CadastroImportacao cadastro) {
        List<ProblemaPrevoo> problemas = new ArrayList<>();
        List<String> informacoes = new ArrayList<>();
        Map<String, ResultadoCertificado> cache = new HashMap<>();
        for (EmpresaImportacao empresa : cadastro.empresas()) {
            verificarEmpresa(empresa, cache, problemas, informacoes);
        }
        NivelPrevoo nivel = problemas.isEmpty() ? NivelPrevoo.OK : NivelPrevoo.BLOQUEADO;
        return new ResultadoPrevoo(nivel, problemas, informacoes);
    }

    private void verificarEmpresa(EmpresaImportacao empresa, Map<String, ResultadoCertificado> cache,
                                  List<ProblemaPrevoo> problemas, List<String> informacoes) {
        if (empresa.certificadoPasta().isEmpty() || empresa.certificadoArquivo().isEmpty()) {
            return;
        }
        String alias = empresa.certificadoAlias().orElse(empresa.cnpj());
        ResultadoSenhaCertificado senha = resolvedorSenha.resolver(alias,
                empresa.senhaCertificadoPlanilha().orElse(null));
        if (!senha.encontrada()) {
            problemas.add(problema(empresa, "Senha do certificado nao encontrada em " + senha.origem(),
                    "Informe a senha por variavel de ambiente ou na planilha."));
            return;
        }
        Path arquivo = empresa.certificadoPasta().orElseThrow().resolve(empresa.certificadoArquivo().orElseThrow());
        String valorSenha = senha.senha().orElseThrow();
        String chaveCache = arquivo.toAbsolutePath().normalize() + "|" + alias + "|" + senha.origem();
        ResultadoCertificado certificado = cache.computeIfAbsent(chaveCache,
                ignored -> validarComSenhaTemporaria(arquivo, valorSenha));
        if (!certificado.valido()) {
            problemas.add(problema(empresa, certificado.mensagem(), "Corrija arquivo/senha do certificado."));
            return;
        }
        if (certificado.cnpjs().isEmpty()) {
            problemas.add(problema(empresa,
                    "Certificado sem CNPJ confirmado para comparar com a planilha: " + empresa.cnpj(),
                    "Use um certificado ICP-Brasil A1 com CNPJ identificavel."));
            return;
        }
        if (!certificado.cnpjs().contains(empresa.cnpj())) {
            problemas.add(problema(empresa,
                    "CNPJ do certificado diferente do CNPJ da planilha. Esperado: "
                            + empresa.cnpj() + ". Encontrado(s): " + certificado.cnpjs(),
                    "Corrija o certificado ou o CNPJ na planilha."));
            return;
        }
        informacoes.add("OK: " + empresa.nome() + " certificado pertence ao CNPJ " + empresa.cnpj());
    }

    private ResultadoCertificado validarComSenhaTemporaria(Path arquivo, String valorSenha) {
        char[] senha = valorSenha.toCharArray();
        try {
            return validadorCertificado.validar(arquivo, senha);
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    private static ProblemaPrevoo problema(EmpresaImportacao empresa, String mensagem, String acao) {
        return new ProblemaPrevoo(
                NivelPrevoo.BLOQUEADO,
                empresa.nome(),
                mensagem,
                "PLANILHA_FISCAL.xlsm -> " + empresa.aba() + " -> linha " + empresa.linha()
                        + " -> certificado/CNPJ",
                acao + " Depois clique VERIFICAR TUDO novamente.");
    }

    @FunctionalInterface
    interface ResolvedorSenha {
        ResultadoSenhaCertificado resolver(String alias, String senhaPlanilha);
    }

    @FunctionalInterface
    interface ValidadorArquivoCertificado {
        ResultadoCertificado validar(Path arquivo, char[] senha);
    }
}
