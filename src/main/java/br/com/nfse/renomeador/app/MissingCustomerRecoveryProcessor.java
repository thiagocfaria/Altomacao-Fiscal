package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.extraction.InvoiceExtractionService;
import br.com.nfse.renomeador.pipeline.DestinationService;
import br.com.nfse.renomeador.pipeline.FileProcessingResult;
import br.com.nfse.renomeador.pipeline.InputCandidate;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

final class MissingCustomerRecoveryProcessor {
    private final InvoiceExtractionService extractionService;
    private final InvoiceProcessingPipeline pipeline;

    MissingCustomerRecoveryProcessor() {
        this(new InvoiceExtractionService(), new InvoiceProcessingPipeline());
    }

    MissingCustomerRecoveryProcessor(InvoiceProcessingPipeline pipeline) {
        this(new InvoiceExtractionService(), pipeline);
    }

    MissingCustomerRecoveryProcessor(InvoiceExtractionService extractionService,
                                     InvoiceProcessingPipeline pipeline) {
        this.extractionService = extractionService;
        this.pipeline = pipeline;
    }

    List<RecoveryBatch> recover(CompanyRouteDirectory routes, boolean preserveInput) throws IOException {
        List<RecoveryBatch> batches = new java.util.ArrayList<>();
        for (ResolvedCompanyPath sourcePath : routes.monitoredPaths()) {
            Path pendingFolder = sourcePath.root().resolve(DestinationService.MISSING_CUSTOMER_FOLDER);
            if (!Files.isDirectory(pendingFolder)) {
                continue;
            }
            for (Path pendingPdf : pendingPdfs(pendingFolder)) {
                Optional<ResolvedCompanyPath> target = targetPathFor(pendingPdf, routes);
                if (target.isEmpty()) {
                    continue;
                }
                List<FileProcessingResult> results = pipeline.process(
                        new InputCandidate(target.orElseThrow(), pendingPdf),
                        preserveInput,
                        routes
                );
                discardPendingDuplicateIfAlreadyProcessed(pendingPdf, results, preserveInput);
                batches.add(new RecoveryBatch(sourcePath, results));
            }
            deleteIfEmpty(pendingFolder);
        }
        return List.copyOf(batches);
    }

    private Optional<ResolvedCompanyPath> targetPathFor(Path pendingPdf, CompanyRouteDirectory routes) throws IOException {
        Optional<InvoiceData> invoice = extractionService.extract(pendingPdf).invoice();
        if (invoice.isEmpty()) {
            return Optional.empty();
        }
        return routes.activePathForCustomerTaxId(invoice.orElseThrow().customerTaxId());
    }

    private static List<Path> pendingPdfs(Path pendingFolder) throws IOException {
        try (var stream = Files.list(pendingFolder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(MissingCustomerRecoveryProcessor::isPdf)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean isPdf(Path path) {
        return path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }

    private static void deleteIfEmpty(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private static void discardPendingDuplicateIfAlreadyProcessed(Path pendingPdf,
                                                                  List<FileProcessingResult> results,
                                                                  boolean preserveInput) throws IOException {
        if (preserveInput || results.size() != 1) {
            return;
        }
        FileProcessingResult result = results.get(0);
        if (result.skipped() && FileProcessingResult.REASON_ALREADY_REGISTERED.equals(result.reason())) {
            Files.deleteIfExists(pendingPdf);
        }
    }

    record RecoveryBatch(ResolvedCompanyPath sourcePath, List<FileProcessingResult> results) {
    }
}
