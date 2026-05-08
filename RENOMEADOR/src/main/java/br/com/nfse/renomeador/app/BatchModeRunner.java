package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.pipeline.FileProcessingResult;
import br.com.nfse.renomeador.pipeline.InputScanner;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;
import br.com.nfse.renomeador.pipeline.ProcessingLogger;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class BatchModeRunner {
    private static final long FILE_TIMEOUT_SECONDS = 60L;
    private static final long MIN_FREE_SPACE_BYTES = 500L * 1024L * 1024L;
    private static final int MAX_PROCESSING_THREADS = 2;

    private final RuntimeCompanyPaths companyPaths;
    private final InputScanner scanner;
    private final InvoiceProcessingPipeline pipeline;
    private final ProcessingLogger logger;
    private final MissingCustomerRecoveryProcessor recoveryProcessor;

    public BatchModeRunner() {
        this(new RuntimeCompanyPaths(), new InputScanner(), new InvoiceProcessingPipeline(), new ProcessingLogger());
    }

    BatchModeRunner(RuntimeCompanyPaths companyPaths, InputScanner scanner, InvoiceProcessingPipeline pipeline,
                    ProcessingLogger logger) {
        this(companyPaths, scanner, pipeline, logger, new MissingCustomerRecoveryProcessor(pipeline));
    }

    BatchModeRunner(RuntimeCompanyPaths companyPaths, InputScanner scanner, InvoiceProcessingPipeline pipeline,
                    ProcessingLogger logger, MissingCustomerRecoveryProcessor recoveryProcessor) {
        this.companyPaths = companyPaths;
        this.scanner = scanner;
        this.pipeline = pipeline;
        this.logger = logger;
        this.recoveryProcessor = recoveryProcessor;
    }

    public ProcessingSummary run(Path config, Optional<String> companyId, Optional<YearMonth> month,
                                 boolean homologation) throws IOException {
        CompanyRouteDirectory routes = companyPaths.loadRoutes(config, companyId, month);
        List<ResolvedCompanyPath> paths = routes.monitoredPaths();
        validateDiskSpace(paths);
        List<br.com.nfse.renomeador.pipeline.InputCandidate> candidates = scanner.scan(paths);
        Map<ResolvedCompanyPath, ProcessingSummary> summariesByPath = new HashMap<>();
        ProcessingSummary overall = new ProcessingSummary();
        ExecutorService timeoutExecutor = Executors.newFixedThreadPool(MAX_PROCESSING_THREADS,
                new NamedThreadFactory("nfse-timeout"));

        try {
            for (ResolvedCompanyPath path : paths) {
                summariesByPath.put(path, new ProcessingSummary());
            }

            for (MissingCustomerRecoveryProcessor.RecoveryBatch batch : recoveryProcessor.recover(routes, homologation)) {
                ProcessingSummary pathSummary = summariesByPath.computeIfAbsent(batch.sourcePath(),
                        ignored -> new ProcessingSummary());
                for (FileProcessingResult result : batch.results()) {
                    recordOperationalLogs(batch.sourcePath(), result, routes);
                    record(pathSummary, result);
                    recordRoutedSummary(summariesByPath, batch.sourcePath(), result, routes);
                    record(overall, result);
                }
            }

            for (var candidate : candidates) {
                List<FileProcessingResult> results = processWithTimeout(candidate, homologation, routes, timeoutExecutor);
                ProcessingSummary pathSummary = summariesByPath.computeIfAbsent(candidate.companyPath(), ignored -> new ProcessingSummary());
                for (FileProcessingResult result : results) {
                    recordOperationalLogs(candidate.companyPath(), result, routes);
                    record(pathSummary, result);
                    recordRoutedSummary(summariesByPath, candidate.companyPath(), result, routes);
                    record(overall, result);
                }
            }

            for (ResolvedCompanyPath path : paths) {
                int repaired = pipeline.repairMisnamedOutputFiles(path);
                for (int i = 0; i < repaired; i++) {
                    overall.recordRepaired();
                }
            }

            for (Map.Entry<ResolvedCompanyPath, ProcessingSummary> entry : summariesByPath.entrySet()) {
                logger.recordSummary(routes, entry.getKey(), entry.getValue());
            }
            return overall;
        } finally {
            timeoutExecutor.shutdownNow();
        }
    }

    private List<FileProcessingResult> processWithTimeout(
            br.com.nfse.renomeador.pipeline.InputCandidate candidate,
            boolean homologation,
            CompanyRouteDirectory routes,
            ExecutorService timeoutExecutor) throws IOException {
        var future = timeoutExecutor.submit(() -> pipeline.process(candidate, homologation, routes));
        try {
            return future.get(FILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return pipeline.timeoutResult(candidate, homologation, routes, FILE_TIMEOUT_SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new IOException("Processamento interrompido", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Falha ao processar arquivo", cause);
        }
    }

    private static void validateDiskSpace(List<ResolvedCompanyPath> paths) throws IOException {
        for (ResolvedCompanyPath path : paths) {
            Path root = path.root().toAbsolutePath().normalize();
            if (!Files.exists(root)) {
                continue;
            }
            FileStore store = Files.getFileStore(root);
            long free = store.getUsableSpace();
            if (free < MIN_FREE_SPACE_BYTES) {
                throw new IllegalStateException("Espaco em disco insuficiente em " + root
                        + ": " + (free / 1_048_576L) + "MB livres, minimo "
                        + (MIN_FREE_SPACE_BYTES / 1_048_576L) + "MB necessarios");
            }
        }
    }

    private void recordOperationalLogs(ResolvedCompanyPath sourcePath, FileProcessingResult result,
                                       CompanyRouteDirectory routes) throws IOException {
        logger.record(routes, sourcePath, result);
        if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
            return;
        }
        Optional<ResolvedCompanyPath> targetPath = routes.activePathForCompanyId(result.companyId());
        if (targetPath.isPresent() && !targetPath.orElseThrow().equals(sourcePath)) {
            logger.record(routes, targetPath.orElseThrow(), result);
        }
    }

    private static void recordRoutedSummary(Map<ResolvedCompanyPath, ProcessingSummary> summariesByPath,
                                            ResolvedCompanyPath sourcePath,
                                            FileProcessingResult result,
                                            CompanyRouteDirectory routes) {
        if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
            return;
        }
        Optional<ResolvedCompanyPath> targetPath = routes.activePathForCompanyId(result.companyId());
        if (targetPath.isPresent() && !targetPath.orElseThrow().equals(sourcePath)) {
            ProcessingSummary targetSummary = summariesByPath.computeIfAbsent(targetPath.orElseThrow(),
                    ignored -> new ProcessingSummary());
            record(targetSummary, result);
        }
    }

    private static void record(ProcessingSummary summary, FileProcessingResult result) {
        if (result.skipped()) {
            summary.recordSkipped();
        } else if (result.error() != null) {
            summary.recordError();
        } else {
            summary.record(result.status());
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
