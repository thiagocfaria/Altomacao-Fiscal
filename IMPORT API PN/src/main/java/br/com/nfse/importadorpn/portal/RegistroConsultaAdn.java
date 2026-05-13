package br.com.nfse.importadorpn.portal;

import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import br.com.nfse.importadorpn.ledger.EstadoDocumento;
import br.com.nfse.importadorpn.ledger.RegistroImportacao;
import br.com.nfse.importadorpn.ledger.RepositorioImportacao;
import br.com.nfse.importadorpn.publicacao.PublicadorRestEntrada;
import br.com.nfse.importadorpn.publicacao.PublicadorRespostaAdn;
import br.com.nfse.importadorpn.publicacao.DmsDiretoPublicado;
import br.com.nfse.importadorpn.publicacao.PublicadorDmsDireto;
import br.com.nfse.importadorpn.publicacao.RestEntradaPublicada;
import br.com.nfse.importadorpn.publicacao.RespostaAdnPublicada;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public final class RegistroConsultaAdn {
    private final RepositorioImportacao repositorio;
    private final PublicadorRespostaAdn publicadorTecnico;
    private final PublicadorRestEntrada publicadorRest;
    private final PublicadorDmsDireto publicadorDms;
    private final ConsultaDanfse consultaDanfse;
    private final boolean recomporPdfConcluidoAusente;
    private final RoteadorDms roteadorDms;

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico) {
        this.repositorio = repositorio;
        this.publicadorTecnico = publicadorTecnico;
        this.publicadorRest = null;
        this.publicadorDms = null;
        this.consultaDanfse = null;
        this.recomporPdfConcluidoAusente = false;
        this.roteadorDms = null;
    }

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico,
            PublicadorRestEntrada publicadorRest) {
        this.repositorio = repositorio;
        this.publicadorTecnico = publicadorTecnico;
        this.publicadorRest = publicadorRest;
        this.publicadorDms = null;
        this.consultaDanfse = null;
        this.recomporPdfConcluidoAusente = false;
        this.roteadorDms = null;
    }

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico,
            PublicadorRestEntrada publicadorRest, ConsultaDanfse consultaDanfse) {
        this.repositorio = repositorio;
        this.publicadorTecnico = publicadorTecnico;
        this.publicadorRest = publicadorRest;
        this.publicadorDms = null;
        this.consultaDanfse = consultaDanfse;
        this.recomporPdfConcluidoAusente = false;
        this.roteadorDms = null;
    }

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico,
            PublicadorRestEntrada publicadorRest, PublicadorDmsDireto publicadorDms, ConsultaDanfse consultaDanfse) {
        this(repositorio, publicadorTecnico, publicadorRest, publicadorDms, consultaDanfse, false);
    }

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico,
            PublicadorRestEntrada publicadorRest, PublicadorDmsDireto publicadorDms, ConsultaDanfse consultaDanfse,
            RoteadorDms roteadorDms) {
        this(repositorio, publicadorTecnico, publicadorRest, publicadorDms, consultaDanfse, false, roteadorDms);
    }

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico,
            PublicadorRestEntrada publicadorRest, PublicadorDmsDireto publicadorDms, ConsultaDanfse consultaDanfse,
            boolean recomporPdfConcluidoAusente) {
        this(repositorio, publicadorTecnico, publicadorRest, publicadorDms, consultaDanfse,
                recomporPdfConcluidoAusente, null);
    }

    public RegistroConsultaAdn(RepositorioImportacao repositorio, PublicadorRespostaAdn publicadorTecnico,
            PublicadorRestEntrada publicadorRest, PublicadorDmsDireto publicadorDms, ConsultaDanfse consultaDanfse,
            boolean recomporPdfConcluidoAusente, RoteadorDms roteadorDms) {
        this.repositorio = repositorio;
        this.publicadorTecnico = publicadorTecnico;
        this.publicadorRest = publicadorRest;
        this.publicadorDms = publicadorDms;
        this.consultaDanfse = consultaDanfse;
        this.recomporPdfConcluidoAusente = recomporPdfConcluidoAusente;
        this.roteadorDms = roteadorDms;
    }

    public ResultadoRegistroConsulta registrar(EmpresaImportacao empresa, String nsu,
            ResultadoConsultaAdn resultado, YearMonth mes, Instant agora) throws IOException {
        return registrar(empresa, nsu, resultado, mes, agora, (EstadoDestinoNotas) null);
    }

    /**
     * Overload que aceita um conjunto de chaves de acesso ja presentes no destino do cliente.
     * Documentos cuja chave esta neste conjunto sao PULADOS - implementa a REGRA INVARIANTE #1
     * (decisao de importar olha o destino real, nao o ledger interno).
     */
    public ResultadoRegistroConsulta registrar(EmpresaImportacao empresa, String nsu,
            ResultadoConsultaAdn resultado, YearMonth mes, Instant agora,
            java.util.Set<String> chavesJaPresentesNoDestino) throws IOException {
        return registrar(empresa, nsu, resultado, mes, agora,
                EstadoDestinoNotas.completoPara(chavesJaPresentesNoDestino));
    }

    public ResultadoRegistroConsulta registrar(EmpresaImportacao empresa, String nsu,
            ResultadoConsultaAdn resultado, YearMonth mes, Instant agora,
            EstadoDestinoNotas estadoDestino) throws IOException {
        Optional<RegistroImportacao> existente = repositorio.buscar(empresa.cnpj(), nsu, chaveLedger(nsu));
        RegistroImportacao base = existente.orElseGet(() ->
                RegistroImportacao.novo(empresa.cnpj(), nsu, chaveLedger(nsu), empresa.nome(), agora));

        RegistroImportacao atualizado;
        RespostaAdnPublicada tecnica = null;
        if (resultado.statusCode() == 200 && resultado.bytes() > 0) {
            tecnica = publicadorTecnico.publicar(empresa.cnpj(), nsu, mes, resultado);
        }
        if (resultado.statusCode() == 200 && resultado.bytes() > 0 && contem(resultado.contentType(), "json")) {
            ResultadoRegistroConsulta registroLote = registrarLoteJson(empresa, resultado, mes, agora, tecnica,
                    estadoDestino);
            if (registroLote.documentosPublicados() > 0 || !registroLote.falhaRegistrada()) {
                return registroLote;
            }
        }
        if (resultado.statusCode() == 200 && resultado.bytes() > 0 && publicavelNaRest(resultado.contentType())) {
            String caminhoOperacional = tecnica.caminho().toString();
            if (publicadorRest != null) {
                RestEntradaPublicada rest = publicadorRest.publicar(empresa.cnpj(), nsu, mes, resultado);
                caminhoOperacional = rest.caminho().toString();
            }
            atualizado = base
                    .comEstadoXml(estadoXml(resultado.contentType()))
                    .comEstadoPdf(estadoPdf(resultado.contentType()))
                    .comEstadoDms(EstadoDocumento.PENDENTE)
                    .comTentativas(base.tentativas() + 1)
                    .comUltimoErro("")
                    .comCaminhoRestFinal(caminhoOperacional)
                    .comAtualizadoEm(agora);
            repositorio.salvar(atualizado);
            return new ResultadoRegistroConsulta(true, false, caminhoOperacional, tecnica.caminho().toString());
        }

        atualizado = base
                .comEstadoXml(EstadoDocumento.FALHA)
                .comEstadoPdf(EstadoDocumento.PENDENTE)
                .comEstadoDms(EstadoDocumento.PENDENTE)
                .comTentativas(base.tentativas() + 1)
                .comUltimoErro("HTTP " + resultado.statusCode() + detalheContentType(resultado.contentType()))
                .comAtualizadoEm(agora);
        repositorio.salvar(atualizado);
        return new ResultadoRegistroConsulta(false, true, "",
                tecnica == null ? "" : tecnica.caminho().toString());
    }

    private static EstadoDocumento estadoXml(String contentType) {
        return contem(contentType, "xml") || contem(contentType, "zip")
                ? EstadoDocumento.CONCLUIDO
                : EstadoDocumento.PENDENTE;
    }

    private static EstadoDocumento estadoPdf(String contentType) {
        return contem(contentType, "pdf") || contem(contentType, "zip")
                ? EstadoDocumento.CONCLUIDO
                : EstadoDocumento.PENDENTE;
    }

    private static boolean contem(String texto, String trecho) {
        return texto != null && texto.toLowerCase().contains(trecho);
    }

    private static boolean publicavelNaRest(String contentType) {
        return contem(contentType, "xml") || contem(contentType, "pdf") || contem(contentType, "zip");
    }

    private static String detalheContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "" : " " + contentType;
    }

    private static String chaveLedger(String nsu) {
        return "NSU-" + nsu;
    }

    private ResultadoRegistroConsulta registrarLoteJson(EmpresaImportacao empresa, ResultadoConsultaAdn resultado,
            YearMonth mes, Instant agora, RespostaAdnPublicada tecnica) throws IOException {
        return registrarLoteJson(empresa, resultado, mes, agora, tecnica, null);
    }

    private ResultadoRegistroConsulta registrarLoteJson(EmpresaImportacao empresa, ResultadoConsultaAdn resultado,
            YearMonth mes, Instant agora, RespostaAdnPublicada tecnica,
            EstadoDestinoNotas estadoDestino) throws IOException {
        List<DocumentoDfeExtraido> documentos = new ExtratorLoteDfeJson().extrair(resultado);
        PlanejadorDocumentoDfe planejador = new PlanejadorDocumentoDfe();
        String primeiroCaminho = "";
        int publicados = 0;
        int pulados = 0;
        for (DocumentoDfeExtraido documento : documentos) {
            String chave = documento.chaveAcesso();
            String nsuDocumento = documento.nsu().isBlank() ? "sem-nsu-" + publicados : documento.nsu();
            String chaveDocumento = chave.isBlank() ? chaveLedger(nsuDocumento) : chave;
            RegistroImportacao base = repositorio.buscar(empresa.cnpj(), nsuDocumento, chaveDocumento)
                    .orElseGet(() -> RegistroImportacao.novo(
                            empresa.cnpj(), nsuDocumento, chaveDocumento, empresa.nome(), agora));
            PlanoDocumentoDfe plano = estadoDestino == null ? null
                    : planejador.planejar(empresa, documento, mes, estadoDestino, roteadorDms);
            if (plano != null && plano.status() == StatusPlanoDocumento.COMPLETO) {
                pulados++;
                continue;
            }
            if (plano != null && (plano.status() == StatusPlanoDocumento.FORA_DO_MES
                    || plano.status() == StatusPlanoDocumento.SEM_DATA_EMISSAO
                    || plano.status() == StatusPlanoDocumento.NAO_PERTENCE_EMPRESA)) {
                repositorio.salvar(base
                        .comEstadoXml(EstadoDocumento.PENDENTE)
                        .comEstadoPdf(EstadoDocumento.PENDENTE)
                        .comEstadoDms(EstadoDocumento.PENDENTE)
                        .comTentativas(base.tentativas() + 1)
                        .comUltimoErro(plano.mensagem())
                        .comAtualizadoEm(agora));
                pulados++;
                continue;
            }
            ResultadoConsultaAdn xml = new ResultadoConsultaAdn(200, "application/xml", documento.xml());
            String caminhoOperacional = tecnica == null ? "" : tecnica.caminho().toString();
            boolean publicarXmlRest = plano == null
                    ? estadoDestino == null || chave.isBlank() || !estadoDestino.xmlRestPresente(chave)
                    : plano.xmlRestFaltante();
            boolean publicarPdfRest = plano == null
                    ? estadoDestino != null && (chave.isBlank() || !estadoDestino.pdfRestPresente(chave))
                    : plano.pdfRestFaltante();
            boolean publicarDms = plano == null
                    ? estadoDestino == null || chave.isBlank() || !estadoDestino.dmsPresente(chave)
                    : plano.dmsFaltante();
            if (publicadorRest != null && publicarXmlRest) {
                RestEntradaPublicada rest = publicadorRest.publicar(empresa.cnpj(), nsuDocumento, mes, xml);
                caminhoOperacional = rest.caminho().toString();
            }
            RegistroImportacao atualizado = base
                    .comEstadoXml(EstadoDocumento.CONCLUIDO)
                    .comEstadoPdf((estadoDestino != null && !publicarPdfRest)
                            || base.estadoPdf() == EstadoDocumento.CONCLUIDO
                            ? EstadoDocumento.CONCLUIDO
                            : EstadoDocumento.PENDENTE)
                    .comEstadoDms(publicarDms ? EstadoDocumento.PENDENTE : EstadoDocumento.CONCLUIDO)
                    .comTentativas(base.tentativas() + 1)
                    .comUltimoErro("")
                    .comCaminhoRestFinal(caminhoOperacional)
                    .comAtualizadoEm(agora);
            if (publicarDms) {
                atualizado = publicarDmsDireto(empresa, documento, nsuDocumento, mes, xml, atualizado);
            }
            if (estadoDestino == null ? deveRegistrarDanfse(empresa, nsuDocumento, mes, atualizado) : publicarPdfRest) {
                atualizado = registrarDanfse(empresa, documento, nsuDocumento, mes, atualizado, agora);
            }
            repositorio.salvar(atualizado);
            if (primeiroCaminho.isBlank()) {
                primeiroCaminho = caminhoOperacional;
            }
            publicados++;
        }
        if (publicados == 0) {
            // Se todos os documentos foram pulados por ja estarem no destino, nao e falha:
            // sistema ja esta sincronizado para esse lote. Retorna publicado=false sem falha.
            boolean falha = pulados == 0;
            return new ResultadoRegistroConsulta(false, falha, "",
                    tecnica == null ? "" : tecnica.caminho().toString(), 0);
        }
        return new ResultadoRegistroConsulta(true, false, primeiroCaminho,
                tecnica == null ? "" : tecnica.caminho().toString(), publicados);
    }

    private RegistroImportacao publicarDmsDireto(EmpresaImportacao empresa, DocumentoDfeExtraido documento,
            String nsuDocumento, YearMonth mes, ResultadoConsultaAdn xml, RegistroImportacao registro) throws IOException {
        if (publicadorDms == null) {
            return registro;
        }
        Optional<DestinoDmsResolvido> destinoResolvido = roteadorDms == null
                ? empresa.caminhoDms().map(path -> new DestinoDmsResolvido(path, empresa.cnpj(), mes))
                : roteadorDms.resolver(empresa, documento, mes);
        if (destinoResolvido.isEmpty()) {
            return registro
                    .comEstadoDms(EstadoDocumento.PENDENTE)
                    .comUltimoErro("DMS sem rota para CNPJ/data de emissao do XML");
        }
        DestinoDmsResolvido destino = destinoResolvido.orElseThrow();
        DmsDiretoPublicado publicado = publicadorDms.publicar(
                destino.caminhoDms(), destino.cnpj(), nsuDocumento, destino.mes(), xml);
        return registro
                .comEstadoDms(EstadoDocumento.CONCLUIDO)
                .comCaminhoDmsFinal(publicado.caminho().toString());
    }

    private boolean deveRegistrarDanfse(EmpresaImportacao empresa, String nsuDocumento, YearMonth mes,
            RegistroImportacao registro) {
        if (registro.estadoPdf() != EstadoDocumento.CONCLUIDO) {
            return true;
        }
        if (!recomporPdfConcluidoAusente || publicadorRest == null) {
            return false;
        }
        return Files.notExists(publicadorRest.caminhoEsperado(empresa.cnpj(), nsuDocumento, mes, "application/pdf"));
    }

    private RegistroImportacao registrarDanfse(EmpresaImportacao empresa, DocumentoDfeExtraido documento,
            String nsuDocumento, YearMonth mes, RegistroImportacao registro, Instant agora) throws IOException {
        if (consultaDanfse == null) {
            return registro;
        }
        if (documento.chaveAcesso().isBlank()) {
            return registro
                    .comEstadoPdf(EstadoDocumento.PENDENTE)
                    .comUltimoErro("DANFSe sem chave de acesso")
                    .comAtualizadoEm(agora);
        }

        Optional<ResultadoConsultaAdn> consulta;
        try {
            consulta = consultaDanfse.consultar(documento);
        } catch (Exception exception) {
            return registro
                    .comEstadoPdf(EstadoDocumento.PENDENTE)
                    .comUltimoErro("DANFSe falhou: " + exception.getClass().getSimpleName())
                    .comAtualizadoEm(agora);
        }
        if (consulta.isEmpty()) {
            return registro;
        }

        ResultadoConsultaAdn pdf = consulta.orElseThrow();
        if (!pdfValido(pdf)) {
            return registro
                    .comEstadoPdf(EstadoDocumento.PENDENTE)
                    .comUltimoErro("DANFSe invalido: HTTP " + pdf.statusCode() + detalheContentType(pdf.contentType()))
                    .comAtualizadoEm(agora);
        }

        publicadorTecnico.publicar(empresa.cnpj(), nsuDocumento + "-pdf", mes, pdf);
        if (publicadorRest != null) {
            publicadorRest.publicar(empresa.cnpj(), nsuDocumento, mes, pdf);
        }
        return registro
                .comEstadoPdf(EstadoDocumento.CONCLUIDO)
                .comUltimoErro("")
                .comAtualizadoEm(agora);
    }

    private static boolean pdfValido(ResultadoConsultaAdn resultado) {
        byte[] corpo = resultado.corpo();
        return resultado.statusCode() == 200
                && resultado.bytes() > 4
                && contem(resultado.contentType(), "pdf")
                && corpo[0] == '%'
                && corpo[1] == 'P'
                && corpo[2] == 'D'
                && corpo[3] == 'F';
    }
}
