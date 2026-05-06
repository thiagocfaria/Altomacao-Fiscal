package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.pipeline.FileProcessingResult;
import br.com.nfse.renomeador.pipeline.InputScanner;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;
import br.com.nfse.renomeador.pipeline.ProcessingLogger;
import br.com.nfse.renomeador.pipeline.ProcessingSummary;

import java.io.IOException;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BatchModeRunner {
    private final RuntimeCompanyPaths companyPaths;
    private final InputScanner scanner;
    private final InvoiceProcessingPipeline pipeline;
    private final ProcessingLogger logger;

    public BatchModeRunner() {
        this(new RuntimeCompanyPaths(), new InputScanner(), new InvoiceProcessingPipeline(), new ProcessingLogger());
    }

    BatchModeRunner(RuntimeCompanyPaths companyPaths, InputScanner scanner, InvoiceProcessingPipeline pipeline,
                    ProcessingLogger logger) {
        this.companyPaths = companyPaths;
        this.scanner = scanner;
        this.pipeline = pipeline;
        this.logger = logger;
    }

    public ProcessingSummary run(Path config, Optional<String> companyId, Optional<YearMonth> month,
                                 boolean homologation) throws IOException {
        CompanyRouteDirectory routes = companyPaths.loadRoutes(config, companyId, month);
        List<ResolvedCompanyPath> paths = routes.monitoredPaths();
        List<br.com.nfse.renomeador.pipeline.InputCandidate> candidates = scanner.scan(paths);
        Map<ResolvedCompanyPath, ProcessingSummary> summariesByPath = new HashMap<>();
        ProcessingSummary overall = new ProcessingSummary();

        for (ResolvedCompanyPath path : paths) {
            summariesByPath.put(path, new ProcessingSummary());
        }

        for (var candidate : candidates) {
            List<FileProcessingResult> results = pipeline.process(candidate, homologation, routes);
            ProcessingSummary pathSummary = summariesByPath.computeIfAbsent(candidate.companyPath(), ignored -> new ProcessingSummary());
            for (FileProcessingResult result : results) {
                recordOperationalLogs(candidate.companyPath(), result, routes);
                record(pathSummary, result);
                record(overall, result);
            }
        }

        for (Map.Entry<ResolvedCompanyPath, ProcessingSummary> entry : summariesByPath.entrySet()) {
            logger.recordSummary(entry.getKey(), entry.getValue());
        }
        return overall;
    }

    private void recordOperationalLogs(ResolvedCompanyPath sourcePath, FileProcessingResult result,
                                       CompanyRouteDirectory routes) throws IOException {
        logger.record(sourcePath, result);
        if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
            return;
        }
        Optional<ResolvedCompanyPath> targetPath = routes.activePathForCompanyId(result.companyId());
        if (targetPath.isPresent() && !targetPath.orElseThrow().equals(sourcePath)) {
            logger.record(targetPath.orElseThrow(), result);
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
}
