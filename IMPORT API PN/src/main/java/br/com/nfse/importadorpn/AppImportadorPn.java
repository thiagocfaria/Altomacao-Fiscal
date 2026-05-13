package br.com.nfse.importadorpn;

import br.com.nfse.importadorpn.agenda.AgendadorJanelas;
import br.com.nfse.importadorpn.agenda.ControleJanelaExecutada;
import br.com.nfse.importadorpn.agenda.ExecutorJanelas;
import br.com.nfse.importadorpn.agenda.ResultadoExecucaoJanelas;
import br.com.nfse.importadorpn.certificado.ContextoSslCertificado;
import br.com.nfse.importadorpn.certificado.ResolvedorSenhaCertificado;
import br.com.nfse.importadorpn.certificado.ResultadoCertificado;
import br.com.nfse.importadorpn.certificado.ResultadoSenhaCertificado;
import br.com.nfse.importadorpn.certificado.ValidadorCertificado;
import br.com.nfse.importadorpn.configuracao.CadastroImportacao;
import br.com.nfse.importadorpn.configuracao.EmpresaImportacao;
import br.com.nfse.importadorpn.configuracao.ErroValidacao;
import br.com.nfse.importadorpn.configuracao.LeitorPlanilhaFiscal;
import br.com.nfse.importadorpn.configuracao.ResultadoValidacao;
import br.com.nfse.importadorpn.configuracao.ValidadorCadastro;
import br.com.nfse.importadorpn.execucao.BloqueioExecucao;
import br.com.nfse.importadorpn.manutencao.ManutencaoRetencao;
import br.com.nfse.importadorpn.manutencao.ResultadoManutencao;
import br.com.nfse.importadorpn.portal.AmbienteAdn;
import br.com.nfse.importadorpn.portal.ClienteAdn;
import br.com.nfse.importadorpn.portal.ConsultaDfePortal;
import br.com.nfse.importadorpn.portal.ConsultaDfePortalComRetentativa;
import br.com.nfse.importadorpn.portal.ConsultaDanfseComRetentativa;
import br.com.nfse.importadorpn.portal.ConsultaAdnPlanejada;
import br.com.nfse.importadorpn.portal.DocumentoDfeExtraido;
import br.com.nfse.importadorpn.portal.ExecutorConsultaAdn;
import br.com.nfse.importadorpn.portal.ExtratorLoteDfeJson;
import br.com.nfse.importadorpn.portal.FaixaNsu;
import br.com.nfse.importadorpn.portal.PlanejadorConsultaAdn;
import br.com.nfse.importadorpn.portal.PoliticaSomenteLeitura;
import br.com.nfse.importadorpn.portal.ReconciliadorPortalDestino;
import br.com.nfse.importadorpn.portal.RegistroConsultaAdn;
import br.com.nfse.importadorpn.portal.ResultadoConsultaAdn;
import br.com.nfse.importadorpn.portal.ResultadoProcessamentoLote;
import br.com.nfse.importadorpn.portal.ResultadoRegistroConsulta;
import br.com.nfse.importadorpn.portal.ResultadoReconciliacaoPortal;
import br.com.nfse.importadorpn.portal.RoteadorDmsPorEmissao;
import br.com.nfse.importadorpn.prevoo.NivelPrevoo;
import br.com.nfse.importadorpn.prevoo.PrevooVerificarTudo;
import br.com.nfse.importadorpn.prevoo.ProblemaPrevoo;
import br.com.nfse.importadorpn.prevoo.ResultadoPrevoo;
import br.com.nfse.importadorpn.publicacao.PublicadorRestEntrada;
import br.com.nfse.importadorpn.publicacao.PublicadorRespostaAdn;
import br.com.nfse.importadorpn.publicacao.PublicadorDmsDireto;
import br.com.nfse.importadorpn.publicacao.RestEntradaPublicada;
import br.com.nfse.importadorpn.ledger.RepositorioImportacao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "importador-api-pn", mixinStandardHelpOptions = true,
        subcommands = {AppImportadorPn.ValidarCadastro.class, AppImportadorPn.ValidarCertificados.class,
                AppImportadorPn.PlanejarConsultaAdn.class, AppImportadorPn.ConsultarAdn.class,
                AppImportadorPn.CapturarAdn.class, AppImportadorPn.VarrerNsus.class,
                AppImportadorPn.PublicarRestSimulado.class,
                AppImportadorPn.SimularJanelas.class,
                AppImportadorPn.ExecutarJanelas.class,
                AppImportadorPn.ReconciliarCommand.class,
                AppImportadorPn.VerificarTudoCommand.class,
                AppImportadorPn.ManutencaoRetencaoCommand.class})
public final class AppImportadorPn implements Runnable {
    private static final int MAX_LOTES_RECONCILIACAO_PADRAO = 500;
    private static final int TENTATIVAS_PORTAL_PADRAO = 5;
    private static final long RETRY_PORTAL_MS_PADRAO = 2000L;

    public static void main(String[] args) {
        int exitCode = commandLine().execute(args);
        System.exit(exitCode);
    }

    static CommandLine commandLine() {
        return new CommandLine(new AppImportadorPn()).setExecutionExceptionHandler(
                (exception, commandLine, parseResult) -> {
                    commandLine.getErr().println("ERRO: " + errorMessage(exception));
                    return 2;
                });
    }

    private static String errorMessage(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @Command(name = "planejar-consulta-adn", description = "Mostra as consultas GET que seriam feitas no ADN, sem executar rede.")
    static final class PlanejarConsultaAdn implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--nsu", required = true, description = "NSU a consultar no dry-run.")
        String nsu;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO_RESTRITA",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Override
        public Integer call() throws Exception {
            CadastroImportacao cadastro = carregarCadastro(planilha, Optional.empty());
            ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - planejar-consulta-adn");
            System.out.println("Modo: DRY_RUN_SOMENTE_LEITURA");
            System.out.println("Ambiente: " + ambiente);
            if (!validacao.aprovado()) {
                System.out.println("Status: ATENCAO");
                int limite = Math.min(20, validacao.erros().size());
                for (ErroValidacao erro : validacao.erros().subList(0, limite)) {
                    System.out.println("- " + erro.origem() + ": " + erro.mensagem());
                }
                return 2;
            }
            PlanejadorConsultaAdn planejador = new PlanejadorConsultaAdn(
                    new ClienteAdn(ambiente, new PoliticaSomenteLeitura()));
            List<ConsultaAdnPlanejada> consultas = planejador.planejarDfePorNsu(cadastro.empresas(), nsu);
            for (ConsultaAdnPlanejada consulta : consultas) {
                System.out.println("- " + consulta.empresa() + " [" + consulta.cnpj() + "]");
                System.out.println("  " + consulta.metodo() + " " + consulta.uri());
            }
            System.out.println("Consultas planejadas: " + consultas.size());
            System.out.println("Rede executada: NAO");
            return 0;
        }
    }

    @Command(name = "consultar-adn", description = "Executa uma consulta GET real no ADN usando certificado A1.")
    static final class ConsultarAdn implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--nsu", required = true, description = "NSU a consultar.")
        String nsu;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO_RESTRITA",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Override
        public Integer call() throws Exception {
            CadastroImportacao cadastro = carregarCadastro(planilha, Optional.empty());
            ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - consultar-adn");
            System.out.println("Modo: SOMENTE_LEITURA");
            System.out.println("Ambiente: " + ambiente);
            if (!validacao.aprovado()) {
                System.out.println("Status: ATENCAO");
                int limite = Math.min(20, validacao.erros().size());
                for (ErroValidacao erro : validacao.erros().subList(0, limite)) {
                    System.out.println("- " + erro.origem() + ": " + erro.mensagem());
                }
                return 2;
            }
            if (cadastro.empresas().size() != 1) {
                System.out.println("Status: ATENCAO");
                System.out.println("Consulta real exige exatamente 1 empresa ativa. Ativas: " + cadastro.empresas().size());
                return 2;
            }
            EmpresaImportacao empresa = cadastro.empresas().get(0);
            ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado();
            String alias = empresa.certificadoAlias().orElse(empresa.cnpj());
            ResultadoSenhaCertificado senha = resolvedor.resolver(alias,
                    empresa.senhaCertificadoPlanilha().orElse(null));
            if (!senha.encontrada()) {
                System.out.println("Status: ATENCAO");
                System.out.println("Senha do certificado nao encontrada em " + senha.origem());
                return 2;
            }
            Path arquivoCertificado = empresa.certificadoPasta().orElseThrow()
                    .resolve(empresa.certificadoArquivo().orElseThrow());
            HttpRequest request = new ClienteAdn(ambiente, new PoliticaSomenteLeitura())
                    .consultarDfePorNsu(nsu, empresa.cnpj());
            System.out.println("- " + empresa.nome() + " [" + empresa.cnpj() + "]");
            System.out.println("  " + request.method() + " " + request.uri());
            ResultadoConsultaAdn resultado = new ExecutorConsultaAdn().executar(request,
                    new ContextoSslCertificado().criar(arquivoCertificado, senha.senha().orElseThrow().toCharArray()));
            System.out.println("Resultado: " + resultado.resumo());
            return 0;
        }
    }

    @Command(name = "capturar-adn",
            description = "LEGADO: consulta o ADN diretamente. Para operacao normal use reconciliar.")
    static final class CapturarAdn implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--backend", required = true, description = "Pasta backend tecnica do IMPORT API PN.")
        Path backend;

        @Option(names = "--nsu", required = true, description = "NSU a consultar.")
        String nsu;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO_RESTRITA",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Option(names = "--mes", description = "Mes de trabalho no formato AAAA-MM. Padrao: mes atual.")
        YearMonth mes;

        @Option(names = "--recompor-pdf",
                description = "LEGADO: tenta baixar DANFSe novamente quando o PDF tecnico esperado nao existe.")
        boolean recomporPdf;

        @Override
        public Integer call() throws Exception {
            YearMonth mesTrabalho = mes == null ? YearMonth.now() : mes;
            CadastroImportacao cadastro = carregarCadastro(planilha, Optional.of(mesTrabalho));
            ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - capturar-adn");
            System.out.println("Modo: SOMENTE_LEITURA_COM_LEDGER");
            System.out.println("Ambiente: " + ambiente);
            if (!validacao.aprovado()) {
                System.out.println("Status: ATENCAO");
                int limite = Math.min(20, validacao.erros().size());
                for (ErroValidacao erro : validacao.erros().subList(0, limite)) {
                    System.out.println("- " + erro.origem() + ": " + erro.mensagem());
                }
                return 2;
            }
            ResultadoCaptura resultado = capturarEmpresas(cadastro, backend, nsu, ambiente, mesTrabalho, recomporPdf);
            System.out.println("Empresas processadas: " + resultado.empresasProcessadas());
            System.out.println("Documentos encontrados: " + resultado.documentosPublicados());
            return 0;
        }
    }

    @Command(name = "varrer-nsus",
            description = "Consulta uma faixa pequena de NSUs em modo somente leitura, com ledger e parada em HTTP 200.")
    static final class VarrerNsus implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--backend", required = true, description = "Pasta backend tecnica do IMPORT API PN.")
        Path backend;

        @Option(names = "--inicio", required = true, description = "Primeiro NSU da faixa.")
        long inicio;

        @Option(names = "--fim", required = true, description = "Ultimo NSU da faixa. Limite: 50 NSUs por rodada.")
        long fim;

        @Option(names = "--intervalo-ms", defaultValue = "1000",
                description = "Pausa entre consultas. Minimo operacional: 500 ms.")
        long intervaloMs;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO_RESTRITA",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Option(names = "--mes", description = "Mes de trabalho no formato AAAA-MM. Padrao: mes atual.")
        YearMonth mes;

        @Option(names = "--continuar-apos-200", description = "Continua a faixa mesmo depois de encontrar documento.")
        boolean continuarApos200;

        @Override
        public Integer call() throws Exception {
            YearMonth mesTrabalho = mes == null ? YearMonth.now() : mes;
            CadastroImportacao cadastro = carregarCadastro(planilha, Optional.of(mesTrabalho));
            ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - varrer-nsus");
            System.out.println("Modo: SOMENTE_LEITURA_COM_LEDGER");
            System.out.println("Ambiente: " + ambiente);
            if (!validacao.aprovado()) {
                System.out.println("Status: ATENCAO");
                int limite = Math.min(20, validacao.erros().size());
                for (ErroValidacao erro : validacao.erros().subList(0, limite)) {
                    System.out.println("- " + erro.origem() + ": " + erro.mensagem());
                }
                return 2;
            }
            if (cadastro.empresas().size() != 1) {
                System.out.println("Status: ATENCAO");
                System.out.println("Varredura real exige exatamente 1 empresa ativa. Ativas: " + cadastro.empresas().size());
                return 2;
            }
            if (cadastro.entradaRest().isEmpty()) {
                System.out.println("Status: ATENCAO");
                System.out.println("Entrada REST global nao encontrada na planilha.");
                return 2;
            }

            FaixaNsu faixa = new FaixaNsu(inicio, fim);
            long pausa = Math.max(500, intervaloMs);
            EmpresaImportacao empresa = cadastro.empresas().get(0);
            RegistroConsultaAdn registroConsulta = new RegistroConsultaAdn(
                    new RepositorioImportacao(backend.resolve("ledger"), mesTrabalho),
                    new PublicadorRespostaAdn(backend),
                    new PublicadorRestEntrada(cadastro.entradaRest().orElseThrow()),
                    new PublicadorDmsDireto(),
                    consultaDanfseComRetentativa(empresa, ambiente));

            int encontrados = 0;
            int consultados = 0;
            for (long nsuAtual : faixa.valores()) {
                if (consultados > 0) {
                    Thread.sleep(pausa);
                }
                String nsuTexto = Long.toString(nsuAtual);
                ResultadoConsultaAdn resultado;
                try {
                    resultado = consultar(empresa, nsuTexto, ambiente);
                } catch (HttpTimeoutException e) {
                    resultado = new ResultadoConsultaAdn(0, "erro-rede-timeout", new byte[0]);
                }
                ResultadoRegistroConsulta registro = registroConsulta.registrar(
                        empresa, nsuTexto, resultado, mesTrabalho, Instant.now());
                consultados++;
                System.out.println("NSU " + nsuTexto + ": " + resultado.resumo());
                if (registro.publicado()) {
                    encontrados += registro.documentosPublicados();
                    System.out.println("  Resposta tecnica salva: " + registro.caminhoTecnico());
                    System.out.println("  Entrada REST publicada: " + registro.caminhoPublicado());
                    if (registro.documentosPublicados() > 1) {
                        System.out.println("  Documentos XML publicados: " + registro.documentosPublicados());
                    }
                    if (!continuarApos200) {
                        break;
                    }
                } else if (!registro.caminhoTecnico().isBlank()) {
                    System.out.println("  Resposta tecnica salva: " + registro.caminhoTecnico());
                }
            }
            System.out.println("Consultados: " + consultados);
            System.out.println("Documentos encontrados: " + encontrados);
            return 0;
        }
    }

    @Command(name = "publicar-rest-simulado",
            description = "Publica um XML/PDF local na entrada-rest global para validar a etapa sem chamar o ADN.")
    static final class PublicarRestSimulado implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--arquivo", required = true, description = "XML ou PDF local a simular como resposta ADN 200.")
        Path arquivo;

        @Option(names = "--cnpj", required = true, description = "CNPJ da empresa da nota.")
        String cnpj;

        @Option(names = "--nsu", required = true, description = "NSU simulado.")
        String nsu;

        @Option(names = "--mes", description = "Mes de trabalho no formato AAAA-MM. Padrao: mes atual.")
        YearMonth mes;

        @Option(names = "--content-type", description = "Content-type simulado. Padrao: inferido pela extensao.")
        String contentType;

        @Override
        public Integer call() throws Exception {
            YearMonth mesTrabalho = mes == null ? YearMonth.now() : mes;
            CadastroImportacao cadastro = carregarCadastro(planilha, Optional.of(mesTrabalho));
            ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - publicar-rest-simulado");
            System.out.println("Modo: SEM_REDE");
            if (!validacao.aprovado()) {
                System.out.println("Status: ATENCAO");
                int limite = Math.min(20, validacao.erros().size());
                for (ErroValidacao erro : validacao.erros().subList(0, limite)) {
                    System.out.println("- " + erro.origem() + ": " + erro.mensagem());
                }
                return 2;
            }
            if (cadastro.entradaRest().isEmpty()) {
                System.out.println("Status: ATENCAO");
                System.out.println("Entrada REST global nao encontrada na planilha.");
                return 2;
            }
            String tipo = contentType == null || contentType.isBlank() ? inferirContentType(arquivo) : contentType;
            ResultadoConsultaAdn respostaSimulada = new ResultadoConsultaAdn(200, tipo, Files.readAllBytes(arquivo));
            RestEntradaPublicada publicada = new PublicadorRestEntrada(cadastro.entradaRest().orElseThrow())
                    .publicar(cnpj, nsu, mesTrabalho, respostaSimulada);

            System.out.println("Entrada REST: " + cadastro.entradaRest().orElseThrow());
            System.out.println("Arquivo publicado: " + publicada.caminho());
            System.out.println("Bytes: " + publicada.bytes());
            System.out.println("Ja existia: " + (publicada.jaExistia() ? "SIM" : "NAO"));
            return 0;
        }

        private static String inferirContentType(Path arquivo) {
            String nome = arquivo.getFileName().toString().toLowerCase();
            if (nome.endsWith(".xml")) {
                return "application/xml";
            }
            if (nome.endsWith(".pdf")) {
                return "application/pdf";
            }
            return "application/octet-stream";
        }
    }

    private static ResultadoConsultaAdn consultar(EmpresaImportacao empresa, String nsu, AmbienteAdn ambiente)
            throws Exception {
        return consultar(empresa, nsu, ambiente, Duration.ofSeconds(30));
    }

    private static ResultadoConsultaAdn consultar(EmpresaImportacao empresa, String nsu, AmbienteAdn ambiente,
                                                  Duration timeout) throws Exception {
        ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado();
        String alias = empresa.certificadoAlias().orElse(empresa.cnpj());
        ResultadoSenhaCertificado senha = resolvedor.resolver(alias,
                empresa.senhaCertificadoPlanilha().orElse(null));
        if (!senha.encontrada()) {
            throw new IllegalStateException("Senha do certificado nao encontrada em " + senha.origem());
        }
        Path arquivoCertificado = empresa.certificadoPasta().orElseThrow()
                .resolve(empresa.certificadoArquivo().orElseThrow());
        HttpRequest request = new ClienteAdn(ambiente, new PoliticaSomenteLeitura(timeout))
                .consultarDfePorNsu(nsu, empresa.cnpj());
        System.out.println("- " + empresa.nome() + " [" + empresa.cnpj() + "]");
        System.out.println("  " + request.method() + " " + request.uri());
        return new ExecutorConsultaAdn().executar(request,
                new ContextoSslCertificado().criar(arquivoCertificado, senha.senha().orElseThrow().toCharArray()));
    }

    private static ConsultaDfePortal consultaDfePortalComRetentativa(AmbienteAdn ambiente, Duration timeout,
            int tentativasPortal, long retryPortalMs) {
        Duration intervalo = Duration.ofMillis(Math.max(0L, retryPortalMs));
        return new ConsultaDfePortalComRetentativa(
                (empresa, nsuTexto) -> consultar(empresa, nsuTexto, ambiente, timeout),
                tentativasPortal,
                intervalo);
    }

    private static CadastroImportacao carregarCadastro(Path planilha, Optional<YearMonth> mes) throws Exception {
        LeitorPlanilhaFiscal leitor = new LeitorPlanilhaFiscal();
        if (mes.isPresent()) {
            return leitor.ler(planilha, mes.orElseThrow());
        }
        return leitor.ler(planilha);
    }

    private static ResultadoCaptura capturarEmpresas(CadastroImportacao cadastro, Path backend, String nsu,
                                                     AmbienteAdn ambiente, YearMonth mesTrabalho) throws Exception {
        return capturarEmpresas(cadastro, backend, nsu, ambiente, mesTrabalho, false);
    }

    private static ResultadoCaptura capturarEmpresas(CadastroImportacao cadastro, Path backend, String nsu,
                                                     AmbienteAdn ambiente, YearMonth mesTrabalho,
                                                     boolean recomporPdf) throws Exception {
        int empresasProcessadas = 0;
        int documentosPublicados = 0;
        for (EmpresaImportacao empresa : cadastro.empresas()) {
            ResultadoConsultaAdn resultado = consultar(empresa, nsu, ambiente);
            ResultadoRegistroConsulta registro = new RegistroConsultaAdn(
                    new RepositorioImportacao(backend.resolve("ledger"), mesTrabalho),
                    new PublicadorRespostaAdn(backend),
                    new PublicadorRestEntrada(cadastro.entradaRest().orElseThrow()),
                    new PublicadorDmsDireto(),
                    consultaDanfseComRetentativa(empresa, ambiente),
                    recomporPdf)
                    .registrar(empresa, nsu, resultado, mesTrabalho, Instant.now());

            empresasProcessadas++;
            documentosPublicados += registro.documentosPublicados();
            System.out.println("Resultado: " + resultado.resumo());
            if (registro.publicado()) {
                System.out.println("Resposta tecnica salva: " + registro.caminhoTecnico());
                System.out.println("Entrada REST publicada: " + registro.caminhoPublicado());
                if (registro.documentosPublicados() > 1) {
                    System.out.println("Documentos XML publicados: " + registro.documentosPublicados());
                }
            } else if (registro.falhaRegistrada()) {
                System.out.println("Ledger atualizado: tentativa registrada, sem marcar como importado.");
            }
        }
        return new ResultadoCaptura(empresasProcessadas, documentosPublicados);
    }

    private record ResultadoCaptura(int empresasProcessadas, int documentosPublicados) {
    }

    private record ResultadoReconciliacao(int empresasProcessadas, int documentosPublicados) {
    }

    private static ResultadoConsultaAdn consultarDanfse(EmpresaImportacao empresa, String chaveAcesso,
            AmbienteAdn ambiente) throws Exception {
        if (chaveAcesso == null || chaveAcesso.isBlank()) {
            return new ResultadoConsultaAdn(0, "erro-chave-ausente", new byte[0]);
        }
        ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado();
        String alias = empresa.certificadoAlias().orElse(empresa.cnpj());
        ResultadoSenhaCertificado senha = resolvedor.resolver(alias,
                empresa.senhaCertificadoPlanilha().orElse(null));
        if (!senha.encontrada()) {
            throw new IllegalStateException("Senha do certificado nao encontrada em " + senha.origem());
        }
        Path arquivoCertificado = empresa.certificadoPasta().orElseThrow()
                .resolve(empresa.certificadoArquivo().orElseThrow());
        HttpRequest request = new ClienteAdn(ambiente, new PoliticaSomenteLeitura())
                .consultarDanfsePorChave(chaveAcesso);
        System.out.println("  " + request.method() + " " + request.uri());
        return new ExecutorConsultaAdn().executar(request,
                new ContextoSslCertificado().criar(arquivoCertificado, senha.senha().orElseThrow().toCharArray()));
    }

    private static ConsultaDanfseComRetentativa consultaDanfseComRetentativa(EmpresaImportacao empresa,
            AmbienteAdn ambiente) {
        // 1 tentativa apenas - sem retry interno. PDFs que falham sao detectados como
        // faltantes pelo proximo reconciliar (a cada 60s no loop do painel) e tentados
        // de novo. A persistencia esta toda no LOOP, nao em retries internos. Isso mantem
        // cada chamada rapida (~5s no caso pior), evitando sobreposicao de execucoes.
        return new ConsultaDanfseComRetentativa(
                documento -> Optional.of(consultarDanfse(empresa, documento.chaveAcesso(), ambiente)),
                1,
                Duration.ofSeconds(1));
    }

    private static ResultadoReconciliacao reconciliarCadastro(CadastroImportacao cadastro, CadastroImportacao cadastroTodosMeses,
            Path backend, String nsu,
            AmbienteAdn ambiente, YearMonth mesTrabalho) throws Exception {
        return reconciliarCadastro(cadastro, cadastroTodosMeses, backend, nsu, ambiente, mesTrabalho,
                MAX_LOTES_RECONCILIACAO_PADRAO, TENTATIVAS_PORTAL_PADRAO, RETRY_PORTAL_MS_PADRAO);
    }

    private static ResultadoReconciliacao reconciliarCadastro(CadastroImportacao cadastro, CadastroImportacao cadastroTodosMeses,
            Path backend, String nsu,
            AmbienteAdn ambiente, YearMonth mesTrabalho, int maxLotes) throws Exception {
        return reconciliarCadastro(cadastro, cadastroTodosMeses, backend, nsu, ambiente, mesTrabalho,
                maxLotes, TENTATIVAS_PORTAL_PADRAO, RETRY_PORTAL_MS_PADRAO);
    }

    private static ResultadoReconciliacao reconciliarCadastro(CadastroImportacao cadastro, CadastroImportacao cadastroTodosMeses,
            Path backend, String nsu,
            AmbienteAdn ambiente, YearMonth mesTrabalho, int maxLotes,
            int tentativasPortal, long retryPortalMs) throws Exception {
        RoteadorDmsPorEmissao roteadorDms = new RoteadorDmsPorEmissao(cadastroTodosMeses, mesTrabalho.getYear());
        ConsultaDfePortal consultaPortal = consultaDfePortalComRetentativa(
                ambiente, Duration.ofSeconds(30), tentativasPortal, retryPortalMs);
        System.out.println("Tentativas Portal por NSU: " + Math.max(1, tentativasPortal));
        System.out.println("Retry Portal base ms: " + Math.max(0L, retryPortalMs));
        ResultadoReconciliacaoPortal resultado = new ReconciliadorPortalDestino().executar(
                cadastro,
                mesTrabalho,
                nsu,
                maxLotes,
                consultaPortal,
                (empresa, nsuTexto, consulta, estadoDestino, documentos, agora) -> {
                    RegistroConsultaAdn registroConsulta = new RegistroConsultaAdn(
                            new RepositorioImportacao(backend.resolve("ledger"), mesTrabalho),
                            new PublicadorRespostaAdn(backend),
                            new PublicadorRestEntrada(cadastro.entradaRest().orElseThrow()),
                            new PublicadorDmsDireto(),
                            consultaDanfseComRetentativa(empresa, ambiente),
                            true,
                            roteadorDms);
                    ResultadoRegistroConsulta registro = registroConsulta
                            .registrar(empresa, nsuTexto, consulta, mesTrabalho, agora, estadoDestino);
                    return ResultadoProcessamentoLote.importados(registro.documentosPublicados());
                },
                Instant.now());

        for (var empresa : resultado.empresas()) {
            System.out.println("- " + empresa.empresa() + " [" + empresa.cnpj() + "]");
            System.out.println("  Chaves completas no destino: " + empresa.chavesCompletasDestino());
            System.out.println("  XML REST=" + empresa.chavesXmlRest()
                    + " PDF REST=" + empresa.chavesPdfRest()
                    + " DMS=" + empresa.chavesDms());
            System.out.println("  Lotes consultados: " + empresa.lotesConsultados());
            System.out.println("  Re-importados: " + empresa.processamento().documentosAfetados());
            if (empresa.truncadoPorMaxLotes()) {
                System.out.println("  ATENCAO: max-lotes atingido antes de parada natural.");
            }
            if (empresa.erroExternoPortal()) {
                System.out.println("  ERRO_EXTERNO Portal: " + empresa.erroExternoPortalMensagem());
            }
        }
        return new ResultadoReconciliacao(resultado.empresasProcessadas(), resultado.totalDocumentosAfetados());
    }

    @Command(name = "validar-certificados", description = "Abre certificados PKCS12 da planilha usando senha por variavel de ambiente.")
    static final class ValidarCertificados implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Override
        public Integer call() throws Exception {
            CadastroImportacao cadastro = new LeitorPlanilhaFiscal().ler(planilha);
            ResolvedorSenhaCertificado resolvedor = new ResolvedorSenhaCertificado();
            ValidadorCertificado validador = new ValidadorCertificado();
            int ok = 0;
            int falhas = 0;
            int configurados = 0;
            System.out.println("IMPORT API PN - validar-certificados");
            for (EmpresaImportacao empresa : cadastro.empresas()) {
                if (empresa.certificadoPasta().isEmpty() || empresa.certificadoArquivo().isEmpty()) {
                    continue;
                }
                configurados++;
                String alias = empresa.certificadoAlias().orElse(empresa.cnpj());
                ResultadoSenhaCertificado senha = resolvedor.resolver(alias,
                        empresa.senhaCertificadoPlanilha().orElse(null));
                Path arquivo = empresa.certificadoPasta().get().resolve(empresa.certificadoArquivo().get());
                if (!senha.encontrada()) {
                    falhas++;
                    System.out.println("- " + empresa.nome() + ": senha nao encontrada em " + senha.origem());
                    continue;
                }
                ResultadoCertificado resultado = validador.validar(arquivo, senha.senha().orElseThrow().toCharArray());
                if (resultado.valido()
                        && resultado.cnpjs().contains(empresa.cnpj())) {
                    ok++;
                    String validade = resultado.venceEm().map(Object::toString).orElse("validade nao lida");
                    System.out.println("- " + empresa.nome() + ": OK (" + resultado.certificados()
                            + " certificado(s), " + validade + ", CNPJ " + empresa.cnpj() + ")");
                } else {
                    falhas++;
                    String detalheCnpj = resultado.valido()
                            ? "CNPJ certificado=" + resultado.cnpjs() + ", CNPJ planilha=" + empresa.cnpj()
                            : resultado.mensagem();
                    System.out.println("- " + empresa.nome() + ": ATENCAO - " + detalheCnpj);
                }
            }
            if (configurados == 0) {
                System.out.println("Nenhum certificado configurado na planilha.");
                return 2;
            }
            System.out.println("Certificados OK: " + ok);
            System.out.println("Certificados com atencao: " + falhas);
            return falhas == 0 ? 0 : 2;
        }
    }

    @Command(name = "simular-janelas", description = "Executa dry-run das janelas pendentes sem chamar API real.")
    static final class SimularJanelas implements Callable<Integer> {
        @Option(names = "--backend", required = true, description = "Pasta backend do IMPORT API PN.")
        Path backend;

        @Option(names = "--agora", description = "Data/hora para simulacao, formato AAAA-MM-DDTHH:MM.")
        LocalDateTime agora;

        @Override
        public Integer call() throws Exception {
            LocalDateTime referencia = agora == null ? LocalDateTime.now() : agora;
            ControleJanelaExecutada controle = new ControleJanelaExecutada(backend.resolve("agenda"));
            AgendadorJanelas agendador = new AgendadorJanelas(controle);
            ExecutorJanelas executor = new ExecutorJanelas(agendador, controle,
                    new BloqueioExecucao(backend.resolve("importador-api-pn.lock")),
                    janela -> System.out.println("Dry-run janela: " + janela.identificador()));
            ResultadoExecucaoJanelas resultado = executor.simularPendentes(referencia);
            System.out.println("IMPORT API PN - simular-janelas");
            if (resultado.lockOcupado()) {
                System.out.println("Status: LOCK_OCUPADO");
                return 3;
            }
            System.out.println("Referencia: " + referencia);
            System.out.println("Janelas pendentes: " + resultado.pendentes());
            System.out.println("Janelas executadas em dry-run: " + resultado.executadas());
            return 0;
        }
    }

    @Command(name = "executar-janelas", mixinStandardHelpOptions = true,
            description = "Executa as janelas pendentes reais do dia usando os horarios operacionais.")
    static final class ExecutarJanelas implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--backend", required = true, description = "Pasta backend do IMPORT API PN.")
        Path backend;

        @Option(names = "--nsu", defaultValue = "1", description = "NSU inicial da janela. Padrao: 1.")
        String nsu;

        @Option(names = "--max-lotes", defaultValue = "500",
                description = "Quantidade maxima de lotes ADN por empresa em cada janela.")
        int maxLotes;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO_RESTRITA",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Option(names = "--agora", description = "Data/hora para execucao, formato AAAA-MM-DDTHH:MM.")
        LocalDateTime agora;

        @Override
        public Integer call() throws Exception {
            LocalDateTime referencia = agora == null ? LocalDateTime.now() : agora;
            ControleJanelaExecutada controle = new ControleJanelaExecutada(backend.resolve("agenda"));
            AgendadorJanelas agendador = new AgendadorJanelas(controle);
            ExecutorJanelas executor = new ExecutorJanelas(agendador, controle,
                    new BloqueioExecucao(backend.resolve("importador-api-pn.lock")),
                    janela -> {
                        YearMonth mesJanela = janela.mes();
                        CadastroImportacao cadastro = carregarCadastro(planilha, Optional.of(mesJanela));
                        ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
                        if (!validacao.aprovado()) {
                            throw new IllegalStateException("Cadastro invalido para a janela "
                                    + janela.identificador() + ": " + validacao.erros());
                        }
                        CadastroImportacao cadastroTodosMeses = new LeitorPlanilhaFiscal().lerTodasAbas(planilha);
                        System.out.println("Executando janela: " + janela.identificador());
                        ResultadoReconciliacao resultado = reconciliarCadastro(cadastro, cadastroTodosMeses,
                                backend, nsu, ambiente, mesJanela, maxLotes);
                        System.out.println("Janela concluida: empresas=" + resultado.empresasProcessadas()
                                + " documentos=" + resultado.documentosPublicados());
                    });
            ResultadoExecucaoJanelas resultado = executor.executarPendentes(referencia);
            System.out.println("IMPORT API PN - executar-janelas");
            if (resultado.lockOcupado()) {
                System.out.println("Status: LOCK_OCUPADO");
                return 3;
            }
            System.out.println("Referencia: " + referencia);
            System.out.println("Janelas pendentes: " + resultado.pendentes());
            System.out.println("Janelas executadas: " + resultado.executadas());
            return 0;
        }
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "validar-cadastro", description = "Valida PLANILHA_FISCAL sem chamar API real.")
    static final class ValidarCadastro implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Override
        public Integer call() throws Exception {
            CadastroImportacao cadastro = new LeitorPlanilhaFiscal().ler(planilha);
            ResultadoValidacao resultado = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - validar-cadastro");
            System.out.println("Empresas ativas: " + resultado.totalEmpresas());
            System.out.println("Entrada REST: " + cadastro.entradaRest().map(Path::toString).orElse("NAO ENCONTRADA"));
            if (resultado.aprovado()) {
                System.out.println("Status: OK");
                return 0;
            }
            System.out.println("Status: ATENCAO");
            int limite = Math.min(50, resultado.erros().size());
            for (ErroValidacao erro : resultado.erros().subList(0, limite)) {
                System.out.println("- " + erro.origem() + ": " + erro.mensagem());
            }
            if (resultado.erros().size() > limite) {
                System.out.println("- ... mais " + (resultado.erros().size() - limite) + " erro(s) omitido(s)");
            }
            return 2;
        }
    }

    /**
     * Comando reconciliar: implementa a REGRA INVARIANTE #1.
     * A cada chamada (loop continuo a cada 60s no painel):
     *   1) Chama o Portal Nacional para cada empresa habilitada
     *   2) Escaneia o destino real do cliente (CAMINHO REST/XML + CAMINHO DMS)
     *   3) Para cada nota do Portal cuja chave NAO esta no destino: re-importa
     *   4) Para cada nota cuja chave esta no destino: pula
     *
     * NAO consulta o ledger interno para decidir. Decisao baseada exclusivamente
     * no estado real das pastas configuradas na planilha.
     */
    @Command(name = "reconciliar",
            description = "Compara Portal vs destino do cliente e re-importa o que falta. Nao usa ledger interno como autoridade.")
    static final class ReconciliarCommand implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--backend", required = true, description = "Pasta backend do IMPORT API PN.")
        Path backend;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Option(names = "--nsu", defaultValue = "1", description = "NSU inicial da varredura.")
        String nsu;

        @Option(names = "--max-lotes", defaultValue = "500",
                description = "Quantidade maxima de lotes ADN por empresa nesta reconciliacao.")
        int maxLotes;

        @Option(names = "--tentativas-portal", defaultValue = "5",
                description = "Tentativas por consulta DFe/NSU em erro temporario do Portal.")
        int tentativasPortal;

        @Option(names = "--retry-portal-ms", defaultValue = "2000",
                description = "Pausa base em milissegundos entre retentativas do Portal.")
        long retryPortalMs;

        @Option(names = "--mes", description = "Mes de trabalho AAAA-MM. Padrao: mes atual.")
        YearMonth mes;

        @Override
        public Integer call() throws Exception {
            YearMonth mesTrabalho = mes == null ? YearMonth.now() : mes;
            CadastroImportacao cadastro = new LeitorPlanilhaFiscal().ler(planilha, mesTrabalho);
            ResultadoValidacao validacao = new ValidadorCadastro().validar(cadastro);
            System.out.println("IMPORT API PN - reconciliar");
            if (!validacao.aprovado()) {
                System.out.println("Status: ATENCAO");
                int limite = Math.min(20, validacao.erros().size());
                for (ErroValidacao erro : validacao.erros().subList(0, limite)) {
                    System.out.println("- " + erro.origem() + ": " + erro.mensagem());
                }
                return 2;
            }
            CadastroImportacao cadastroTodosMeses = new LeitorPlanilhaFiscal().lerTodasAbas(planilha);
            ResultadoReconciliacao resultado = reconciliarCadastro(cadastro, cadastroTodosMeses,
                    backend, nsu, ambiente, mesTrabalho, maxLotes, tentativasPortal, retryPortalMs);
            System.out.println("Empresas processadas: " + resultado.empresasProcessadas());
            System.out.println("Documentos re-importados: " + resultado.documentosPublicados());
            return 0;
        }
    }

    @Command(name = "verificar-tudo",
            description = "Pre-voo fiel do sistema: valida localmente, consulta Portal e simula reconciliar sem importar.")
    static final class VerificarTudoCommand implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Caminho da PLANILHA_FISCAL.xlsm")
        Path planilha;

        @Option(names = "--backend", required = true, description = "Pasta backend do IMPORT API PN.")
        Path backend;

        @Option(names = "--mes", description = "Mes de atuacao AAAA-MM. Padrao: mes atual.")
        YearMonth mes;

        @Option(names = "--ambiente", defaultValue = "PRODUCAO",
                description = "PRODUCAO_RESTRITA ou PRODUCAO.")
        AmbienteAdn ambiente;

        @Option(names = "--nsu", defaultValue = "1", description = "NSU inicial que sera usado ao ligar.")
        String nsu;

        @Option(names = "--max-lotes", defaultValue = "500",
                description = "Quantidade maxima de lotes que sera usada ao ligar.")
        int maxLotes;

        @Option(names = "--timeout-segundos", defaultValue = "30",
                description = "Timeout por chamada real ao ADN durante o pre-voo.")
        long timeoutSegundos;

        @Option(names = "--tentativas-portal", defaultValue = "5",
                description = "Tentativas por consulta DFe/NSU em erro temporario do Portal.")
        int tentativasPortal;

        @Option(names = "--retry-portal-ms", defaultValue = "2000",
                description = "Pausa base em milissegundos entre retentativas do Portal.")
        long retryPortalMs;

        @Override
        public Integer call() throws Exception {
            YearMonth mesTrabalho = mes == null ? YearMonth.now() : mes;
            CadastroImportacao cadastro = new LeitorPlanilhaFiscal().ler(planilha, mesTrabalho);
            CadastroImportacao cadastroTodosMeses = new LeitorPlanilhaFiscal().lerTodasAbas(planilha);
            Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSegundos));
            ConsultaDfePortal consultaPortal = consultaDfePortalComRetentativa(
                    ambiente, timeout, tentativasPortal, retryPortalMs);
            ResultadoPrevoo resultado = new PrevooVerificarTudo(consultaPortal)
                    .verificar(cadastro, cadastroTodosMeses, mesTrabalho, backend, nsu, maxLotes);
            System.out.println("IMPORT API PN - verificar-tudo");
            System.out.println("NIVEL: " + resultado.nivel());
            System.out.println("Mes de atuacao: " + mesTrabalho);
            System.out.println("Ambiente: " + ambiente);
            System.out.println("NSU inicial ao ligar: " + nsu);
            System.out.println("Max lotes ao ligar: " + maxLotes);
            System.out.println("Tentativas Portal por NSU: " + Math.max(1, tentativasPortal));
            System.out.println("Retry Portal base ms: " + Math.max(0L, retryPortalMs));
            for (String info : resultado.informacoes()) {
                System.out.println(info);
            }
            for (ProblemaPrevoo problema : resultado.problemas()) {
                System.out.println("NIVEL: " + problema.nivel());
                System.out.println("Problema: " + problema.mensagem());
                System.out.println("Onde corrigir: " + problema.ondeCorrigir());
                System.out.println("Acao: " + problema.acao());
            }
            if (resultado.nivel() == NivelPrevoo.OK) {
                System.out.println("PODE LIGAR? SIM");
                System.out.println("TUDO OK - no momento da verificacao, o sistema esta pronto para ligar.");
                return 0;
            }
            if (resultado.nivel() == NivelPrevoo.ATENCAO) {
                System.out.println("PODE LIGAR? SIM, COM ATENCAO");
                return 1;
            }
            if (resultado.nivel() == NivelPrevoo.ERRO_EXTERNO) {
                System.out.println("PODE LIGAR? NAO");
                return 4;
            }
            System.out.println("PODE LIGAR? NAO");
            return 2;
        }
    }

    @Command(name = "manutencao-retencao", description = "Compacta logs antigos e remove ledgers mensais fora da retencao.")
    static final class ManutencaoRetencaoCommand implements Callable<Integer> {
        @Option(names = "--backend", required = true, description = "Pasta backend do IMPORT API PN.")
        Path backend;

        @Option(names = "--dias-log", defaultValue = "30", description = "Dias de log .jsonl sem compactar.")
        int diasLog;

        @Option(names = "--meses-ledger", defaultValue = "6", description = "Quantidade de ledgers mensais a manter.")
        int mesesLedger;

        @Override
        public Integer call() throws Exception {
            ResultadoManutencao resultado = new ManutencaoRetencao(backend)
                    .executar(LocalDate.now(), diasLog, mesesLedger);
            System.out.println("IMPORT API PN - manutencao-retencao");
            System.out.println("Logs compactados: " + resultado.logsCompactados());
            System.out.println("Ledgers removidos: " + resultado.ledgersRemovidos());
            return 0;
        }
    }
}
