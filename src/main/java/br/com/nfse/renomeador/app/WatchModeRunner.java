package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.pipeline.FileProcessingResult;
import br.com.nfse.renomeador.pipeline.InputCandidate;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;
import br.com.nfse.renomeador.pipeline.ProcessingLogger;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;

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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class WatchModeRunner {
    private final RuntimeCompanyPaths companyPaths;
    private final InvoiceProcessingPipeline pipeline;
    private final ProcessingLogger logger;

    public WatchModeRunner() {
        this(new RuntimeCompanyPaths(), new InvoiceProcessingPipeline(), new ProcessingLogger());
    }

    WatchModeRunner(RuntimeCompanyPaths companyPaths, InvoiceProcessingPipeline pipeline, ProcessingLogger logger) {
        this.companyPaths = companyPaths;
        this.pipeline = pipeline;
        this.logger = logger;
    }

    public void run(Path config, Optional<String> companyId, Optional<YearMonth> month,
                    boolean homologation) throws IOException, InterruptedException {
        List<ResolvedCompanyPath> paths = companyPaths.load(config, companyId, month);
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            Map<WatchKey, ResolvedCompanyPath> byKey = register(paths, watchService);
            if (byKey.isEmpty()) {
                return;
            }
            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                ResolvedCompanyPath companyPath = byKey.get(key);
                if (companyPath != null) {
                    handleKey(key, companyPath, homologation);
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
            WatchKey key = input.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);
            byKey.put(key, path);
        }
        return byKey;
    }

    private void handleKey(WatchKey key, ResolvedCompanyPath companyPath, boolean homologation) throws IOException {
        Path input = (Path) key.watchable();
        ProcessingSummary summary = new ProcessingSummary();
        for (var event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            Path file = input.resolve((Path) event.context());
            if (!file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                continue;
            }
            List<FileProcessingResult> results = pipeline.process(new InputCandidate(companyPath, file), homologation);
            for (FileProcessingResult result : results) {
                logger.record(companyPath, result);
                if (result.skipped()) {
                    summary.recordSkipped();
                } else if (result.error() != null) {
                    summary.recordError();
                } else {
                    summary.record(result.status());
                }
            }
        }
        logger.recordSummary(companyPath, summary);
    }
}
