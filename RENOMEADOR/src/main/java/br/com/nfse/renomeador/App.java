package br.com.nfse.renomeador;

import br.com.nfse.renomeador.app.BatchModeRunner;
import br.com.nfse.renomeador.app.ApplicationLock;
import br.com.nfse.renomeador.app.WatchModeRunner;
import br.com.nfse.renomeador.config.CompanyRegistryLoader;
import br.com.nfse.renomeador.config.CompanyRegistryValidator;
import br.com.nfse.renomeador.config.excel.ExcelCompanyImporter;
import br.com.nfse.renomeador.config.excel.ExcelWorkbookPreparer;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;
import br.com.nfse.renomeador.pipeline.TechnicalRetentionPolicy;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;
import java.util.concurrent.Callable;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        int exitCode = commandLine().execute(args);
        System.exit(exitCode);
    }

    public static CommandLine commandLine() {
        return new CommandLine(new Cli()).setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
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

    @Command(
            name = "renomeador-nfse",
            mixinStandardHelpOptions = true,
            version = "0.1.0-SNAPSHOT",
            description = "Renomeador operacional de PDFs/XMLs de NFS-e.",
            subcommands = {BatchCommand.class, WatchCommand.class, ConfigCommand.class, MaintenanceCommand.class}
    )
    public static final class Cli implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    public abstract static class BaseCommand {
        @Option(names = "--config", required = true, description = "Arquivo empresas.yaml.")
        Path config;

        @Option(names = "--empresa", description = "ID da empresa a processar.")
        String companyId;

        @Option(names = "--mes", description = "Mes no formato AAAA-MM para estrategia informado.")
        String month;

        @Option(names = "--homologacao", description = "Preserva os PDFs/XMLs de entrada.")
        boolean homologation;

        @Option(names = "--planilha", description = "Planilha Excel usada para atualizar o empresas.yaml antes da execucao.")
        Path spreadsheet;

        @Option(names = "--sem-atualizar-planilha", description = "Usa o empresas.yaml existente sem procurar/importar PLANILHA_FISCAL.xlsm automaticamente.")
        boolean skipSpreadsheetRefresh;

        Optional<String> companyId() {
            return Optional.ofNullable(companyId).filter(value -> !value.isBlank());
        }

        Optional<YearMonth> month() {
            return Optional.ofNullable(month).filter(value -> !value.isBlank()).map(YearMonth::parse);
        }

        Optional<Path> spreadsheet() {
            if (skipSpreadsheetRefresh) {
                return Optional.empty();
            }
            if (spreadsheet != null) {
                return Optional.of(spreadsheet);
            }
            Path configParent = config == null ? null : config.toAbsolutePath().normalize().getParent();
            return findDefaultSpreadsheet(configParent);
        }

        private static Optional<Path> findDefaultSpreadsheet(Path configParent) {
            Path current = configParent;
            for (int depth = 0; depth < 3 && current != null; depth++) {
                Path defaultSpreadsheet = current.resolve("PLANILHA_FISCAL.xlsm");
                if (Files.isRegularFile(defaultSpreadsheet)) {
                    return Optional.of(defaultSpreadsheet);
                }
                current = current.getParent();
            }
            return Optional.empty();
        }
    }

    @Command(name = "batch", mixinStandardHelpOptions = true, description = "Executa uma passada unica.")
    public static final class BatchCommand extends BaseCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            try (ApplicationLock ignored = ApplicationLock.acquire(config)) {
                refreshFromSpreadsheetIfPresent(this);
                ProcessingSummary summary = new BatchModeRunner().run(config, companyId(), month(), homologation);
                System.out.printf("Processados=%d OK=%d Canceladas=%d Duplicadas=%d Corrigidos=%d Ignorados=%d Erros=%d%n",
                        summary.total(),
                        summary.count(br.com.nfse.renomeador.processing.ProcessingStatus.OK),
                        summary.count(br.com.nfse.renomeador.processing.ProcessingStatus.CANCELLED),
                        summary.count(br.com.nfse.renomeador.processing.ProcessingStatus.DUPLICATE),
                        summary.repaired(),
                        summary.skipped(),
                        summary.errors());
                return summary.errors() == 0 ? 0 : 2;
            }
        }
    }

    @Command(name = "watch", mixinStandardHelpOptions = true, description = "Observa pastas ativas continuamente.")
    public static final class WatchCommand extends BaseCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            try (ApplicationLock ignored = ApplicationLock.acquire(config)) {
                new WatchModeRunner().run(config, companyId(), month(), homologation, spreadsheet());
            }
            return 0;
        }
    }

    @Command(name = "config", mixinStandardHelpOptions = true, description = "Ferramentas de configuracao.",
            subcommands = {ImportExcelCommand.class, PrepareExcelCommand.class, CheckConfigCommand.class})
    public static final class ConfigCommand implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @Command(name = "manutencao", mixinStandardHelpOptions = true, description = "Rotinas tecnicas de manutencao.",
            subcommands = {CleanupTechnicalCommand.class})
    public static final class MaintenanceCommand implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @Command(name = "limpar-tecnicos", mixinStandardHelpOptions = true,
            description = "Compacta logs antigos, limpa split-work antigo e gera relatorio de revisar.")
    public static final class CleanupTechnicalCommand implements Callable<Integer> {
        @Option(names = "--backend", required = true, description = "Pasta backend tecnica do RENOMEADOR.")
        Path backend;

        @Override
        public Integer call() throws Exception {
            new TechnicalRetentionPolicy().applyBackendRoot(backend);
            System.out.printf("Manutencao tecnica aplicada em %s%n", backend);
            return 0;
        }
    }

    @Command(name = "import-excel", mixinStandardHelpOptions = true,
            description = "Importa planilha Excel para empresas.yaml.")
    public static final class ImportExcelCommand implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Arquivo .xlsx ou .xlsm.")
        Path spreadsheet;

        @Option(names = "--saida", required = true, description = "Arquivo empresas.yaml a gerar.")
        Path output;

        @Option(names = "--aba", description = "Nome da aba; se omitido, usa CADASTRO ou a planilha fiscal legada.")
        String sheet;

        @Option(names = "--mes", description = "Mes no formato AAAA-MM; se omitido, usa o mes atual.")
        String month;

        @Option(names = "--sobrescrever", description = "Sobrescreve o YAML de saida se ja existir.")
        boolean overwrite;

        @Override
        public Integer call() throws Exception {
            ExcelCompanyImporter importer = new ExcelCompanyImporter();
            int imported;
            boolean hasSheet = sheet != null && !sheet.isBlank();
            boolean hasMonth = month != null && !month.isBlank();
            if (hasSheet || hasMonth) {
                imported = importer.importToYaml(spreadsheet, output, sheet, overwrite,
                        Optional.ofNullable(month).filter(value -> !value.isBlank()).map(YearMonth::parse),
                        java.time.LocalDate.now());
            } else {
                imported = importer.importAllMonthsToYaml(spreadsheet, output, overwrite);
            }
            new CompanyRegistryValidator().validate(new CompanyRegistryLoader().load(output));
            System.out.printf("Empresas importadas=%d%n", imported);
            return 0;
        }
    }

    @Command(name = "preparar-planilha", mixinStandardHelpOptions = true,
            description = "Cria uma copia profissional da planilha fiscal com DASHBOARD, cadastros mensais e CONFIG.")
    public static final class PrepareExcelCommand implements Callable<Integer> {
        @Option(names = "--entrada", required = true, description = "Planilha .xlsx ou .xlsm original.")
        Path input;

        @Option(names = "--saida", required = true, description = "Planilha preparada a gerar.")
        Path output;

        @Override
        public Integer call() throws Exception {
            new ExcelWorkbookPreparer().prepare(input, output);
            System.out.printf("Planilha preparada=%s%n", output);
            return 0;
        }
    }

    @Command(name = "check", mixinStandardHelpOptions = true, description = "Valida empresas.yaml.")
    public static final class CheckConfigCommand implements Callable<Integer> {
        @Option(names = "--config", required = true, description = "Arquivo empresas.yaml.")
        Path config;

        @Override
        public Integer call() throws Exception {
            new CompanyRegistryValidator().validate(new CompanyRegistryLoader().load(config));
            System.out.println("Configuracao valida");
            return 0;
        }
    }

    private static void refreshFromSpreadsheetIfPresent(BaseCommand command) throws Exception {
        if (command.spreadsheet().isEmpty()) return;
        Path planilha = command.spreadsheet().orElseThrow();
        if (command.month().isPresent()) {
            new ExcelCompanyImporter().importToYaml(planilha, command.config, "", true,
                    command.month(), java.time.LocalDate.now());
        } else {
            new ExcelCompanyImporter().importAllMonthsToYaml(planilha, command.config, true);
        }
    }
}
