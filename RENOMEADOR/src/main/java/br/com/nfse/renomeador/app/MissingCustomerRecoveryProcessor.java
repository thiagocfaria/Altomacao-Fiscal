package br.com.nfse.renomeador.app;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.extraction.InvoiceExtractionService;
import br.com.nfse.renomeador.pipeline.DestinationService;
import br.com.nfse.renomeador.pipeline.DocumentType;
import br.com.nfse.renomeador.pipeline.FileProcessingResult;
import br.com.nfse.renomeador.pipeline.InputCandidate;
import br.com.nfse.renomeador.pipeline.InvoiceProcessingPipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
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
            for (Path pendingFolder : pendingFolders(sourcePath)) {
                if (!Files.isDirectory(pendingFolder)) {
                    continue;
                }
                for (Path pendingDocument : pendingDocuments(pendingFolder)) {
                    Optional<ResolvedCompanyPath> target = targetPathFor(pendingDocument, routes);
                    if (target.isEmpty()) {
                        continue;
                    }
                    List<FileProcessingResult> results = pipeline.process(
                            new InputCandidate(target.orElseThrow(), pendingDocument),
                            preserveInput,
                            routes
                    );
                    discardPendingDuplicateIfAlreadyProcessed(pendingDocument, results, preserveInput);
                    batches.add(new RecoveryBatch(sourcePath, results));
                }
                deleteIfEmpty(pendingFolder);
            }
        }
        return List.copyOf(batches);
    }

    private Optional<ResolvedCompanyPath> targetPathFor(Path pendingDocument, CompanyRouteDirectory routes) throws IOException {
        Optional<InvoiceData> invoice = extractionService.extract(pendingDocument).invoice();
        if (invoice.isEmpty()) {
            return Optional.empty();
        }
        InvoiceData data = invoice.orElseThrow();
        Optional<YearMonth> emissionMonth = parseEmissionMonth(data.issueDate());
        if (emissionMonth.isPresent()) {
            return routes.activePathForCustomerTaxIdAndMonth(data.customerTaxId(), emissionMonth.orElseThrow());
        }
        return routes.activePathForCustomerTaxId(data.customerTaxId());
    }

    private static Optional<YearMonth> parseEmissionMonth(String issueDate) {
        if (issueDate == null || issueDate.isBlank()) {
            return Optional.empty();
        }
        String[] parts = issueDate.trim().split("/");
        if (parts.length == 3) {
            try {
                return Optional.of(YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<Path> pendingFolders(ResolvedCompanyPath sourcePath) {
        Path root = sourcePath.root();
        return List.of(
                root.resolve(DestinationService.MISSING_CUSTOMER_FOLDER),
                DocumentType.PDF.folderUnder(root).resolve(DestinationService.MISSING_CUSTOMER_FOLDER),
                DocumentType.XML.folderUnder(root).resolve(DestinationService.MISSING_CUSTOMER_FOLDER)
        );
    }

    private static List<Path> pendingDocuments(Path pendingFolder) throws IOException {
        try (var stream = Files.list(pendingFolder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(MissingCustomerRecoveryProcessor::isSupportedDocument)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static boolean isSupportedDocument(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileName.endsWith(".pdf") || fileName.endsWith(".xml");
    }

    private static void deleteIfEmpty(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            if (stream.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private static void discardPendingDuplicateIfAlreadyProcessed(Path pendingDocument,
                                                                  List<FileProcessingResult> results,
                                                                  boolean preserveInput) throws IOException {
        if (preserveInput || results.size() != 1) {
            return;
        }
        FileProcessingResult result = results.get(0);
        if (result.skipped() && FileProcessingResult.REASON_ALREADY_REGISTERED.equals(result.reason())) {
            Files.deleteIfExists(pendingDocument);
        }
    }

    record RecoveryBatch(ResolvedCompanyPath sourcePath, List<FileProcessingResult> results) {
    }
}
