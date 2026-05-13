package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ReconciliadorPortalDestino {
    private final ChavesPresentesNoDestino scanner;
    private final ExtratorLoteDfeJson extratorLote;

    public ReconciliadorPortalDestino() {
        this(new ChavesPresentesNoDestino(), new ExtratorLoteDfeJson());
    }

    ReconciliadorPortalDestino(ChavesPresentesNoDestino scanner, ExtratorLoteDfeJson extratorLote) {
        this.scanner = scanner;
        this.extratorLote = extratorLote;
    }

    public ResultadoReconciliacaoPortal executar(CadastroImportacao cadastro, YearMonth mes, String nsuInicial,
                                                 int maxLotes, ConsultaDfePortal consulta,
                                                 ProcessadorResultadoPortal processador,
                                                 Instant agora) throws Exception {
        int limiteLotes = Math.max(1, maxLotes);
        List<ResumoEmpresaReconciliacao> resumos = new ArrayList<>();
        for (EmpresaImportacao empresa : cadastro.empresas()) {
            if (empresa.cnpj() == null || empresa.cnpj().isBlank()) {
                continue;
            }
            EstadoDestinoNotas estadoDestino = scanner.escanearEstado(empresa, mes);
            long nsuAtual = Long.parseLong(nsuInicial);
            int lotesConsultados = 0;
            int documentosPortal = 0;
            boolean truncado = false;
            boolean erroExternoPortal = false;
            String erroExternoPortalMensagem = "";
            ResultadoProcessamentoLote processamento = ResultadoProcessamentoLote.vazio();
            while (lotesConsultados < limiteLotes) {
                String nsuTexto = Long.toString(nsuAtual);
                ResultadoConsultaAdn resultado = consulta.consultar(empresa, nsuTexto);
                lotesConsultados++;
                if (fimNaturalDfe(resultado)) {
                    break;
                }
                if (resultado.statusCode() != 200) {
                    erroExternoPortal = true;
                    erroExternoPortalMensagem = "HTTP " + resultado.statusCode()
                            + (resultado.contentType() == null || resultado.contentType().isBlank()
                            ? "" : " " + resultado.contentType());
                    break;
                }
                List<DocumentoDfeExtraido> documentos = extratorLote.extrair(resultado);
                processamento = processamento.somar(
                        processador.processar(empresa, nsuTexto, resultado, estadoDestino, documentos, agora));
                documentosPortal += documentos.size();
                Optional<Long> proximoNsu = proximoNsu(documentos, nsuAtual);
                if (documentos.isEmpty() || proximoNsu.isEmpty()) {
                    break;
                }
                nsuAtual = proximoNsu.orElseThrow();
                if (lotesConsultados >= limiteLotes) {
                    truncado = true;
                }
            }
            resumos.add(new ResumoEmpresaReconciliacao(
                    empresa.nome(),
                    empresa.cnpj(),
                    estadoDestino.chavesCompletas().size(),
                    estadoDestino.chavesXmlRest().size(),
                    estadoDestino.chavesPdfRest().size(),
                    estadoDestino.chavesDms().size(),
                    lotesConsultados,
                    documentosPortal,
                    processamento,
                    truncado,
                    erroExternoPortal,
                    erroExternoPortalMensagem));
        }
        return new ResultadoReconciliacaoPortal(resumos);
    }

    private static boolean fimNaturalDfe(ResultadoConsultaAdn resultado) {
        return resultado.statusCode() == 404;
    }

    private static Optional<Long> proximoNsu(List<DocumentoDfeExtraido> documentos, long nsuAtual) {
        long maior = nsuAtual;
        for (DocumentoDfeExtraido documento : documentos) {
            try {
                maior = Math.max(maior, Long.parseLong(documento.nsu()));
            } catch (NumberFormatException ignored) {
            }
        }
        return maior > nsuAtual ? Optional.of(maior + 1) : Optional.empty();
    }

    @FunctionalInterface
    public interface ProcessadorResultadoPortal {
        ResultadoProcessamentoLote processar(EmpresaImportacao empresa, String nsu,
                                             ResultadoConsultaAdn resultado, EstadoDestinoNotas estadoDestino,
                                             List<DocumentoDfeExtraido> documentos, Instant agora) throws Exception;
    }
}
