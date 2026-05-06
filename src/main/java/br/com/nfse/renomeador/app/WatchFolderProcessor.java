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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WatchFolderProcessor {
    private final InputScanner scanner;
    private final InvoiceProcessingPipeline pipeline;
    private final ProcessingLogger logger;

    WatchFolderProcessor() {
        this(new InputScanner(), new InvoiceProcessingPipeline(), new ProcessingLogger());
    }

    WatchFolderProcessor(InputScanner scanner, InvoiceProcessingPipeline pipeline, ProcessingLogger logger) {
        this.scanner = scanner;
        this.pipeline = pipeline;
        this.logger = logger;
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
        for (InputCandidate candidate : scanner.scan(paths)) {
            ProcessingSummary pathSummary = summariesByPath.computeIfAbsent(candidate.companyPath(),
                    ignored -> new ProcessingSummary());
            List<FileProcessingResult> results = pipeline.process(candidate, homologation, routes);
            recordResults(candidate.companyPath(), results, pathSummary, routes);
            recordAll(overall, results);
        }
        for (Map.Entry<ResolvedCompanyPath, ProcessingSummary> entry : summariesByPath.entrySet()) {
            logger.recordSummary(entry.getKey(), entry.getValue());
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
            recordResults(companyPath, pipeline.process(new InputCandidate(companyPath, file), homologation, routes),
                    summary, routes);
        }
        logger.recordSummary(companyPath, summary);
        return summary;
    }

    private void recordResultsFromScan(ResolvedCompanyPath companyPath, boolean homologation,
                                       ProcessingSummary summary, CompanyRouteDirectory routes) throws IOException {
        for (InputCandidate candidate : scanner.scan(List.of(companyPath))) {
            recordResults(candidate.companyPath(), pipeline.process(candidate, homologation, routes), summary, routes);
        }
    }

    private void recordResults(ResolvedCompanyPath companyPath, List<FileProcessingResult> results,
                               ProcessingSummary summary, CompanyRouteDirectory routes) throws IOException {
        for (FileProcessingResult result : results) {
            recordOperationalLogs(companyPath, result, routes);
            record(summary, result);
        }
    }

    private void recordOperationalLogs(ResolvedCompanyPath sourcePath, FileProcessingResult result,
                                       CompanyRouteDirectory routes) throws IOException {
        logger.record(sourcePath, result);
        if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
            return;
        }
        var targetPath = routes.activePathForCompanyId(result.companyId());
        if (targetPath.isPresent() && !targetPath.orElseThrow().equals(sourcePath)) {
            logger.record(targetPath.orElseThrow(), result);
        }
    }

    private static void recordAll(ProcessingSummary summary, List<FileProcessingResult> results) {
        for (FileProcessingResult result : results) {
            record(summary, result);
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
}
