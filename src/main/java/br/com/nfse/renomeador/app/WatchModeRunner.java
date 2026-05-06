package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.pipeline.InputScanner;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;
import br.com.nfse.renomeador.pipeline.ProcessingLogger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class WatchModeRunner {
    private final RuntimeCompanyPaths companyPaths;
    private final WatchFolderProcessor processor;

    public WatchModeRunner() {
        this(new RuntimeCompanyPaths(), new WatchFolderProcessor());
    }

    WatchModeRunner(RuntimeCompanyPaths companyPaths, InvoiceProcessingPipeline pipeline, ProcessingLogger logger) {
        this(companyPaths, new WatchFolderProcessor(new InputScanner(), pipeline, logger));
    }

    WatchModeRunner(RuntimeCompanyPaths companyPaths, WatchFolderProcessor processor) {
        this.companyPaths = companyPaths;
        this.processor = processor;
    }

    public void run(Path config, Optional<String> companyId, Optional<YearMonth> month,
                    boolean homologation) throws IOException, InterruptedException {
        CompanyRouteDirectory routes = companyPaths.loadRoutes(config, companyId, month);
        List<ResolvedCompanyPath> paths = routes.monitoredPaths();
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, ResolvedCompanyPath> byKey = register(paths, watchService);
            if (byKey.isEmpty()) {
                return;
            }
            processor.processExisting(routes, homologation);
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                ResolvedCompanyPath companyPath = byKey.get(key);
                if (companyPath != null) {
                    processor.processEvents((Path) key.watchable(), companyPath, key.pollEvents(), homologation, routes);
                }
                if (!key.reset()) {
                    byKey.remove(key);
                    if (byKey.isEmpty()) {
                        return;
                    }
                }
            }
        }
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
}
