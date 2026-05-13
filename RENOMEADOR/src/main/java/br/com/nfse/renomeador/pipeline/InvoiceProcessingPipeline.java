package br.com.nfse.renomeador.pipeline;

import br.com.nfse.renomeador.InvoiceData;
import br.com.nfse.renomeador.ProcessingDecision;
import br.com.nfse.renomeador.config.CompanyRouteDirectory;
import br.com.nfse.renomeador.config.ResolvedCompanyPath;
import br.com.nfse.renomeador.extraction.ExtractionResult;
import br.com.nfse.renomeador.extraction.InvoiceExtractionService;
import br.com.nfse.renomeador.extraction.InvoiceSplitter;
import br.com.nfse.renomeador.files.FileHashService;
import br.com.nfse.renomeador.files.StableFileGuard;
import br.com.nfse.renomeador.layout.LayoutType;
import br.com.nfse.renomeador.ledger.DuplicateInvoiceIndex;
import br.com.nfse.renomeador.ledger.LedgerEntry;
import br.com.nfse.renomeador.ledger.ProcessingLedger;
import br.com.nfse.renomeador.naming.FileNameBuilder;
import br.com.nfse.renomeador.processing.ProcessingDecisionService;
import br.com.nfse.renomeador.processing.ProcessingStatus;
import br.com.nfse.renomeador.pdf.PdfTextExtractor;
import br.com.nfse.renomeador.text.TextNormalizer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InvoiceProcessingPipeline {
    public static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;
    public static final int MAX_PAGE_COUNT = 80;
    private static final Pattern IMPORT_API_PN_FILE_NAME =
            Pattern.compile("^PN_(\\d{14})_NSU_.*\\.(?i:xml|pdf)$");

    private final StableFileGuard stableFileGuard;
    private final FileHashService hashService;
    private final InvoiceExtractionService extractionService;
    private final InvoiceSplitter splitter;
    private final PdfTextExtractor textExtractor;
    private final DestinationService destinationService;
    private final FileNameBuilder fileNameBuilder;
    private final Duration stabilityInterval;
    private final int stabilityChecks;
    private final ProcessingStateRegistry processingStateRegistry;

    public InvoiceProcessingPipeline() {
        this(new StableFileGuard(), new FileHashService(), new InvoiceExtractionService(), new InvoiceSplitter(), new PdfTextExtractor(),
                new DestinationService(), new FileNameBuilder(), Duration.ofMillis(200), 2,
                new ProcessingStateRegistry());
    }

    InvoiceProcessingPipeline(StableFileGuard stableFileGuard, FileHashService hashService,
                              InvoiceExtractionService extractionService, InvoiceSplitter splitter, PdfTextExtractor textExtractor,
                              DestinationService destinationService, FileNameBuilder fileNameBuilder,
                              Duration stabilityInterval, int stabilityChecks,
                              ProcessingStateRegistry processingStateRegistry) {
        this.stableFileGuard = stableFileGuard;
        this.hashService = hashService;
        this.extractionService = extractionService;
        this.splitter = splitter;
        this.textExtractor = textExtractor;
        this.destinationService = destinationService;
        this.fileNameBuilder = fileNameBuilder;
        this.stabilityInterval = stabilityInterval;
        this.stabilityChecks = stabilityChecks;
        this.processingStateRegistry = processingStateRegistry;
    }

    public List<FileProcessingResult> process(InputCandidate candidate, boolean preserveInput) {
        return process(candidate, preserveInput, CompanyRouteDirectory.single(candidate.companyPath()));
    }

    public List<FileProcessingResult> process(InputCandidate candidate, boolean preserveInput,
                                              CompanyRouteDirectory routes) {
        Instant startedAt = Instant.now();
        ResolvedCompanyPath companyPath = candidate.companyPath();
        Path source = candidate.source();
        DocumentType documentType = DocumentType.from(source);
        String companyId = companyPath.company().id();
        ProcessingLedger ledger = processingStateRegistry.ledger(TechnicalPaths.ledger(routes, companyPath));
        ProcessingLedger legacyLedger = processingStateRegistry.ledger(PathsForCompany.ledger(companyPath));
        long size = -1L;
        Instant lastModified = Instant.EPOCH;
        String sha256 = "";

        try {
            if (!stableFileGuard.isStable(source, stabilityInterval, stabilityChecks)) {
                return List.of(FileProcessingResult.skipped(companyId, source, FileProcessingResult.REASON_UNSTABLE_FILE,
                        elapsedMillis(startedAt)));
            }

            size = Files.size(source);
            lastModified = Files.getLastModifiedTime(source).toInstant();
            if (size > MAX_FILE_SIZE_BYTES) {
                FileProcessingResult result = handleOversizedFile(companyPath, source, preserveInput, routes, size);
                ledger.record(new LedgerEntry(
                        companyId,
                        source,
                        size,
                        lastModified,
                        "",
                        "ERROR",
                        result.destination() == null ? Path.of("") : result.destination(),
                        Instant.now()
                ));
                return List.of(result.withDurationMillis(elapsedMillis(startedAt)));
            }
            if (documentType == DocumentType.PDF) {
                int pages = textExtractor.countPages(source);
                if (pages > MAX_PAGE_COUNT) {
                    FileProcessingResult result = handleTooManyPagesFile(companyPath, source, preserveInput, routes, pages);
                    ledger.record(new LedgerEntry(
                            companyId,
                            source,
                            size,
                            lastModified,
                            "",
                            "ERROR",
                            result.destination() == null ? Path.of("") : result.destination(),
                            Instant.now()
                    ));
                    return List.of(result.withDurationMillis(elapsedMillis(startedAt)));
                }
            }
            sha256 = hashService.sha256(source);
            if (hasProcessedInKnownLedgers(routes, companyPath, companyId, source, size, lastModified, sha256, legacyLedger)) {
                return List.of(FileProcessingResult.skipped(companyId, source,
                        FileProcessingResult.REASON_ALREADY_REGISTERED, elapsedMillis(startedAt)));
            }

            List<FileProcessingResult> results = processWorkFiles(source, companyPath, preserveInput, documentType, routes);
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
            recordRoutedLedgersIfNeeded(routes, companyPath, source, size, lastModified, sha256, results);
            cleanupSourceAfterSplit(source, preserveInput, results);
            return withDuration(results, startedAt);
        } catch (Exception exception) {
            FileProcessingResult result = handleTechnicalError(companyPath, source, preserveInput, routes, exception);
            result = result.withDurationMillis(elapsedMillis(startedAt));
            recordTechnicalFailureIfPossible(ledger, companyId, source, size, lastModified, sha256, result);
            return List.of(result);
        }
    }

    private List<FileProcessingResult> processWorkFiles(Path source, ResolvedCompanyPath companyPath,
                                                        boolean preserveInput, DocumentType documentType,
                                                        CompanyRouteDirectory routes) throws IOException {
        List<Path> workFiles = workFilesFor(source, companyPath, documentType, routes);
        boolean split = workFiles.size() > 1 || !workFiles.get(0).equals(source);
        List<FileProcessingResult> results = new ArrayList<>();
        for (Path workFile : workFiles) {
            results.add(processSingle(workFile, source, companyPath, preserveInput || split, documentType, routes));
        }
        if (split) {
            cleanupWorkFiles(workFiles);
        }
        return List.copyOf(results);
    }

    private List<Path> workFilesFor(Path source, ResolvedCompanyPath companyPath,
                                    DocumentType documentType, CompanyRouteDirectory routes) throws IOException {
        if (documentType == DocumentType.XML) {
            return List.of(source);
        }
        if (textExtractor.extractPages(source).size() <= 1) {
            return List.of(source);
        }
        if (splitter.splitSupportedPages(source).size() <= 1) {
            return List.of(source);
        }
        Path splitDirectory = nextRunDirectory(TechnicalPaths.splitWork(routes, companyPath));
        return splitter.splitSupportedPagesToFiles(source, splitDirectory);
    }

    private FileProcessingResult processSingle(Path workFile, Path originalSource, ResolvedCompanyPath companyPath,
                                               boolean preserveInput, DocumentType documentType,
                                               CompanyRouteDirectory routes) throws IOException {
        ExtractionResult extraction = extractionService.extract(workFile);
        ProcessingDecision decision;
        InvoiceData invoice;
        if (extraction.invoice().isPresent()) {
            invoice = extraction.invoice().orElseThrow();
            Optional<String> importApiPnOwner = importApiPnOwnerTaxId(originalSource);
            if (importApiPnOwner.isPresent()) {
                Optional<YearMonth> emissionMonth = parseEmissionMonth(invoice.issueDate());
                Optional<ResolvedCompanyPath> target = emissionMonth.isPresent()
                        ? routes.activePathForCustomerTaxIdAndMonth(importApiPnOwner.orElseThrow(), emissionMonth.get())
                        : routes.activePathForCustomerTaxId(importApiPnOwner.orElseThrow());
                if (target.isPresent() && !target.get().equals(companyPath)) {
                    return processSingle(workFile, originalSource, target.get(), preserveInput, documentType, routes);
                }
                if (target.isEmpty()) {
                    String fileName = fileNameBuilder.buildMissingCustomerPath(invoice, documentType.extension());
                    DestinationResult destination = destinationService.sendToMissingCustomer(workFile, companyPath,
                            fileName, preserveInput, documentType, routes);
                    return FileProcessingResult.processed(companyPath.company().id(), originalSource,
                            ProcessingStatus.WRONG_COMPANY,
                            "CNPJ da consulta PN sem caminho REST ativo no Excel", destination.destination());
                }
            } else {
                boolean wrongTaxId = shouldResolveByCustomerTaxId(invoice, companyPath);
                boolean wrongMonth = !wrongTaxId && shouldRerouteByEmissionMonth(invoice, companyPath);
                if (wrongTaxId || wrongMonth) {
                    Optional<YearMonth> emissionMonth = parseEmissionMonth(invoice.issueDate());
                    Optional<ResolvedCompanyPath> target = emissionMonth.isPresent()
                            ? routes.activePathForCustomerTaxIdAndMonth(invoice.customerTaxId(), emissionMonth.get())
                            : routes.activePathForCustomerTaxId(invoice.customerTaxId());
                    if (target.isPresent() && !target.get().equals(companyPath)) {
                        return processSingle(workFile, originalSource, target.get(), preserveInput, documentType, routes);
                    }
                    String reason = wrongMonth
                            ? "Caminho REST do mes " + emissionMonth.map(YearMonth::toString).orElse("desconhecido") + " nao configurado na planilha"
                            : "Tomador nao encontrado com caminho REST ativo no Excel";
                    String fileName = fileNameBuilder.buildMissingCustomerPath(invoice, documentType.extension());
                    DestinationResult destination = destinationService.sendToMissingCustomer(workFile, companyPath,
                            fileName, preserveInput, documentType, routes);
                    return FileProcessingResult.processed(companyPath.company().id(), originalSource,
                            ProcessingStatus.WRONG_COMPANY, reason, destination.destination());
                }
            }
            decision = new ProcessingDecisionService(companyPath.company().customerTaxId(),
                    importApiPnOwner.isPresent()).decide(invoice);
            if (decision.status() == ProcessingStatus.OK) {
                DuplicateCheck duplicate = handleFiscalDuplicate(workFile, originalSource, companyPath, invoice,
                        preserveInput, documentType, routes);
                if (duplicate.result() != null) {
                    return duplicate.result();
                }
                if (duplicate.reasonSuffix() != null) {
                    decision = new ProcessingDecision(decision.status(), decision.reviewRequired(),
                            decision.reason() + "; " + duplicate.reasonSuffix());
                }
            }
        } else {
            invoice = placeholderInvoice(extraction);
            decision = new ProcessingDecision(ProcessingStatus.UNSUPPORTED, true, extraction.reason());
        }

        String finalName = fileNameBuilder.build(invoice, decision.status(), documentType.extension());
        DestinationResult destination = destinationService.send(workFile, companyPath, decision.status(), finalName,
                preserveInput, invoice.retained(), documentType, routes);
        if (decision.status() == ProcessingStatus.OK) {
            recordFiscalDuplicateIndex(companyPath, invoice, destination.destination(), documentType, routes);
        }
        return FileProcessingResult.processed(companyPath.company().id(), originalSource, decision.status(),
                decision.reason(), destination.destination());
    }

    private DuplicateCheck handleFiscalDuplicate(Path workFile, Path originalSource, ResolvedCompanyPath companyPath,
                                                 InvoiceData invoice, boolean preserveInput,
                                                 DocumentType documentType,
                                                 CompanyRouteDirectory routes) throws IOException {
        String fiscalKey = fiscalDuplicateKey(invoice, documentType);
        if (fiscalKey.isBlank()) {
            return DuplicateCheck.none();
        }
        DuplicateInvoiceIndex legacyIndex = processingStateRegistry.duplicateIndex(PathsForCompany.logs(companyPath).resolve("duplicadas.idx"));
        List<DuplicateInvoiceIndex> indexes = processingStateRegistry.monthlyDuplicateIndexes(TechnicalPaths.companyRoot(routes, companyPath));
        String companyId = companyPath.company().id();
        var portal = findDuplicate(indexes, legacyIndex, companyId, fiscalKey, LayoutType.PORTAL_NACIONAL);
        var abrasf = findDuplicate(indexes, legacyIndex, companyId, fiscalKey, LayoutType.ABRASF_ISSNET);
        if (invoice.layout() == LayoutType.PORTAL_NACIONAL && portal.isPresent()) {
            discardWorkFile(workFile, preserveInput);
            return new DuplicateCheck(FileProcessingResult.processed(companyId, originalSource,
                    ProcessingStatus.DUPLICATE,
                    "Portal Nacional duplicada descartada; copia operacional equivalente ja existe",
                    portal.orElseThrow().destination()), null);
        }
        if (invoice.layout() == LayoutType.ABRASF_ISSNET) {
            if (portal.isPresent()) {
                discardWorkFile(workFile, preserveInput);
                return new DuplicateCheck(FileProcessingResult.processed(companyId, originalSource,
                        ProcessingStatus.DUPLICATE,
                        "ABRASF duplicada descartada; Portal Nacional equivalente ja existe",
                        portal.orElseThrow().destination()), null);
            }
            if (abrasf.isPresent()) {
                discardWorkFile(workFile, preserveInput);
                return new DuplicateCheck(FileProcessingResult.processed(companyId, originalSource,
                        ProcessingStatus.DUPLICATE,
                        "ABRASF duplicada descartada; copia operacional equivalente ja existe",
                        abrasf.orElseThrow().destination()), null);
            }
        }
        if (invoice.layout() == LayoutType.PORTAL_NACIONAL) {
            if (abrasf.isPresent()) {
                Path destination = abrasf.orElseThrow().destination();
                if (canDeleteOperationalDuplicate(companyPath, destination)) {
                    Files.deleteIfExists(destination);
                    return new DuplicateCheck(null, "ABRASF duplicada anterior removida por Portal Nacional equivalente");
                }
                return new DuplicateCheck(null,
                        "ABRASF duplicada anterior apontava para fora da pasta da empresa; nao removida automaticamente");
            }
        }
        return DuplicateCheck.none();
    }

    private static java.util.Optional<DuplicateInvoiceIndex.Entry> findDuplicate(List<DuplicateInvoiceIndex> indexes,
                                                                                DuplicateInvoiceIndex legacyIndex,
                                                                                String companyId,
                                                                                String fiscalKey,
                                                                                LayoutType layout) throws IOException {
        for (DuplicateInvoiceIndex index : indexes) {
            var current = index.find(companyId, fiscalKey, layout);
            if (current.isPresent() && destinoExisteNoDisco(current.orElseThrow())) {
                return current;
            }
        }
        var legacy = legacyIndex.find(companyId, fiscalKey, layout);
        if (legacy.isPresent() && destinoExisteNoDisco(legacy.orElseThrow())) {
            return legacy;
        }
        return java.util.Optional.empty();
    }

    /**
     * Verifica se o arquivo destino registrado no indice de duplicatas ainda existe.
     * Se foi apagado manualmente (ou por outro processo), nao consideramos mais como
     * duplicata - o arquivo precisa ser reprocessado para reaparecer no destino.
     * Isso evita o bug de "tudo virou duplicata" depois que alguem limpa as pastas REST.
     */
    private static boolean destinoExisteNoDisco(DuplicateInvoiceIndex.Entry entrada) {
        Path destino = entrada.destination();
        if (destino == null || destino.toString().isBlank()) {
            return false;
        }
        return Files.exists(destino);
    }

    private static void discardWorkFile(Path workFile, boolean preserveInput) throws IOException {
        if (!preserveInput) {
            Files.deleteIfExists(workFile);
        }
    }

    private static boolean canDeleteOperationalDuplicate(ResolvedCompanyPath companyPath, Path destination) {
        if (destination == null || destination.toString().isBlank()) {
            return false;
        }
        Path normalizedDestination = destination.toAbsolutePath().normalize();
        return Files.isRegularFile(normalizedDestination)
                && (isInside(PathsForCompany.processed(companyPath), normalizedDestination)
                || isInside(companyPath.root().resolve(DestinationService.RETAINED_FOLDER), normalizedDestination)
                || isInside(PathsForCompany.cancelled(companyPath), normalizedDestination)
                || isInside(companyPath.root().resolve(DestinationService.MISSING_CUSTOMER_FOLDER), normalizedDestination)
                || isInsideDocumentDestination(companyPath, normalizedDestination));
    }

    private static boolean isInsideDocumentDestination(ResolvedCompanyPath companyPath, Path normalizedDestination) {
        for (DocumentType documentType : DocumentType.values()) {
            Path documentRoot = documentType.folderUnder(companyPath.root());
            if (isInside(documentRoot.resolve(companyPath.company().folders().processed()), normalizedDestination)
                    || isInside(documentRoot.resolve(DestinationService.RETAINED_FOLDER), normalizedDestination)
                    || isInside(documentRoot.resolve(companyPath.company().folders().cancelled()), normalizedDestination)
                    || isInside(documentRoot.resolve(DestinationService.MISSING_CUSTOMER_FOLDER), normalizedDestination)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInside(Path directory, Path file) {
        return file.startsWith(directory.toAbsolutePath().normalize());
    }

    private void recordFiscalDuplicateIndex(ResolvedCompanyPath companyPath, InvoiceData invoice,
                                            Path destination, DocumentType documentType,
                                            CompanyRouteDirectory routes) throws IOException {
        String fiscalKey = fiscalDuplicateKey(invoice, documentType);
        if (fiscalKey.isBlank()) {
            return;
        }
        processingStateRegistry.duplicateIndex(TechnicalPaths.duplicateIndex(routes, companyPath))
                .record(companyPath.company().id(), fiscalKey, invoice.layout(), destination);
    }

    private static boolean shouldRerouteByEmissionMonth(InvoiceData invoice, ResolvedCompanyPath companyPath) {
        if (companyPath.month().isEmpty()) return false;
        if (TextNormalizer.digitsOnly(invoice.customerTaxId()).isBlank()) return false;
        return parseEmissionMonth(invoice.issueDate())
                .map(em -> !em.equals(companyPath.month().get()))
                .orElse(false);
    }

    private static Optional<YearMonth> parseEmissionMonth(String issueDate) {
        if (issueDate == null || issueDate.isBlank()) return Optional.empty();
        String[] parts = issueDate.split("/");
        if (parts.length == 3 && parts[2].length() == 4) {
            try {
                return Optional.of(YearMonth.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException | java.time.DateTimeException ignored) {
            }
        }
        return Optional.empty();
    }

    private static boolean isWrongFolder(InvoiceData invoice, ResolvedCompanyPath companyPath) {
        String invoiceTaxId = TextNormalizer.digitsOnly(invoice.customerTaxId());
        String expectedTaxId = TextNormalizer.digitsOnly(companyPath.company().customerTaxId());
        return !invoiceTaxId.isBlank() && !expectedTaxId.isBlank() && !invoiceTaxId.equals(expectedTaxId);
    }

    private static boolean shouldResolveByCustomerTaxId(InvoiceData invoice, ResolvedCompanyPath companyPath) {
        return companyPath.company().sourceOnly() || isWrongFolder(invoice, companyPath);
    }

    private static Optional<String> importApiPnOwnerTaxId(Path source) {
        String fileName = source.getFileName() == null ? "" : source.getFileName().toString();
        Matcher matcher = IMPORT_API_PN_FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private static String fiscalDuplicateKey(InvoiceData invoice) {
        String number = normalizedNumber(invoice.number());
        String providerTaxId = TextNormalizer.digitsOnly(invoice.providerTaxId());
        String providerName = normalizedName(invoice.providerName());
        String customerTaxId = TextNormalizer.digitsOnly(invoice.customerTaxId());
        String issueDate = normalizedDate(invoice.issueDate());
        String serviceValue = normalizedMoney(invoice.serviceValue());
        String netValue = normalizedMoney(invoice.netValue());
        if (number.isBlank() || providerTaxId.isBlank() || providerName.isBlank()
                || customerTaxId.isBlank() || issueDate.isBlank()
                || serviceValue.isBlank() || netValue.isBlank()) {
            return "";
        }
        return String.join("|", number, providerTaxId, providerName, customerTaxId, issueDate, serviceValue, netValue);
    }

    private static String fiscalDuplicateKey(InvoiceData invoice, DocumentType documentType) {
        String fiscalKey = fiscalDuplicateKey(invoice);
        if (fiscalKey.isBlank() || documentType == DocumentType.PDF) {
            return fiscalKey;
        }
        return documentType.name() + "|" + fiscalKey;
    }

    private static String normalizedNumber(String number) {
        String digits = TextNormalizer.digitsOnly(number);
        String stripped = digits.replaceFirst("^0+", "");
        return stripped.isEmpty() ? digits : stripped;
    }

    private static String normalizedName(String value) {
        return TextNormalizer.normalize(value).replaceAll("[^A-Z0-9]+", " ").replaceAll("\\s+", " ").strip();
    }

    private static String normalizedDate(String date) {
        if (date == null || date.isBlank()) {
            return "";
        }
        String[] parts = date.split("/");
        if (parts.length == 3 && parts[2].length() == 4) {
            return parts[2] + parts[1] + parts[0];
        }
        return date.replaceAll("\\D", "");
    }

    private static String normalizedMoney(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private FileProcessingResult handleTechnicalError(ResolvedCompanyPath companyPath, Path source,
                                                      boolean preserveInput, CompanyRouteDirectory routes,
                                                      Exception exception) {
        try {
            DestinationResult destination = destinationService.sendTechnicalError(source, companyPath, preserveInput,
                    DocumentType.from(source), routes);
            return FileProcessingResult.failed(companyPath.company().id(), source,
                    "Erro tecnico no processamento", destination.destination(), exception);
        } catch (Exception moveException) {
            exception.addSuppressed(moveException);
            return FileProcessingResult.failed(companyPath.company().id(), source,
                    "Erro tecnico no processamento; falha ao mover para NAO SUPORTADOS", null, exception);
        }
    }

    public List<FileProcessingResult> timeoutResult(InputCandidate candidate, boolean preserveInput,
                                                    CompanyRouteDirectory routes, long timeoutSeconds) {
        Instant startedAt = Instant.now();
        String reason = "Timeout de " + timeoutSeconds + "s excedido";
        TimeoutException exception = new TimeoutException(reason);
        FileProcessingResult result = handleTechnicalError(candidate.companyPath(), candidate.source(),
                preserveInput, routes, exception);
        return List.of(result.withDurationMillis(elapsedMillis(startedAt)));
    }

    private FileProcessingResult handleOversizedFile(ResolvedCompanyPath companyPath, Path source,
                                                     boolean preserveInput, CompanyRouteDirectory routes,
                                                     long size) {
        try {
            String fileName = "ARQUIVO_MUITO_GRANDE_" + source.getFileName();
            DestinationResult destination = destinationService.send(source, companyPath, ProcessingStatus.UNSUPPORTED,
                    fileName, preserveInput, false, DocumentType.PDF, routes);
            String reason = "Arquivo excede limite de " + (MAX_FILE_SIZE_BYTES / 1_048_576L)
                    + "MB: " + (size / 1_048_576L) + "MB";
            return FileProcessingResult.failed(companyPath.company().id(), source, reason,
                    destination.destination(), new IllegalArgumentException(reason));
        } catch (Exception moveException) {
            return FileProcessingResult.failed(companyPath.company().id(), source,
                    "Arquivo excede limite de tamanho; falha ao mover para NAO SUPORTADOS", null, moveException);
        }
    }

    private FileProcessingResult handleTooManyPagesFile(ResolvedCompanyPath companyPath, Path source,
                                                        boolean preserveInput, CompanyRouteDirectory routes,
                                                        int pages) {
        try {
            String fileName = "PAGINAS_DEMAIS_" + source.getFileName();
            DestinationResult destination = destinationService.send(source, companyPath, ProcessingStatus.UNSUPPORTED,
                    fileName, preserveInput, false, DocumentType.PDF, routes);
            String reason = "PDF excede limite de paginas " + MAX_PAGE_COUNT + ": " + pages + " paginas";
            return FileProcessingResult.failed(companyPath.company().id(), source, reason,
                    destination.destination(), new IllegalArgumentException(reason));
        } catch (Exception moveException) {
            return FileProcessingResult.failed(companyPath.company().id(), source,
                    "PDF excede limite de paginas; falha ao mover para NAO SUPORTADOS", null, moveException);
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

    private static List<FileProcessingResult> withDuration(List<FileProcessingResult> results, Instant startedAt) {
        long durationMillis = elapsedMillis(startedAt);
        return results.stream()
                .map(result -> result.withDurationMillis(durationMillis))
                .toList();
    }

    private static long elapsedMillis(Instant startedAt) {
        return Math.max(0L, Duration.between(startedAt, Instant.now()).toMillis());
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

    public int repairMisnamedOutputFiles(ResolvedCompanyPath companyPath) {
        int count = 0;
        count += repairDocumentFolders(companyPath, DocumentType.PDF);
        count += repairDocumentFolders(companyPath, DocumentType.XML);
        count += repairFolder(PathsForCompany.processed(companyPath), ProcessingStatus.OK);
        count += repairFolder(companyPath.root().resolve(DestinationService.RETAINED_FOLDER), ProcessingStatus.OK);
        count += repairFolder(PathsForCompany.cancelled(companyPath), ProcessingStatus.CANCELLED);
        return count;
    }

    private int repairDocumentFolders(ResolvedCompanyPath companyPath, DocumentType documentType) {
        Path documentRoot = documentType.folderUnder(companyPath.root());
        int count = 0;
        count += repairFolder(documentRoot.resolve(companyPath.company().folders().processed()), ProcessingStatus.OK);
        count += repairFolder(documentRoot.resolve(DestinationService.RETAINED_FOLDER), ProcessingStatus.OK);
        count += repairFolder(documentRoot.resolve(companyPath.company().folders().cancelled()), ProcessingStatus.CANCELLED);
        return count;
    }

    private int repairFolder(Path folder, ProcessingStatus status) {
        if (!Files.isDirectory(folder)) return 0;
        int count = 0;
        try (var stream = Files.list(folder)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(InvoiceProcessingPipeline::isSupportedDocument)
                    .toList();
            for (Path file : files) {
                try {
                    if (repairFileIfNeeded(file, status)) count++;
                } catch (Exception ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return count;
    }

    private boolean repairFileIfNeeded(Path file, ProcessingStatus status) throws IOException {
        ExtractionResult extraction = extractionService.extract(file);
        if (extraction.invoice().isEmpty()) return false;
        InvoiceData invoice = extraction.invoice().orElseThrow();
        if (invoice.providerName().isBlank()) return false;
        String correctName = fileNameBuilder.build(invoice, status, DocumentType.from(file).extension());
        String currentName = file.getFileName().toString();
        if (currentName.equals(correctName)) return false;
        Path newPath = file.getParent().resolve(correctName);
        if (Files.exists(newPath)) return false;
        Files.move(file, newPath);
        return true;
    }

    private static boolean isSupportedDocument(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return fileName.endsWith(".pdf") || fileName.endsWith(".xml");
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

    private void recordRoutedLedgersIfNeeded(CompanyRouteDirectory routes, ResolvedCompanyPath sourcePath,
                                             Path source, long size, Instant lastModified, String sha256,
                                             List<FileProcessingResult> results) throws IOException {
        for (FileProcessingResult result : results) {
            if (result.companyId() == null || result.companyId().equals(sourcePath.company().id())) {
                continue;
            }
            var targetPath = routes.activePathForCompanyId(result.companyId());
            if (targetPath.isEmpty() || targetPath.orElseThrow().equals(sourcePath)) {
                continue;
            }
            processingStateRegistry.ledger(TechnicalPaths.ledger(routes, targetPath.orElseThrow()))
                    .record(new LedgerEntry(
                            result.companyId(),
                            source,
                            size,
                            lastModified,
                            sha256,
                            String.valueOf(result.status()),
                            result.destination() == null ? Path.of("") : result.destination(),
                            Instant.now()
                    ));
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

    private boolean hasProcessedInKnownLedgers(CompanyRouteDirectory routes, ResolvedCompanyPath companyPath,
                                               String companyId, Path source, long size, Instant lastModified,
                                               String sha256, ProcessingLedger legacyLedger) throws IOException {
        for (ProcessingLedger knownLedger : processingStateRegistry.monthlyLedgers(TechnicalPaths.companyRoot(routes, companyPath))) {
            if (knownLedger.hasProcessed(companyId, source, size, lastModified, sha256)) {
                return true;
            }
        }
        return legacyLedger.hasProcessed(companyId, source, size, lastModified, sha256);
    }

    private record DuplicateCheck(FileProcessingResult result, String reasonSuffix) {
        static DuplicateCheck none() {
            return new DuplicateCheck(null, null);
        }
    }
}
