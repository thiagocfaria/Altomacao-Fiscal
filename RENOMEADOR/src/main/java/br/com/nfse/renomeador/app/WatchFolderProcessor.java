package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.pipeline.FileProcessingResult;
import br.com.nfse.renomeador.pipeline.InputCandidate;
import br.com.nfse.renomeador.pipeline.InputScanner;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;
import br.com.nfse.renomeador.pipeline.ProcessingLogger;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

final class WatchFolderProcessor {
    private static final int MAX_STABILITY_ATTEMPTS = 5;
    private static final Duration STABILITY_RETRY_DELAY = Duration.ofMillis(250);
    private static final long FILE_TIMEOUT_SECONDS = 60L;

    private final InputScanner scanner;
    private final InvoiceProcessingPipeline pipeline;
    private final ProcessingLogger logger;
    private final MissingCustomerRecoveryProcessor recoveryProcessor;
    private final ExecutorService timeoutExecutor;

    WatchFolderProcessor() {
        this(new InputScanner(), new InvoiceProcessingPipeline(), new ProcessingLogger());
    }

    WatchFolderProcessor(InputScanner scanner, InvoiceProcessingPipeline pipeline, ProcessingLogger logger) {
        this(scanner, pipeline, logger, new MissingCustomerRecoveryProcessor(pipeline));
    }

    WatchFolderProcessor(InputScanner scanner, InvoiceProcessingPipeline pipeline, ProcessingLogger logger,
                         MissingCustomerRecoveryProcessor recoveryProcessor) {
        this.scanner = scanner;
        this.pipeline = pipeline;
        this.logger = logger;
        this.recoveryProcessor = recoveryProcessor;
        this.timeoutExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("nfse-watch-timeout"));
    }

    ProcessingSummary processExisting(List<ResolvedCompanyPath> paths, boolean homologation) throws IOException {
        return processExisting(new CompanyRouteDirectory(paths, paths, paths.stream()
                .filter(path -> !path.company().sourceOnly())
                .map(path -> br.com.nfse.renomeador.text.TextNormalizer.digitsOnly(path.company().customerTaxId()))
                .collect(java.util.stream.Collectors.toSet())), homologation);
    }

    ProcessingSummary processExisting(CompanyRouteDirectory routes, boolean homologation) throws IOException {
        List<ResolvedCompanyPath> paths = routes.monitoredPaths();
        ProcessingSummary overall = new ProcessingSummary();
        Map<ResolvedCompanyPath, ProcessingSummary> summariesByPath = new HashMap<>();
        for (ResolvedCompanyPath path : paths) {
            summariesByPath.put(path, new ProcessingSummary());
        }
        for (MissingCustomerRecoveryProcessor.RecoveryBatch batch : recoveryProcessor.recover(routes, homologation)) {
            ProcessingSummary pathSummary = summariesByPath.computeIfAbsent(batch.sourcePath(),
                    ignored -> new ProcessingSummary());
            recordResultsForExisting(batch.sourcePath(), batch.results(), pathSummary, summariesByPath, routes);
            recordAll(overall, batch.results());
        }
        for (InputCandidate candidate : scanner.scan(paths)) {
            ProcessingSummary pathSummary = summariesByPath.computeIfAbsent(candidate.companyPath(),
                    ignored -> new ProcessingSummary());
            List<FileProcessingResult> results = processCandidateWithRetry(candidate, homologation, routes);
            recordResultsForExisting(candidate.companyPath(), results, pathSummary, summariesByPath, routes);
            recordAll(overall, results);
        }
        for (Map.Entry<ResolvedCompanyPath, ProcessingSummary> entry : summariesByPath.entrySet()) {
            logger.recordSummary(routes, entry.getKey(), entry.getValue());
        }
        return overall;
    }

    ProcessingSummary processEvents(Path input, ResolvedCompanyPath companyPath, List<WatchEvent<?>> events,
                                    boolean homologation) throws IOException {
        return processEvents(input, companyPath, events, homologation, CompanyRouteDirectory.single(companyPath));
    }

    ProcessingSummary processEvents(Path input, ResolvedCompanyPath companyPath, List<WatchEvent<?>> events,
                                    boolean homologation, CompanyRouteDirectory routes) throws IOException {
        ProcessingSummary summary = new ProcessingSummary();
        for (WatchEvent<?> event : events) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                recordResultsFromScan(companyPath, homologation, summary, routes);
                continue;
            }
            if (event.kind() != StandardWatchEventKinds.ENTRY_CREATE
                    && event.kind() != StandardWatchEventKinds.ENTRY_MODIFY) {
                continue;
            }
            Object context = event.context();
            if (!(context instanceof Path relative)) {
                continue;
            }
            Path file = input.resolve(relative);
            if (!isPdf(file)) {
                continue;
            }
            recordResults(companyPath, processCandidateWithRetry(new InputCandidate(companyPath, file), homologation, routes),
                    summary, routes);
        }
        logger.recordSummary(routes, companyPath, summary);
        return summary;
    }

    private void recordResultsFromScan(ResolvedCompanyPath companyPath, boolean homologation,
                                       ProcessingSummary summary, CompanyRouteDirectory routes) throws IOException {
        for (InputCandidate candidate : scanner.scan(List.of(companyPath))) {
            recordResults(candidate.companyPath(), processCandidateWithRetry(candidate, homologation, routes),
                    summary, routes);
        }
    }

    private List<FileProcessingResult> processCandidateWithRetry(InputCandidate candidate, boolean homologation,
                                                                 CompanyRouteDirectory routes) throws IOException {
        List<FileProcessingResult> lastResult = List.of();
        for (int attempt = 1; attempt <= MAX_STABILITY_ATTEMPTS; attempt++) {
            lastResult = processWithTimeout(candidate, homologation, routes);
            if (!isUnstableSkip(lastResult) || attempt == MAX_STABILITY_ATTEMPTS) {
                return lastResult;
            }
            if (!sleepBeforeRetry()) {
                return lastResult;
            }
        }
        return lastResult;
    }

    private List<FileProcessingResult> processWithTimeout(InputCandidate candidate, boolean homologation,
                                                          CompanyRouteDirectory routes) throws IOException {
        var future = timeoutExecutor.submit(() -> pipeline.process(candidate, homologation, routes));
        try {
            return future.get(FILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            return pipeline.timeoutResult(candidate, homologation, routes, FILE_TIMEOUT_SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return List.of(FileProcessingResult.skipped(candidate.companyPath().company().id(), candidate.source(),
                    "Processamento interrompido"));
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IOException("Falha ao processar arquivo observado", cause);
        }
    }

    private void recordResults(ResolvedCompanyPath companyPath, List<FileProcessingResult> results,
                               ProcessingSummary summary, CompanyRouteDirectory routes) throws IOException {
        for (FileProcessingResult result : results) {
            recordOperationalLogs(companyPath, result, routes);
            record(summary, result);
        }
    }

    private void recordResultsForExisting(ResolvedCompanyPath companyPath, List<FileProcessingResult> results,
                                          ProcessingSummary summary,
                                          Map<ResolvedCompanyPath, ProcessingSummary> summariesByPath,
                                          CompanyRouteDirectory routes) throws IOException {
        for (FileProcessingResult result : results) {
            recordOperationalLogs(companyPath, result, routes);
            record(summary, result);
            recordRoutedSummary(summariesByPath, companyPath, result, routes);
        }
    }

    private void recordOperationalLogs(ResolvedCompanyPath sourcePath, FileProcessingResult result,
                                       CompanyRouteDirectory routes) throws IOException {
        logger.record(routes, sourcePath, result);
        if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
            return;
        }
        var targetPath = routes.activePathForCompanyId(result.companyId());
        if (targetPath.isPresent() && !targetPath.orElseThrow().equals(sourcePath)) {
            logger.record(routes, targetPath.orElseThrow(), result);
        }
    }

    private static void recordAll(ProcessingSummary summary, List<FileProcessingResult> results) {
        for (FileProcessingResult result : results) {
            record(summary, result);
        }
    }

    private static void recordRoutedSummary(Map<ResolvedCompanyPath, ProcessingSummary> summariesByPath,
                                            ResolvedCompanyPath sourcePath,
                                            FileProcessingResult result,
                                            CompanyRouteDirectory routes) {
        if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
            return;
        }
        var targetPath = routes.activePathForCompanyId(result.companyId());
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

    private static boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static boolean isUnstableSkip(List<FileProcessingResult> results) {
        return results.size() == 1
                && results.get(0).skipped()
                && FileProcessingResult.REASON_UNSTABLE_FILE.equals(results.get(0).reason());
    }

    private static boolean sleepBeforeRetry() {
        try {
            Thread.sleep(STABILITY_RETRY_DELAY.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
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
