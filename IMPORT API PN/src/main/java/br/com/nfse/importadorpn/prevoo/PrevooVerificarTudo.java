package br.com.nfse.importadorpn.prevoo;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.ErroValidacao;
import br.com.nfse.importadorpn.configuracao.ResultadoValidacao;
import br.com.nfse.importadorpn.configuracao.ValidadorCadastro;
import br.com.nfse.importadorpn.portal.ConsultaDfePortal;
import br.com.nfse.importadorpn.portal.ResumoEmpresaReconciliacao;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public final class PrevooVerificarTudo {
    private final ConsultaDfePortal consultaPortal;

    public PrevooVerificarTudo() {
        this(null);
    }

    public PrevooVerificarTudo(ConsultaDfePortal consultaPortal) {
        this.consultaPortal = consultaPortal;
    }

    public ResultadoPrevoo verificar(CadastroImportacao cadastro, YearMonth mes, Path backend) {
        return verificarLocal(cadastro, mes, backend);
    }

    public ResultadoPrevoo verificar(CadastroImportacao cadastro, CadastroImportacao cadastroTodosMeses,
                                     YearMonth mes, Path backend, String nsu, int maxLotes) {
        ResultadoPrevoo local = verificarLocal(cadastro, mes, backend);
        if (consultaPortal == null || local.nivel() == NivelPrevoo.BLOQUEADO) {
            return local;
        }
        try {
            ResultadoDryRunReconciliacao dryRun = new SimuladorReconciliacaoDryRun(consultaPortal)
                    .simular(cadastro, cadastroTodosMeses, mes, nsu, maxLotes);
            return local.combinar(resultadoDryRun(dryRun, maxLotes));
        } catch (Exception exception) {
            return local.combinar(new ResultadoPrevoo(
                    NivelPrevoo.ERRO_EXTERNO,
                    List.of(new ProblemaPrevoo(
                            NivelPrevoo.ERRO_EXTERNO,
                            "Portal Nacional/ADN",
                            "Portal/rede/certificado falhou na consulta real: "
                                    + exception.getClass().getSimpleName() + " - " + mensagem(exception),
                            "Certificado, rede local, Portal Nacional ou ambiente ADN",
                            "Corrija a causa externa e clique VERIFICAR TUDO novamente.")),
                    List.of("Portal real testado: NAO")));
        }
    }

    private ResultadoPrevoo verificarLocal(CadastroImportacao cadastro, YearMonth mes, Path backend) {
        List<String> informacoes = new ArrayList<>();
        informacoes.add("Empresas ativas: " + cadastro.empresas().size());

        ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
        List<ProblemaPrevoo> problemas = new ArrayList<>();
        for (ErroValidacao erro : validacao.erros()) {
            if (erroDeCaminhoJaCobertoPeloPrevoo(erro)) {
                continue;
            }
            problemas.add(problemaCadastro(erro, mes));
        }
        ResultadoPrevoo resultadoCadastro = new ResultadoPrevoo(
                validacao.aprovado() ? NivelPrevoo.OK : NivelPrevoo.BLOQUEADO,
                problemas,
                informacoes);
        ResultadoPrevoo resultadoCaminhos = new VerificacaoCaminhos().verificar(cadastro);
        ResultadoPrevoo resultadoBackend = new VerificacaoCaminhos().verificarBackend(backend);
        ResultadoPrevoo resultadoCertificados = new VerificacaoCertificadosPrevoo().verificar(cadastro);
        return resultadoCadastro
                .combinar(resultadoCaminhos)
                .combinar(resultadoBackend)
                .combinar(resultadoCertificados);
    }

    private static ResultadoPrevoo resultadoDryRun(ResultadoDryRunReconciliacao dryRun, int maxLotes) {
        List<String> informacoes = new ArrayList<>();
        informacoes.add("Portal real testado: SIM");
        informacoes.add("Dry-run reconciliar: SIM");
        informacoes.add("Lotes consultados no Portal: " + dryRun.totalLotesConsultados());
        informacoes.add("Documentos retornados pelo Portal: " + dryRun.totalDocumentosPortal());
        informacoes.add("Documentos completos no destino: " + dryRun.totalDocumentosCompletos());
        informacoes.add("Notas faltantes encontradas: " + dryRun.totalDocumentosAfetados());
        informacoes.add("Faltantes REST XML: " + dryRun.totalXmlRestFaltantes());
        informacoes.add("Faltantes REST PDF: " + dryRun.totalPdfRestFaltantes());
        informacoes.add("Faltantes DMS: " + dryRun.totalDmsFaltantes());
        informacoes.add("Documentos fora do mes: " + dryRun.totalForaDoMes());
        informacoes.add("Documentos fora da empresa consultada: " + dryRun.totalForaDaEmpresa());
        informacoes.add("XML sem data de emissao: " + dryRun.totalSemDataEmissao());
        informacoes.add("Erros externos Portal: " + dryRun.totalErrosExternosPortal());
        int limiteRotasDms = Math.min(20, dryRun.rotasDmsAusentes().size());
        for (int i = 0; i < limiteRotasDms; i++) {
            informacoes.add(dryRun.rotasDmsAusentes().get(i).resumo());
        }
        if (dryRun.rotasDmsAusentes().size() > limiteRotasDms) {
            informacoes.add("DMS sem rota: ... mais "
                    + (dryRun.rotasDmsAusentes().size() - limiteRotasDms)
                    + " detalhe(s) omitido(s)");
        }
        for (ResumoEmpresaReconciliacao empresa : dryRun.empresas()) {
            informacoes.add("Empresa " + empresa.empresa()
                    + ": Portal lotes=" + empresa.lotesConsultados()
                    + ", documentos=" + empresa.documentosPortal()
                    + ", completos=" + empresa.processamento().documentosCompletos()
                    + ", faltantes REST XML=" + empresa.processamento().xmlRestFaltantes()
                    + ", faltantes REST PDF=" + empresa.processamento().pdfRestFaltantes()
                    + ", faltantes DMS=" + empresa.processamento().dmsFaltantes()
                    + ", fora do mes=" + empresa.processamento().foraDoMes()
                    + ", fora da empresa=" + empresa.processamento().foraDaEmpresa()
                    + ", sem data=" + empresa.processamento().semDataEmissao());
        }

        List<ProblemaPrevoo> problemas = new ArrayList<>();
        for (ResumoEmpresaReconciliacao empresa : dryRun.empresas()) {
            if (empresa.erroExternoPortal()) {
                problemas.add(new ProblemaPrevoo(
                        NivelPrevoo.ERRO_EXTERNO,
                        empresa.empresa(),
                        "Portal Nacional/ADN retornou erro na consulta real: "
                                + empresa.erroExternoPortalMensagem(),
                        "Portal Nacional, certificado, rede local ou ambiente ADN",
                        "Corrija a causa externa e clique VERIFICAR TUDO novamente."));
            }
        }
        if (dryRun.truncadoPorMaxLotes()) {
            problemas.add(new ProblemaPrevoo(
                    NivelPrevoo.ATENCAO,
                    "Portal Nacional/ADN",
                    "Dry-run atingiu --max-lotes=" + maxLotes
                            + " antes de uma parada natural. A rodada real continuara nos proximos ciclos.",
                    "Parametro --max-lotes do painel",
                    "Aumente o limite se quiser validar a fila inteira agora ou aceite ligar com atencao."));
        }
        if (dryRun.totalRotaDmsAusente() > 0) {
            problemas.add(new ProblemaPrevoo(
                    NivelPrevoo.BLOQUEADO,
                    "DMS",
                    "Ha " + dryRun.totalRotaDmsAusente()
                            + " documento(s) do Portal sem rota DMS para CNPJ/data de emissao.",
                    "PLANILHA_FISCAL.xlsm -> abas mensais -> CAMINHO DMS",
                    "Corrija a rota mensal do DMS e clique VERIFICAR TUDO novamente."));
        }
        return new ResultadoPrevoo(dryRun.nivel(), problemas, informacoes);
    }

    private static String mensagem(Exception exception) {
        String mensagem = exception.getMessage();
        return mensagem == null || mensagem.isBlank() ? "sem detalhe" : mensagem;
    }

    private static ProblemaPrevoo problemaCadastro(ErroValidacao erro, YearMonth mes) {
        if (erro.mensagem().contains("Nenhuma empresa ativa do IMPORT API PN foi encontrada")) {
            return new ProblemaPrevoo(
                    NivelPrevoo.BLOQUEADO,
                    erro.origem(),
                    erro.mensagem(),
                    "PLANILHA_FISCAL.xlsm -> " + abaCadastro(mes) + " -> coluna IMPORT API PN ATIVO",
                    "Marque SIM na coluna IMPORT API PN ATIVO para as empresas reais que devem consultar "
                            + "o Portal neste mes. A linha tecnica IMPORT API PN ENTRADA REST nao conta "
                            + "como empresa.");
        }
        return new ProblemaPrevoo(
                NivelPrevoo.BLOQUEADO,
                erro.origem(),
                erro.mensagem(),
                "PLANILHA_FISCAL.xlsm -> " + erro.origem(),
                "Corrija a planilha/caminho e clique VERIFICAR TUDO novamente.");
    }

    private static String abaCadastro(YearMonth mes) {
        return "CADASTRO " + switch (mes.getMonth()) {
            case JANUARY -> "JANEIRO";
            case FEBRUARY -> "FEVEREIRO";
            case MARCH -> "MARCO";
            case APRIL -> "ABRIL";
            case MAY -> "MAIO";
            case JUNE -> "JUNHO";
            case JULY -> "JULHO";
            case AUGUST -> "AGOSTO";
            case SEPTEMBER -> "SETEMBRO";
            case OCTOBER -> "OUTUBRO";
            case NOVEMBER -> "NOVEMBRO";
            case DECEMBER -> "DEZEMBRO";
        };
    }

    private static boolean erroDeCaminhoJaCobertoPeloPrevoo(ErroValidacao erro) {
        String mensagem = erro.mensagem();
        return mensagem.contains("entrada-rest nao existe")
                || mensagem.contains("CAMINHO REST nao existe")
                || mensagem.contains("CAMINHO DMS nao existe");
    }
}
