package br.com.nfse.importadorpn.prevoo;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class VerificacaoCaminhos {
    private static final List<String> SUBPASTAS_REST = List.of(
            "XML/processados",
            "XML/RETIDO",
            "XML/canceladas",
            "PDF/processados",
            "PDF/RETIDO",
            "PDF/canceladas");

    public ResultadoPrevoo verificar(CadastroImportacao cadastro) {
        List<ProblemaPrevoo> problemas = new ArrayList<>();
        List<String> informacoes = new ArrayList<>();
        cadastro.entradaRest().ifPresent(entrada -> verificarEntradaRest(entrada, problemas, informacoes));
        verificarDiretorio(cadastro.entradaRest(), "Entrada REST tecnica", "cadastro",
                "PLANILHA_FISCAL.xlsm -> linha tecnica IMPORT API PN ENTRADA REST",
                problemas, informacoes);
        cadastro.empresas().forEach(empresa -> verificarEmpresa(empresa, problemas, informacoes));
        NivelPrevoo nivel = nivelFinal(problemas);
        return new ResultadoPrevoo(nivel, problemas, informacoes);
    }

    public ResultadoPrevoo verificarBackend(Path backend) {
        List<ProblemaPrevoo> problemas = new ArrayList<>();
        List<String> informacoes = new ArrayList<>();
        verificarDiretorio(Optional.ofNullable(backend), "Backend IMPORT API PN", "backend",
                "painel.py -> BACKEND / parametro --backend",
                problemas, informacoes);
        return new ResultadoPrevoo(nivelFinal(problemas), problemas, informacoes);
    }

    private static void verificarEmpresa(EmpresaImportacao empresa, List<ProblemaPrevoo> problemas,
                                         List<String> informacoes) {
        verificarDiretorio(empresa.caminhoRest(), "CAMINHO REST", empresa.nome(),
                origemPlanilha(empresa, "CAMINHO REST"), problemas, informacoes);
        empresa.caminhoRest().ifPresent(rest -> verificarSubpastasRest(empresa, rest, problemas, informacoes));
        verificarDiretorio(empresa.caminhoDms(), "CAMINHO DMS", empresa.nome(),
                origemPlanilha(empresa, "CAMINHO DMS"), problemas, informacoes);
    }

    private static void verificarSubpastasRest(EmpresaImportacao empresa, Path rest,
                                               List<ProblemaPrevoo> problemas, List<String> informacoes) {
        if (!Files.isDirectory(rest)) {
            return;
        }
        for (String subpasta : SUBPASTAS_REST) {
            Path destino = rest.resolve(subpasta);
            try {
                if (Files.exists(destino)) {
                    if (!Files.isDirectory(destino)) {
                        problemas.add(new ProblemaPrevoo(
                                NivelPrevoo.BLOQUEADO,
                                empresa.nome(),
                                "Subpasta REST existe mas nao e pasta: " + destino,
                                origemPlanilha(empresa, "CAMINHO REST"),
                                "Remova/corrija este caminho e clique VERIFICAR TUDO novamente."));
                    }
                    continue;
                }
                testarCriacaoDiretorioTemporario(rest);
                informacoes.add("OK: " + empresa.nome() + " subpastas REST ausentes podem ser criadas em: " + rest);
            } catch (IOException e) {
                problemas.add(new ProblemaPrevoo(
                        NivelPrevoo.BLOQUEADO,
                        empresa.nome(),
                        "Subpasta REST nao pode ser criada: " + destino,
                        origemPlanilha(empresa, "CAMINHO REST"),
                        "Corrija permissao/caminho e clique VERIFICAR TUDO novamente."));
            }
        }
    }

    private static void verificarEntradaRest(Path entradaRest, List<ProblemaPrevoo> problemas,
                                             List<String> informacoes) {
        if (!Files.isDirectory(entradaRest)) {
            return;
        }
        try (Stream<Path> arquivos = Files.list(entradaRest)) {
            long pendentes = arquivos
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().startsWith(".prevoo-"))
                    .count();
            if (pendentes == 0) {
                informacoes.add("OK: Entrada REST sem arquivos soltos.");
                return;
            }
            problemas.add(new ProblemaPrevoo(
                    NivelPrevoo.ATENCAO,
                    "entrada-rest",
                    "Entrada REST contem " + pendentes + " arquivo(s) pendente(s).",
                    "Pasta entrada REST tecnica: " + entradaRest,
                    "Confira se o RENOMEADOR esta ligado e se esses arquivos devem ser processados."));
        } catch (IOException e) {
            problemas.add(new ProblemaPrevoo(
                    NivelPrevoo.BLOQUEADO,
                    "entrada-rest",
                    "Entrada REST nao pode ser lida: " + entradaRest,
                    "PLANILHA_FISCAL.xlsm -> linha tecnica IMPORT API PN ENTRADA REST",
                    "Corrija a permissao da pasta e clique VERIFICAR TUDO novamente."));
        }
    }

    private static void verificarDiretorio(Optional<Path> path, String rotulo, String empresa,
                                           String ondeCorrigir, List<ProblemaPrevoo> problemas,
                                           List<String> informacoes) {
        if (path.isEmpty()) {
            problemas.add(new ProblemaPrevoo(NivelPrevoo.BLOQUEADO, empresa, rotulo + " nao informado",
                    ondeCorrigir, "Preencha o caminho na planilha e clique VERIFICAR TUDO novamente."));
            return;
        }
        Path diretorio = path.orElseThrow();
        if (!Files.isDirectory(diretorio)) {
            problemas.add(new ProblemaPrevoo(NivelPrevoo.BLOQUEADO, empresa,
                    rotulo + " nao existe ou nao e pasta: " + diretorio,
                    ondeCorrigir, "Corrija/crie a pasta e clique VERIFICAR TUDO novamente."));
            return;
        }
        try {
            testarEscrita(diretorio);
            informacoes.add("OK: " + empresa + " " + rotulo + " gravavel: " + diretorio);
        } catch (IOException e) {
            problemas.add(new ProblemaPrevoo(NivelPrevoo.BLOQUEADO, empresa,
                    rotulo + " nao aceita escrita: " + diretorio,
                    ondeCorrigir, "Corrija a permissao da pasta e clique VERIFICAR TUDO novamente."));
        }
    }

    private static void testarEscrita(Path diretorio) throws IOException {
        Path teste = null;
        try {
            teste = Files.createTempFile(diretorio, ".prevoo-", ".tmp");
        } finally {
            if (teste != null) {
                Files.deleteIfExists(teste);
            }
        }
    }

    private static void testarCriacaoDiretorioTemporario(Path diretorio) throws IOException {
        Path teste = null;
        try {
            teste = Files.createTempDirectory(diretorio, ".prevoo-dir-");
        } finally {
            if (teste != null) {
                Files.deleteIfExists(teste);
            }
        }
    }

    private static String origemPlanilha(EmpresaImportacao empresa, String campo) {
        return "PLANILHA_FISCAL.xlsm -> " + empresa.aba() + " -> linha " + empresa.linha() + " -> " + campo;
    }

    private static NivelPrevoo nivelFinal(List<ProblemaPrevoo> problemas) {
        if (problemas.stream().anyMatch(p -> p.nivel() == NivelPrevoo.BLOQUEADO)) {
            return NivelPrevoo.BLOQUEADO;
        }
        if (problemas.stream().anyMatch(p -> p.nivel() == NivelPrevoo.ATENCAO)) {
            return NivelPrevoo.ATENCAO;
        }
        return NivelPrevoo.OK;
    }
}
