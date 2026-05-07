package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.config.excel.ExcelCompanyImporter;
import br.com.nfse.renomeador.pipeline.InputScanner;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;
import br.com.nfse.renomeador.pipeline.ProcessingLogger;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileLockInterruptionException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class WatchModeRunner {
    private static final long CONFIG_RELOAD_POLL_MILLIS = 500L;
    private static final FileTime NO_SPREADSHEET = FileTime.fromMillis(-1L);

    private final RuntimeCompanyPaths companyPaths;
    private final WatchFolderProcessor processor;
    private final ExcelCompanyImporter excelImporter;

    public WatchModeRunner() {
        this(new RuntimeCompanyPaths(), new WatchFolderProcessor());
    }

    WatchModeRunner(RuntimeCompanyPaths companyPaths, InvoiceProcessingPipeline pipeline, ProcessingLogger logger) {
        this(companyPaths, new WatchFolderProcessor(new InputScanner(), pipeline, logger));
    }

    WatchModeRunner(RuntimeCompanyPaths companyPaths, WatchFolderProcessor processor) {
        this(companyPaths, processor, new ExcelCompanyImporter());
    }

    WatchModeRunner(RuntimeCompanyPaths companyPaths, WatchFolderProcessor processor,
                    ExcelCompanyImporter excelImporter) {
        this.companyPaths = companyPaths;
        this.processor = processor;
        this.excelImporter = excelImporter;
    }

    public void run(Path config, Optional<String> companyId, Optional<YearMonth> month,
                    boolean homologation) throws IOException, InterruptedException {
        run(config, companyId, month, homologation, Optional.empty());
    }

    public void run(Path config, Optional<String> companyId, Optional<YearMonth> month,
                    boolean homologation, Optional<Path> spreadsheet) throws IOException, InterruptedException {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            refreshFromSpreadsheet(config, spreadsheet, month);
            WatchState state = loadState(config, companyId, month, spreadsheet, watchService);
            if (state.byKey().isEmpty()) {
                return;
            }
            processor.processExisting(state.routes(), homologation);
            while (!Thread.currentThread().isInterrupted()) {
                state = reloadIfConfigChanged(config, companyId, month, homologation, spreadsheet, watchService, state);
                WatchKey key = watchService.poll(CONFIG_RELOAD_POLL_MILLIS, TimeUnit.MILLISECONDS);
                if (key == null) {
                    continue;
                }
                ResolvedCompanyPath companyPath = state.byKey().get(key);
                if (companyPath != null) {
                    processor.processEvents((Path) key.watchable(), companyPath, key.pollEvents(),
                            homologation, state.routes());
                }
                if (!key.reset()) {
                    state.byKey().remove(key);
                    if (state.byKey().isEmpty()) {
                        return;
                    }
                }
            }
        } catch (ClosedByInterruptException | FileLockInterruptionException exception) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Watcher interrompido durante operacao de arquivo");
        }
    }

    private WatchState reloadIfConfigChanged(Path config, Optional<String> companyId, Optional<YearMonth> month,
                                             boolean homologation, Optional<Path> spreadsheet, WatchService watchService,
                                             WatchState current) throws IOException {
        FileTime modified = lastModified(config);
        FileTime spreadsheetModified = spreadsheet.map(WatchModeRunner::lastModifiedUnchecked).orElse(NO_SPREADSHEET);
        boolean changed = spreadsheet.isPresent()
                ? !spreadsheetModified.equals(current.spreadsheetModified())
                : !modified.equals(current.configModified());
        if (!changed) {
            return current;
        }
        refreshFromSpreadsheet(config, spreadsheet, month);
        cancel(current.byKey());
        WatchState reloaded = loadState(config, companyId, month, spreadsheet, watchService);
        processor.processExisting(reloaded.routes(), homologation);
        return reloaded;
    }

    private WatchState loadState(Path config, Optional<String> companyId, Optional<YearMonth> month,
                                 Optional<Path> spreadsheet, WatchService watchService) throws IOException {
        CompanyRouteDirectory routes = companyPaths.loadRoutes(config, companyId, month);
        return new WatchState(routes, register(routes.monitoredPaths(), watchService), lastModified(config),
                spreadsheet.map(WatchModeRunner::lastModifiedUnchecked).orElse(NO_SPREADSHEET));
    }

    private static FileTime lastModified(Path config) throws IOException {
        return Files.getLastModifiedTime(config.toAbsolutePath().normalize());
    }

    private static FileTime lastModifiedUnchecked(Path path) {
        try {
            return lastModified(path);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Nao foi possivel ler data da planilha: " + path, exception);
        }
    }

    private void refreshFromSpreadsheet(Path config, Optional<Path> spreadsheet, Optional<YearMonth> month) throws IOException {
        if (spreadsheet.isEmpty()) return;
        Path planilha = spreadsheet.orElseThrow();
        if (month.isPresent()) {
            excelImporter.importToYaml(planilha, config, "", true, month, LocalDate.now());
        } else {
            excelImporter.importAllMonthsToYaml(planilha, config, true);
        }
    }

    private static void cancel(Map<WatchKey, ResolvedCompanyPath> byKey) {
        for (WatchKey key : byKey.keySet()) {
            key.cancel();
        }
        byKey.clear();
    }

    private static Map<WatchKey, ResolvedCompanyPath> register(List<ResolvedCompanyPath> paths,
                                                               WatchService watchService) throws IOException {
        Map<WatchKey, ResolvedCompanyPath> byKey = new HashMap<>();
        for (ResolvedCompanyPath path : paths) {
            Path input = path.root().resolve(path.company().folders().input());
            if (!Files.isDirectory(input)) {
                continue;
            }
            WatchKey key = input.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            byKey.put(key, path);
        }
        return byKey;
    }

    private record WatchState(CompanyRouteDirectory routes,
                              Map<WatchKey, ResolvedCompanyPath> byKey,
                              FileTime configModified,
                              FileTime spreadsheetModified) {
    }
}
