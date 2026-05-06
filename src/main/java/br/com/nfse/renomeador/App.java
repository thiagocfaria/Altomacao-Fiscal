package br.com.nfse.renomeador;

import br.com.nfse.renomeador.app.BatchModeRunner;
import br.com.nfse.renomeador.app.WatchModeRunner;
import br.com.nfse.renomeador.config.CompanyRegistryLoader;
import br.com.nfse.renomeador.config.CompanyRegistryValidator;
import br.com.nfse.renomeador.config.excel.ExcelCompanyImporter;
import br.com.nfse.renomeador.config.excel.ExcelWorkbookPreparer;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Optional;
import java.util.concurrent.Callable;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Cli()).execute(args);
        System.exit(exitCode);
    }

    @Command(
            name = "renomeador-nfse",
            mixinStandardHelpOptions = true,
            version = "0.1.0-SNAPSHOT",
            description = "Renomeador operacional de PDFs de NFS-e.",
            subcommands = {BatchCommand.class, WatchCommand.class, ConfigCommand.class}
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

        @Option(names = "--homologacao", description = "Preserva os PDFs de entrada.")
        boolean homologation;

        Optional<String> companyId() {
            return Optional.ofNullable(companyId).filter(value -> !value.isBlank());
        }

        Optional<YearMonth> month() {
            return Optional.ofNullable(month).filter(value -> !value.isBlank()).map(YearMonth::parse);
        }
    }

    @Command(name = "batch", mixinStandardHelpOptions = true, description = "Executa uma passada unica.")
    public static final class BatchCommand extends BaseCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            ProcessingSummary summary = new BatchModeRunner().run(config, companyId(), month(), homologation);
            System.out.printf("Processados=%d OK=%d Canceladas=%d Duplicadas=%d Ignorados=%d Erros=%d%n",
                    summary.total(),
                    summary.count(br.com.nfse.renomeador.processing.ProcessingStatus.OK),
                    summary.count(br.com.nfse.renomeador.processing.ProcessingStatus.CANCELLED),
                    summary.count(br.com.nfse.renomeador.processing.ProcessingStatus.DUPLICATE),
                    summary.skipped(),
                    summary.errors());
            return summary.errors() == 0 ? 0 : 2;
        }
    }

    @Command(name = "watch", mixinStandardHelpOptions = true, description = "Observa pastas ativas continuamente.")
    public static final class WatchCommand extends BaseCommand implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            new WatchModeRunner().run(config, companyId(), month(), homologation);
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

    @Command(name = "import-excel", mixinStandardHelpOptions = true,
            description = "Importa planilha Excel para empresas.yaml.")
    public static final class ImportExcelCommand implements Callable<Integer> {
        @Option(names = "--planilha", required = true, description = "Arquivo .xlsx ou .xlsm.")
        Path spreadsheet;

        @Option(names = "--saida", required = true, description = "Arquivo empresas.yaml a gerar.")
        Path output;

        @Option(names = "--aba", description = "Nome da aba; se omitido, usa a primeira.")
        String sheet;

        @Option(names = "--sobrescrever", description = "Sobrescreve o YAML de saida se ja existir.")
        boolean overwrite;

        @Override
        public Integer call() throws Exception {
            int imported = new ExcelCompanyImporter().importToYaml(spreadsheet, output, sheet, overwrite);
            new CompanyRegistryValidator().validate(new CompanyRegistryLoader().load(output));
            System.out.printf("Empresas importadas=%d%n", imported);
            return 0;
        }
    }

    @Command(name = "preparar-planilha", mixinStandardHelpOptions = true,
            description = "Cria uma copia profissional da planilha fiscal para preencher CAMINHO REST.")
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
}
