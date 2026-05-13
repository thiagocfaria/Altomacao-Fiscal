package br.com.nfse.importadorpn.prevoo;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import br.com.nfse.importadorpn.portal.ConsultaDfePortal;
import br.com.nfse.importadorpn.portal.DadosDocumentoDfe;
import br.com.nfse.importadorpn.portal.DetalheRotaDmsAusente;
import br.com.nfse.importadorpn.portal.DocumentoDfeExtraido;
import br.com.nfse.importadorpn.portal.PlanejadorDocumentoDfe;
import br.com.nfse.importadorpn.portal.PlanoDocumentoDfe;
import br.com.nfse.importadorpn.portal.ReconciliadorPortalDestino;
import br.com.nfse.importadorpn.portal.ResultadoProcessamentoLote;
import br.com.nfse.importadorpn.portal.ResultadoReconciliacaoPortal;
import br.com.nfse.importadorpn.portal.RoteadorDmsPorEmissao;
import br.com.nfse.importadorpn.portal.StatusPlanoDocumento;
import java.time.Instant;
import java.time.YearMonth;

public final class SimuladorReconciliacaoDryRun {
    private final ConsultaDfePortal consultaPortal;
    private final ReconciliadorPortalDestino reconciliador;
    private final PlanejadorDocumentoDfe planejador;

    public SimuladorReconciliacaoDryRun(ConsultaDfePortal consultaPortal) {
        this(consultaPortal, new ReconciliadorPortalDestino(), new PlanejadorDocumentoDfe());
    }

    SimuladorReconciliacaoDryRun(ConsultaDfePortal consultaPortal, ReconciliadorPortalDestino reconciliador,
                                 PlanejadorDocumentoDfe planejador) {
        this.consultaPortal = consultaPortal;
        this.reconciliador = reconciliador;
        this.planejador = planejador;
    }

    public ResultadoDryRunReconciliacao simular(CadastroImportacao cadastro, CadastroImportacao cadastroTodosMeses,
                                                YearMonth mes, String nsuInicial, int maxLotes) throws Exception {
        RoteadorDmsPorEmissao roteadorDms = new RoteadorDmsPorEmissao(cadastroTodosMeses, mes.getYear());
        ResultadoReconciliacaoPortal resultado = reconciliador.executar(cadastro, mes, nsuInicial, maxLotes,
                consultaPortal,
                (empresa, nsu, consulta, estadoDestino, documentos, agora) -> {
                    ResultadoProcessamentoLote acumulado = ResultadoProcessamentoLote.vazio();
                    for (var documento : documentos) {
                        PlanoDocumentoDfe plano = planejador.planejar(empresa, documento, mes, estadoDestino,
                                roteadorDms);
                        acumulado = acumulado.somar(resultadoPlano(empresa, documento, plano));
                    }
                    return acumulado;
                },
                Instant.now());
        return new ResultadoDryRunReconciliacao(resultado, nivel(resultado));
    }

    private static ResultadoProcessamentoLote resultadoPlano(EmpresaImportacao empresa, DocumentoDfeExtraido documento,
                                                             PlanoDocumentoDfe plano) {
        if (plano.status() == StatusPlanoDocumento.COMPLETO) {
            return new ResultadoProcessamentoLote(0, 1, 0, 0, 0, 0, 0, 0);
        }
        if (plano.status() == StatusPlanoDocumento.FORA_DO_MES) {
            return new ResultadoProcessamentoLote(0, 0, 0, 0, 0, 1, 0, 0);
        }
        if (plano.status() == StatusPlanoDocumento.SEM_DATA_EMISSAO) {
            return new ResultadoProcessamentoLote(0, 0, 0, 0, 0, 0, 1, 0);
        }
        if (plano.status() == StatusPlanoDocumento.NAO_PERTENCE_EMPRESA) {
            return new ResultadoProcessamentoLote(0, 0, 0, 0, 0, 0, 1, 0, 0);
        }
        return new ResultadoProcessamentoLote(
                1,
                0,
                plano.xmlRestFaltante() ? 1 : 0,
                plano.pdfRestFaltante() ? 1 : 0,
                plano.dmsFaltante() ? 1 : 0,
                0,
                0,
                0,
                plano.rotaDmsAusente() ? 1 : 0,
                plano.rotaDmsAusente() ? java.util.List.of(detalheRotaDmsAusente(empresa, documento)) : java.util.List.of());
    }

    private static DetalheRotaDmsAusente detalheRotaDmsAusente(EmpresaImportacao empresa,
                                                               DocumentoDfeExtraido documento) {
        DadosDocumentoDfe dados = DadosDocumentoDfe.fromXml(documento.xml());
        return new DetalheRotaDmsAusente(
                empresa.nome(),
                empresa.cnpj(),
                cnpjEncontradoNoXml(empresa, dados),
                dados.mesEmissao().orElse(null),
                documento.nsu(),
                documento.chaveAcesso());
    }

    private static String cnpjEncontradoNoXml(EmpresaImportacao empresa, DadosDocumentoDfe dados) {
        if (empresa.cnpj().equals(dados.cnpjPrestador())) {
            return dados.cnpjPrestador();
        }
        if (empresa.cnpj().equals(dados.cnpjTomador())) {
            return dados.cnpjTomador();
        }
        if (empresa.cnpj().equals(dados.cnpjIntermediario())) {
            return dados.cnpjIntermediario();
        }
        return "";
    }

    private static NivelPrevoo nivel(ResultadoReconciliacaoPortal resultado) {
        if (resultado.erroExternoPortal()) {
            return NivelPrevoo.ERRO_EXTERNO;
        }
        if (resultado.totalRotaDmsAusente() > 0) {
            return NivelPrevoo.BLOQUEADO;
        }
        if (resultado.truncadoPorMaxLotes()) {
            return NivelPrevoo.ATENCAO;
        }
        return NivelPrevoo.OK;
    }
}
