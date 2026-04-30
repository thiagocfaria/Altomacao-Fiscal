package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.ProcessingDecision;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.extraction.ExtractionResult;
import br.com.nfse.renomeador.extraction.InvoiceExtractionService;
import br.com.nfse.renomeador.extraction.InvoiceSplitter;
import br.com.nfse.renomeador.files.FileHashService;
import br.com.nfse.renomeador.files.OriginalArchiveService;
import br.com.nfse.renomeador.files.StableFileGuard;
import br.com.nfse.renomeador.ledger.LedgerEntry;
import br.com.nfse.renomeador.ledger.ProcessingLedger;
import br.com.nfse.renomeador.naming.FileNameBuilder;
import br.com.nfse.renomeador.processing.ProcessingDecisionService;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import br.com.nfse.renomeador.pdf.PdfTextExtractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InvoiceProcessingPipeline {
    private final StableFileGuard stableFileGuard;
    private final FileHashService hashService;
    private final OriginalArchiveService archiveService;
    private final InvoiceExtractionService extractionService;
    private final InvoiceSplitter splitter;
    private final PdfTextExtractor textExtractor;
    private final DestinationService destinationService;
    private final FileNameBuilder fileNameBuilder;
    private final Duration stabilityInterval;
    private final int stabilityChecks;

    public InvoiceProcessingPipeline() {
        this(new StableFileGuard(), new FileHashService(), new OriginalArchiveService(),
                new InvoiceExtractionService(), new InvoiceSplitter(), new PdfTextExtractor(),
                new DestinationService(), new FileNameBuilder(), Duration.ofMillis(200), 2);
    }

    InvoiceProcessingPipeline(StableFileGuard stableFileGuard, FileHashService hashService,
                              OriginalArchiveService archiveService, InvoiceExtractionService extractionService,
                              InvoiceSplitter splitter, PdfTextExtractor textExtractor,
                              DestinationService destinationService, FileNameBuilder fileNameBuilder,
                              Duration stabilityInterval, int stabilityChecks) {
        this.stableFileGuard = stableFileGuard;
        this.hashService = hashService;
        this.archiveService = archiveService;
        this.extractionService = extractionService;
        this.splitter = splitter;
        this.textExtractor = textExtractor;
        this.destinationService = destinationService;
        this.fileNameBuilder = fileNameBuilder;
        this.stabilityInterval = stabilityInterval;
        this.stabilityChecks = stabilityChecks;
    }

    public List<FileProcessingResult> process(InputCandidate candidate, boolean preserveInput) {
        ResolvedCompanyPath companyPath = candidate.companyPath();
        Path source = candidate.source();
        String companyId = companyPath.company().id();
        ProcessingLedger ledger = new ProcessingLedger(PathsForCompany.ledger(companyPath));
        long size = -1L;
        Instant lastModified = Instant.EPOCH;
        String sha256 = "";

        try {
            if (!stableFileGuard.isStable(source, stabilityInterval, stabilityChecks)) {
                return List.of(FileProcessingResult.skipped(companyId, source, "Arquivo ainda nao esta estavel"));
            }

            size = Files.size(source);
            lastModified = Files.getLastModifiedTime(source).toInstant();
            sha256 = hashService.sha256(source);
            if (ledger.hasProcessed(companyId, source, size, lastModified, sha256)) {
                return List.of(FileProcessingResult.skipped(companyId, source, "Arquivo ja registrado no ledger"));
            }

            archiveService.archive(source, PathsForCompany.originals(companyPath));
            List<FileProcessingResult> results = processWorkFiles(source, companyPath, preserveInput);
            ledger.record(new LedgerEntry(
                    companyId,
                    source,
                    size,
                    lastModified,
                    sha256,
                    finalStatus(results),
                    finalDestination(results),
                    Instant.now()
            ));
            cleanupSourceAfterSplit(source, preserveInput, results);
            return results;
        } catch (Exception exception) {
            FileProcessingResult result = handleTechnicalError(companyPath, source, preserveInput, exception);
            recordTechnicalFailureIfPossible(ledger, companyId, source, size, lastModified, sha256, result);
            return List.of(result);
        }
    }

    private List<FileProcessingResult> processWorkFiles(Path source, ResolvedCompanyPath companyPath,
                                                        boolean preserveInput) throws IOException {
        List<Path> workFiles = workFilesFor(source, companyPath);
        boolean split = workFiles.size() > 1 || !workFiles.get(0).equals(source);
        List<FileProcessingResult> results = new ArrayList<>();
        for (Path workFile : workFiles) {
            results.add(processSingle(workFile, source, companyPath, preserveInput || split));
        }
        if (split) {
            cleanupWorkFiles(workFiles);
        }
        return List.copyOf(results);
    }

    private List<Path> workFilesFor(Path source, ResolvedCompanyPath companyPath) throws IOException {
        if (textExtractor.extractPages(source).size() <= 1) {
            return List.of(source);
        }
        if (splitter.splitSupportedPages(source).size() <= 1) {
            return List.of(source);
        }
        Path splitDirectory = nextRunDirectory(PathsForCompany.logs(companyPath).resolve("split-work"));
        return splitter.splitSupportedPagesToFiles(source, splitDirectory);
    }

    private FileProcessingResult processSingle(Path workFile, Path originalSource, ResolvedCompanyPath companyPath,
                                               boolean preserveInput) throws IOException {
        ExtractionResult extraction = extractionService.extract(workFile);
        ProcessingDecision decision;
        InvoiceData invoice;
        if (extraction.invoice().isPresent()) {
            invoice = extraction.invoice().orElseThrow();
            decision = new ProcessingDecisionService(companyPath.company().customerTaxId()).decide(invoice);
        } else {
            invoice = placeholderInvoice(extraction);
            decision = new ProcessingDecision(ProcessingStatus.UNSUPPORTED, true, extraction.reason());
        }

        String finalName = fileNameBuilder.build(invoice, decision.status());
        DestinationResult destination = destinationService.send(workFile, companyPath, decision.status(), finalName, preserveInput);
        return FileProcessingResult.processed(companyPath.company().id(), originalSource, decision.status(),
                decision.reason(), destination.destination());
    }

    private FileProcessingResult handleTechnicalError(ResolvedCompanyPath companyPath, Path source,
                                                      boolean preserveInput, Exception exception) {
        try {
            DestinationResult destination = destinationService.sendTechnicalError(source, companyPath, preserveInput);
            return FileProcessingResult.failed(companyPath.company().id(), source,
                    "Erro tecnico no processamento", destination.destination(), exception);
        } catch (Exception moveException) {
            exception.addSuppressed(moveException);
            return FileProcessingResult.failed(companyPath.company().id(), source,
                    "Erro tecnico no processamento; falha ao mover para revisao", null, exception);
        }
    }

    private static InvoiceData placeholderInvoice(ExtractionResult extraction) {
        return new InvoiceData(extraction.layout(), "", "", "", "", "", "",
                null, null, false, false);
    }

    private static String finalStatus(List<FileProcessingResult> results) {
        if (results.size() == 1) {
            return String.valueOf(results.get(0).status());
        }
        return "SPLIT_" + results.size();
    }

    private static Path finalDestination(List<FileProcessingResult> results) {
        return results.stream()
                .map(FileProcessingResult::destination)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(Path.of(""));
    }

    private static void cleanupSourceAfterSplit(Path source, boolean preserveInput,
                                                List<FileProcessingResult> results) throws IOException {
        if (preserveInput || results.size() <= 1) {
            return;
        }
        Files.deleteIfExists(source);
    }

    private static void cleanupWorkFiles(List<Path> workFiles) throws IOException {
        Path parent = null;
        for (Path workFile : workFiles) {
            parent = workFile.getParent();
            Files.deleteIfExists(workFile);
        }
        if (parent != null) {
            try (var stream = Files.list(parent)) {
                if (stream.findAny().isEmpty()) {
                    Files.deleteIfExists(parent);
                }
            }
        }
    }

    private static void recordTechnicalFailureIfPossible(ProcessingLedger ledger, String companyId, Path source,
                                                         long size, Instant lastModified, String sha256,
                                                         FileProcessingResult result) {
        if (size < 0 || sha256.isBlank()) {
            return;
        }
        try {
            ledger.record(new LedgerEntry(
                    companyId,
                    source,
                    size,
                    lastModified,
                    sha256,
                    "ERROR",
                    result.destination() == null ? Path.of("") : result.destination(),
                    Instant.now()
            ));
        } catch (IOException ignored) {
            // O log operacional ainda carrega a falha principal; nao esconda o erro original por falha secundaria.
        }
    }

    private static Path nextRunDirectory(Path desired) throws IOException {
        Files.createDirectories(desired.getParent());
        if (!Files.exists(desired)) {
            Files.createDirectories(desired);
            return desired;
        }
        for (int counter = 1; counter < 10_000; counter++) {
            Path candidate = desired.getParent().resolve("%s_%02d".formatted(desired.getFileName(), counter));
            if (!Files.exists(candidate)) {
                Files.createDirectories(candidate);
                return candidate;
            }
        }
        throw new IllegalStateException("Nao foi possivel criar diretorio temporario de split: " + desired);
    }
}
