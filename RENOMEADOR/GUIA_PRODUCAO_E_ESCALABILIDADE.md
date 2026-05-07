# Guia de Produção e Escalabilidade — Renomeador NFS-e

> Documento técnico de referência. Descreve a arquitetura atual, os gargalos identificados, o plano de fila de processamento e o roteiro completo de evolução para produção em grande escala.

---

## Sumário

1. [Visão Geral do Sistema](#1-visão-geral-do-sistema)
2. [Arquitetura Atual](#2-arquitetura-atual)
3. [Fluxo de Processamento](#3-fluxo-de-processamento)
4. [Gargalos Identificados](#4-gargalos-identificados)
5. [Modelo de Fila — Como Implementar](#5-modelo-de-fila--como-implementar)
6. [Guia de Performance por Componente](#6-guia-de-performance-por-componente)
7. [Robustez e Proteção do Servidor](#7-robustez-e-proteção-do-servidor)
8. [Observabilidade — Logs, Métricas e Alertas](#8-observabilidade--logs-métricas-e-alertas)
9. [Ledger — Evolução do Banco de Controle](#9-ledger--evolução-do-banco-de-controle)
10. [Roteiro de Evolução Priorizado](#10-roteiro-de-evolução-priorizado)
11. [Checklist de Produção](#11-checklist-de-produção)

---

## 1. Visão Geral do Sistema

O Renomeador NFS-e é um sistema Java 17 que processa PDFs de Notas Fiscais de Serviço Eletrônicas (NFS-e), extrai os dados fiscais relevantes de cada nota, renomeia o arquivo no padrão operacional e o move para a pasta REST correta da empresa tomadora, conforme configuração da planilha Excel.

### O que o sistema faz hoje

```
PDF na pasta de entrada
        │
        ▼
Detecção de Layout (Portal Nacional / ABRASF / Não suportado)
        │
        ▼
Extração de Texto (PDFBox)
        │
        ▼
Parse dos Campos Fiscais (número, data, CNPJ, prestador, tomador, valor)
        │
        ▼
Decisão de Status (OK / Cancelada / Retenção / Empresa errada)
        │
        ▼
Roteamento por CNPJ + Mês de Emissão
        │
        ▼
Renomeação e Movimentação para pasta REST correta
        │
        ▼
Registro no Ledger (anti-reprocessamento por SHA-256)
```

### Volumes esperados em produção

| Escala | PDFs/dia | PDFs/mês | Empresas ativas |
|--------|----------|----------|-----------------|
| Pequena (atual) | 50–200 | 1.500 | 50–100 |
| Média | 500–2.000 | 30.000 | 200–500 |
| Grande | 5.000–20.000 | 300.000 | 1.000+ |

O sistema atual está adequado para a escala pequena. Para escala média e grande, as mudanças descritas neste documento são necessárias.

---

## 2. Arquitetura Atual

```
App (CLI Picocli)
├── BatchCommand          → BatchModeRunner
├── WatchCommand          → WatchModeRunner
└── ConfigCommand         → ImportExcelCommand / CheckConfigCommand

config/
├── CompanyRegistryLoader          Lê empresas.yaml (Jackson YAML)
├── CompanyRegistry                Lista de CompanyConfig (imutável)
├── CompanyConfig                  Dados de uma empresa (record)
├── MonthlyPathResolver            Resolve pasta por estratégia e mês
├── CompanyRouteDirectory          Índice de roteamento por CNPJ + mês
├── ResolvedCompanyPath            Empresa + pasta resolvida + mês
└── excel/ExcelCompanyImporter     Lê planilha Excel → empresas.yaml

pipeline/
├── InputScanner                   Lista PDFs nas pastas de entrada
├── InvoiceProcessingPipeline      Orquestra: hash → arquivo → extrai → decide → move
├── DestinationService             Move arquivo para pasta destino correta
└── ProcessingLogger               Escreve log operacional TSV

extraction/
├── InvoiceExtractionService       Texto → Layout → Parser → InvoiceData
└── InvoiceSplitter                Separa PDF de múltiplas páginas

pdf/PdfTextExtractor               PDFBox: extrai texto por página

files/
├── FileHashService                SHA-256 do arquivo
├── OriginalArchiveService         Copia original para backend/originais/
└── StableFileGuard                Aguarda arquivo parar de ser escrito

ledger/
├── ProcessingLedger               TSV de arquivos já processados (anti-dup)
└── DuplicateInvoiceIndex          TSV de chaves fiscais (Portal × ABRASF)

parser/
├── PortalNacionalParser           Regex para DANFSe v1.0
├── AbrasfIssnetParser             Regex para NFS-e municipal ABRASF/ISSNet
└── ParserSupport                  Funções auxiliares comuns

layout/LayoutDetector              Classifica layout pelo texto extraído
naming/FileNameBuilder             Gera nome final com 150 chars máx
```

---

## 3. Fluxo de Processamento

### Modo Batch

```
1. Carrega empresas.yaml
2. Para cada empresa habilitada:
   a. Resolve pasta de entrada para o mês (ou todos os meses)
3. InputScanner lista todos os PDFs das pastas de entrada
4. Para cada PDF (SEQUENCIALMENTE — gargalo principal):
   a. StableFileGuard: aguarda estabilidade (size + timestamp)
   b. FileHashService: calcula SHA-256
   c. ProcessingLedger: verifica se já foi processado
   d. OriginalArchiveService: copia para backend/originais/
   e. InvoiceSplitter: verifica se precisa separar por páginas
   f. Para cada página/arquivo de trabalho:
      i.  PdfTextExtractor: extrai texto completo
      ii. LayoutDetector: classifica o layout
      iii. Parser: extrai campos fiscais
      iv. ProcessingDecisionService: decide status
      v.  DestinationService: move para pasta correta
      vi. ProcessingLedger: registra como processado
5. ProcessingLogger: escreve resumo final
```

### Modo Watch (Watcher contínuo)

```
1. Carrega empresas.yaml (ou importa planilha)
2. Registra WatchKey para cada pasta de entrada
3. Loop infinito:
   a. A cada 500ms: verifica se config mudou → recarrega se necessário
   b. Aguarda evento WatchService (ENTRY_CREATE / ENTRY_MODIFY)
   c. Ao receber evento: processa o arquivo (mesmo fluxo do batch)
   d. Em OVERFLOW: faz varredura completa da pasta
```

---

## 4. Gargalos Identificados

Esta seção lista os problemas encontrados na revisão do código, ordenados por gravidade.

---

### 4.1 Processamento Sequencial (CRÍTICO)

**Localização:** `BatchModeRunner.run()` — loop `for (var candidate : candidates)`

**Problema:** Cada PDF é processado um após o outro, em sequência. Um servidor com 8 núcleos processa como se tivesse 1. Em lote de 500 PDFs com 300ms médio cada, o tempo total é **150 segundos** — poderia ser **19 segundos** com 8 threads.

**Solução:** Substituir o loop sequencial por `ExecutorService` com pool de threads dimensionado ao servidor (veja seção 5).

---

### 4.2 Ledger com Busca Linear (CRÍTICO para escala)

**Localização:** `ProcessingLedger.hasProcessed()` — leitura completa do arquivo TSV a cada verificação

**Problema:** Com 100 PDFs processados/dia, em 1 ano o ledger tem ~36.000 linhas. Para processar 200 PDFs novos, o sistema lê 36.000 linhas × 200 verificações = **7,2 milhões de comparações**. O tempo de processamento cresce de forma quadrática O(n²).

**Solução imediata:** Carregar ledger em `HashSet<String>` uma vez no início do batch.  
**Solução definitiva:** Migrar para SQLite com índice B-tree (veja seção 9).

---

### 4.3 PDF Lido Múltiplas Vezes (SÉRIO)

**Localização:** `InvoiceSplitter` + `PdfTextExtractor`

**Problema:** Para um PDF de 10 páginas, o PDFBox o lê até **3 vezes**:
1. `extractPages()` para contar páginas
2. `splitSupportedPages()` para verificar layouts
3. `splitSupportedPagesToFiles()` para realmente separar

Cada leitura carrega o PDF inteiro na memória. Para um PDF de 20MB, isso significa **60MB de heap** e **3× o tempo de I/O**.

**Solução:** Extrair texto uma única vez, passar a representação em memória para os demais componentes (veja seção 6.3).

---

### 4.4 Ledger sem Lock de Arquivo (RISCO DE CORRUPÇÃO)

**Localização:** `ProcessingLedger.record()` e `DuplicateInvoiceIndex.record()`

**Problema:** Se dois processos ou duas threads escrevem no mesmo arquivo TSV simultaneamente, as linhas ficam intercaladas ou corrompidas. Isso pode acontecer hoje se o usuário rodar `batch` duas vezes ao mesmo tempo ou se o watcher processar o mesmo arquivo duas vezes por eventos simultâneos.

**Solução imediata:** `FileLock` via `FileChannel` antes de qualquer escrita.  
**Solução definitiva:** SQLite (serializa operações por design).

---

### 4.5 Ausência de Timeout por Arquivo (RISCO DE TRAVAMENTO)

**Localização:** `InvoiceProcessingPipeline.processSingle()` — sem timeout

**Problema:** PDFs malformados, criptografados ou muito grandes podem fazer o PDFBox travar indefinidamente, parando todo o processamento. Em modo watcher, isso trava o servidor até reinicialização manual.

**Solução:** Envolver cada arquivo em `CompletableFuture.get(timeout, TimeUnit.SECONDS)` com timeout de 60 segundos.

---

### 4.6 Sem Limite de Tamanho de Arquivo

**Localização:** `InputScanner` — sem verificação de tamanho antes de enfileirar

**Problema:** Um PDF de 2GB sendo processado pode esgotar a heap JVM. O limite padrão do PDFBox é RAM disponível.

**Solução:** Verificar `Files.size()` antes de enfileirar. Arquivos acima de 500MB vão para `revisar/` com aviso no log.

---

### 4.7 Logs sem Rotação

**Localização:** `ProcessingLogger` e `logback.xml`

**Problema:** Logs operacionais crescem indefinidamente. Com 200 PDFs/dia, o arquivo `execucao.log` acumula ~1MB/dia = 365MB/ano por empresa. Com 100 empresas = 36GB de logs.

**Solução:** Configurar `RollingFileAppender` no Logback com rotação diária e retenção de 90 dias.

---

### 4.8 Busca Linear por CNPJ no Roteamento

**Localização:** `CompanyRouteDirectory.activePathForCustomerTaxId()` — itera lista

**Problema:** Com 500 empresas ativas, cada roteamento percorre até 500 entradas. Para 2.000 PDFs/dia com roteamento ativo, são 1 milhão de iterações desnecessárias por dia.

**Solução:** Construir `Map<String, ResolvedCompanyPath>` indexado por CNPJ (e por CNPJ+mês) na inicialização do `CompanyRouteDirectory`.

---

### 4.9 InputScanner Sem Limite de Memória

**Localização:** `InputScanner.scan()` — carrega lista completa

**Problema:** Uma pasta com 50.000 PDFs carrega 50.000 objetos `InputCandidate` antes de processar o primeiro arquivo.

**Solução:** Usar `Stream` com `Files.walk()` paginado ou processar via fila com backpressure (veja seção 5).

---

## 5. Modelo de Fila — Como Implementar

Esta é a mudança mais importante para produção em escala. O objetivo é:

- **Não bloquear o servidor** — limitar quantos PDFs são processados ao mesmo tempo
- **Usar múltiplos núcleos** — processar em paralelo com pool de threads seguro
- **Ter backpressure** — se a fila encher, parar de aceitar novos arquivos temporariamente
- **Garantir ordenação justa** — o primeiro arquivo que chegou é o primeiro processado (FIFO)

### 5.1 Arquitetura da Fila

```
InputScanner
    │
    │ (produz candidatos)
    ▼
BlockingQueue<InputCandidate>     ← backpressure automático
    │              │              │
    ▼              ▼              ▼
Worker-1       Worker-2       Worker-N      (pool de threads)
    │              │              │
    └──────────────┴──────────────┘
                   │
                   ▼
          ResultCollector
                   │
                   ▼
        ProcessingLogger (resumo)
```

### 5.2 Implementação — BatchModeRunner com Fila

```java
// Dimensionamento recomendado:
// - Threads = número de núcleos × 2 (I/O bound, não CPU bound)
// - Fila = 4× o número de threads (evita starvation sem usar muita memória)
// - Timeout por arquivo = 60 segundos

private static final int THREAD_MULTIPLIER = 2;
private static final int QUEUE_FACTOR = 4;
private static final long FILE_TIMEOUT_SECONDS = 60L;

public ProcessingSummary run(Path config, Optional<String> companyId,
                             Optional<YearMonth> month, boolean homologation) throws IOException {
    CompanyRouteDirectory routes = companyPaths.loadRoutes(config, companyId, month);
    List<InputCandidate> candidates = scanner.scan(routes.monitoredPaths());

    int threads = Math.max(2, Runtime.getRuntime().availableProcessors() * THREAD_MULTIPLIER);
    int queueCapacity = threads * QUEUE_FACTOR;

    BlockingQueue<InputCandidate> queue = new LinkedBlockingQueue<>(queueCapacity);
    ExecutorService executor = Executors.newFixedThreadPool(threads,
            new NamedThreadFactory("nfse-worker"));

    // Produtor: alimenta a fila com candidatos
    Thread producer = new Thread(() -> {
        for (InputCandidate candidate : candidates) {
            try {
                queue.put(candidate);   // bloqueia se a fila estiver cheia (backpressure)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }, "nfse-producer");

    // Consumidores: workers que processam os arquivos
    List<Future<List<FileProcessingResult>>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
        futures.add(executor.submit(() -> processQueue(queue, routes, homologation)));
    }

    producer.start();
    producer.join();

    // Enviar sinal de fim para cada worker
    for (int i = 0; i < threads; i++) {
        queue.put(InputCandidate.POISON_PILL);
    }

    // Coletar resultados
    ProcessingSummary summary = new ProcessingSummary();
    for (Future<List<FileProcessingResult>> future : futures) {
        for (FileProcessingResult result : future.get(5, TimeUnit.MINUTES)) {
            summary.record(result);
        }
    }

    executor.shutdown();
    return summary;
}
```

### 5.3 Dimensionamento Recomendado por Servidor

| Servidor | Núcleos | Threads workers | Tamanho da fila | PDFs/hora esperados |
|----------|---------|-----------------|-----------------|---------------------|
| Básico | 2 | 4 | 16 | ~720 |
| Intermediário | 4 | 8 | 32 | ~1.440 |
| Produção | 8 | 16 | 64 | ~2.880 |
| Alta carga | 16 | 32 | 128 | ~5.760 |

> **Regra prática:** o processamento é I/O-bound (espera disco/rede), não CPU-bound. Por isso o número de threads pode ser 2× o número de núcleos sem sobrecarregar a CPU.

### 5.4 Timeout por Arquivo

Cada processamento deve ter um limite de tempo. PDF problemático não pode travar um worker para sempre:

```java
private FileProcessingResult processWithTimeout(InputCandidate candidate,
                                                 CompanyRouteDirectory routes,
                                                 boolean homologation) {
    Future<List<FileProcessingResult>> task = timeoutExecutor.submit(
            () -> pipeline.process(candidate, homologation, routes));
    try {
        List<FileProcessingResult> results = task.get(FILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return results.isEmpty() ? FileProcessingResult.skipped(...) : results.get(0);
    } catch (TimeoutException e) {
        task.cancel(true);  // interrompe a thread do PDFBox
        log.warn("Timeout ao processar {}: arquivo movido para revisao", candidate.source());
        return FileProcessingResult.failed(..., "Timeout de " + FILE_TIMEOUT_SECONDS + "s excedido", ...);
    }
}
```

### 5.5 Limite de Tamanho de Arquivo

Verificar antes de enfileirar, nunca depois:

```java
private static final long MAX_FILE_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB

// Em InputScanner ou no início do processamento:
long size = Files.size(candidate.source());
if (size > MAX_FILE_SIZE_BYTES) {
    log.warn("Arquivo ignorado por exceder {}MB: {}", MAX_FILE_SIZE_BYTES / 1_048_576,
             candidate.source().getFileName());
    destinationService.sendToReview(candidate.source(), candidate.companyPath(),
            "ARQUIVO_MUITO_GRANDE_" + ..., false);
    continue;
}
```

---

## 6. Guia de Performance por Componente

### 6.1 CompanyRouteDirectory — Índice por CNPJ

**Problema atual:** busca linear O(n) por CNPJ e por CNPJ+mês.  
**Solução:** construir `Map` indexado uma única vez na criação do objeto.

```java
// Substituir:
public Optional<ResolvedCompanyPath> activePathForCustomerTaxId(String taxId) {
    for (ResolvedCompanyPath path : activePaths) { ... }  // O(n) — ruim
}

// Por:
private final Map<String, ResolvedCompanyPath> indexByCnpj;
private final Map<String, ResolvedCompanyPath> indexByCnpjAndMonth; // chave: "cnpj|yyyy-MM"

// Construído no construtor compact com um único loop O(n):
this.indexByCnpj = buildCnpjIndex(activePaths);
this.indexByCnpjAndMonth = buildCnpjMonthIndex(activePaths);

// Busca passa a ser O(1):
public Optional<ResolvedCompanyPath> activePathForCustomerTaxId(String taxId) {
    return Optional.ofNullable(indexByCnpj.get(TextNormalizer.digitsOnly(taxId)));
}
```

### 6.2 ProcessingLedger — Cache em Memória

**Problema atual:** lê arquivo completo em disco para cada verificação.  
**Solução imediata (sem mudar o formato de arquivo):** carregar o ledger em `Set` uma vez por execução.

```java
public final class ProcessingLedger {
    private final Path path;
    private Set<String> loadedHashes = null;  // cache lazy

    public boolean hasProcessed(String companyId, Path source, long size,
                                Instant lastModified, String sha256) throws IOException {
        if (loadedHashes == null) {
            loadedHashes = loadAllHashes();  // lê arquivo UMA vez
        }
        return loadedHashes.contains(sha256);
    }

    public void record(LedgerEntry entry) throws IOException {
        appendLine(entry);  // escrita ainda síncrona — OK por ora
        if (loadedHashes != null) {
            loadedHashes.add(entry.sha256());  // mantém cache atualizado
        }
    }

    private Set<String> loadAllHashes() throws IOException {
        if (!Files.exists(path)) return new HashSet<>();
        try (Stream<String> lines = Files.lines(path)) {
            return lines.map(line -> line.split("\t"))
                        .filter(parts -> parts.length >= 3)
                        .map(parts -> parts[2])
                        .collect(Collectors.toCollection(HashSet::new));
        }
    }
}
```

**Ganho:** reduz de O(n²) para O(n) na etapa de verificação de duplicatas.

### 6.3 PdfTextExtractor — Extrair Uma Vez

**Problema atual:** PDFBox abre o mesmo arquivo até 3 vezes durante split.  
**Solução:** extrair texto e páginas em uma única abertura, retornar estrutura completa.

```java
// Novo método que extrai tudo em uma passada:
public record PdfContent(String fullText, List<String> pageTexts, int pageCount) {}

public PdfContent extractAll(Path pdfFile) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        List<String> pages = new ArrayList<>(doc.getNumberOfPages());
        for (int p = 1; p <= doc.getNumberOfPages(); p++) {
            stripper.setStartPage(p);
            stripper.setEndPage(p);
            pages.add(stripper.getText(doc));
        }
        String full = String.join("\n", pages);
        return new PdfContent(full, List.copyOf(pages), doc.getNumberOfPages());
    }
}
```

**Ganho:** elimina 2 das 3 aberturas do PDF — reduz I/O em 66% e uso de heap em 50% para PDFs com split.

### 6.4 InvoiceSplitter — Reutilizar PdfContent

```java
// Atual: abre PDF 2x dentro do splitter
// Novo: recebe PdfContent já extraído como parâmetro

public List<Path> splitToFiles(Path source, PdfContent content,
                               Path workDirectory) throws IOException {
    // usa content.pageTexts() para detectar layouts
    // usa PDDocument apenas para gravar os arquivos separados (1 abertura)
}
```

### 6.5 Logback — Rotação e Performance

No arquivo `src/main/resources/logback.xml`, configurar rotação de logs:

```xml
<configuration>
  <!-- Appender assíncrono: não bloqueia a thread de processamento -->
  <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>1024</queueSize>
    <discardingThreshold>0</discardingThreshold>  <!-- nunca descartar mensagens -->
    <appender-ref ref="ROLLING_FILE"/>
  </appender>

  <!-- Rotação diária com retenção de 90 dias e limite de 1GB total -->
  <appender name="ROLLING_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/renomeador.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>logs/renomeador.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
      <maxHistory>90</maxHistory>
      <totalSizeCap>1GB</totalSizeCap>
    </rollingPolicy>
    <encoder>
      <pattern>%d{ISO8601} [%thread] %-5level %logger{36} — %msg%n</pattern>
    </encoder>
  </appender>

  <!-- Suprimir ruído do PDFBox/POI que não agrega valor operacional -->
  <logger name="org.apache.pdfbox" level="ERROR"/>
  <logger name="org.apache.poi"    level="ERROR"/>
  <logger name="org.apache.fontbox" level="ERROR"/>

  <root level="INFO">
    <appender-ref ref="ASYNC_FILE"/>
  </root>
</configuration>
```

---

## 7. Robustez e Proteção do Servidor

### 7.1 Lock de Arquivo no Ledger

Para garantir que dois processos nunca escrevam no ledger simultaneamente:

```java
private void appendLine(LedgerEntry entry) throws IOException {
    Files.createDirectories(path.getParent());
    try (FileChannel channel = FileChannel.open(path,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
         FileLock lock = channel.lock()) {            // bloqueia até ter o lock exclusivo
        String line = formatLine(entry) + "\n";
        channel.write(ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8)));
    }
}
```

**Por que isso importa:** sem este lock, dois workers processando arquivos diferentes da mesma empresa podem escrever linhas intercaladas no ledger, corrompendo entradas e fazendo o sistema reprocessar arquivos já concluídos.

### 7.2 Validação de Espaço em Disco

Antes de iniciar processamento em lote:

```java
private static final long MIN_FREE_SPACE_BYTES = 500L * 1024 * 1024; // 500 MB

private void validateDiskSpace(List<ResolvedCompanyPath> paths) throws IOException {
    for (ResolvedCompanyPath companyPath : paths) {
        Path root = companyPath.root().toAbsolutePath();
        if (!Files.exists(root)) continue;
        FileStore store = Files.getFileStore(root);
        long free = store.getUsableSpace();
        if (free < MIN_FREE_SPACE_BYTES) {
            throw new IllegalStateException(
                "Espaço em disco insuficiente em " + root +
                ": " + (free / 1_048_576) + "MB livres, mínimo " +
                (MIN_FREE_SPACE_BYTES / 1_048_576) + "MB necessários");
        }
    }
}
```

### 7.3 Shutdown Gracioso

Garantir que arquivos em processamento sejam concluídos antes do encerramento:

```java
// Em App.main():
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    log.info("Encerrando Renomeador NFS-e — aguardando conclusão dos processos em andamento...");
    globalExecutor.shutdown();
    try {
        if (!globalExecutor.awaitTermination(120, TimeUnit.SECONDS)) {
            log.warn("Timeout no encerramento — alguns arquivos podem não ter sido finalizados");
            globalExecutor.shutdownNow();
        }
    } catch (InterruptedException e) {
        globalExecutor.shutdownNow();
        Thread.currentThread().interrupt();
    }
    log.info("Renomeador encerrado.");
}, "nfse-shutdown-hook"));
```

### 7.4 Circuit Breaker — Proteger contra Falhas em Cascata

Se um tipo de arquivo continuar falhando repetidamente, o sistema deve parar de tentar processar arquivos similares por um período:

```java
// Implementação simples sem dependência externa:
public final class SimpleCircuitBreaker {
    private final int failureThreshold;
    private final Duration resetDelay;
    private final AtomicInteger failures = new AtomicInteger(0);
    private volatile Instant openedAt = null;

    public boolean isOpen() {
        if (openedAt == null) return false;
        if (Instant.now().isAfter(openedAt.plus(resetDelay))) {
            failures.set(0);
            openedAt = null;
            return false;
        }
        return true;
    }

    public void recordSuccess() { failures.set(0); }

    public void recordFailure() {
        if (failures.incrementAndGet() >= failureThreshold) {
            openedAt = Instant.now();
            log.error("Circuit breaker aberto: {} falhas consecutivas. " +
                      "Pausando por {}.", failureThreshold, resetDelay);
        }
    }
}
```

---

## 8. Observabilidade — Logs, Métricas e Alertas

### 8.1 Log Operacional Estruturado

O log operacional atual é TSV (tab-separated). Para ambientes de produção, adicionar suporte a JSON facilita análise por ferramentas externas (Excel, Power BI, etc.):

**Formato TSV atual (mantido para compatibilidade):**
```
2026-05-07T10:00:00Z	OK	250ms	empresa_x	NFSE_9_PRESTADOR_07.05.2026_1500.00.pdf
```

**Campos a adicionar no log:**
```
timestamp | status | duracaoMs | empresa | arquivo_origem | arquivo_destino |
layout | numero_nota | cnpj_tomador | mes_emissao | tamanho_bytes | erro
```

Isso permite responder perguntas como:
- "Quantas notas de abril foram processadas esta semana?"
- "Qual empresa tem mais notas em revisar?"
- "Qual é o tempo médio de processamento por layout?"

### 8.2 Métricas Operacionais a Registrar

Adicionar ao resumo final de cada execução:

```
=== RESUMO DE EXECUÇÃO ===
Data/hora       : 07/05/2026 10:00:00
Duração total   : 45s
Arquivos lidos  : 200
  OK            : 178 (89,0%)
  Canceladas    :   5 ( 2,5%)
  Revisão       :  12 ( 6,0%)
  Ignorados     :   3 ( 1,5%)
  Erros         :   2 ( 1,0%)
Throughput      : 4,4 PDFs/s
Tempo médio/PDF : 225ms
Maior arquivo   : 18,4 MB (NFSE_0045...)
PDFs divididos  :   4 (total 11 páginas)
Empresas ativas : 124
==========================
```

### 8.3 Alertas Automáticos via Log

Adicionar entradas de WARN/ERROR automaticamente quando:

```java
// Taxa de erro > 10% da execução:
if (summary.errors() > summary.total() * 0.10) {
    log.warn("ALERTA: taxa de erros elevada: {}/{} ({:.1f}%)",
             summary.errors(), summary.total(),
             100.0 * summary.errors() / summary.total());
}

// Arquivo em TOMADOR NAO ENCONTRADO há mais de 7 dias:
if (pendingDays > 7) {
    log.warn("ALERTA: arquivo {} aguarda roteamento há {} dias — " +
             "verifique se o caminho REST está cadastrado na planilha",
             fileName, pendingDays);
}

// Disco com menos de 20% livre:
if (freePercent < 20) {
    log.warn("ALERTA: espaço livre em disco: {}% — considere arquivar logs antigos",
             freePercent);
}
```

---

## 9. Ledger — Evolução do Banco de Controle

O ledger atual (arquivo TSV) é adequado para até ~50.000 entradas. Para volumes maiores, a migração para SQLite é o próximo passo natural — sem necessidade de servidor de banco de dados.

### 9.1 Por que SQLite?

| Característica | TSV atual | SQLite |
|----------------|-----------|--------|
| Busca por SHA-256 | O(n) — lento | O(log n) — rápido |
| Escrita concorrente | Corrupção possível | Serializada, segura |
| Transações | Não há | ACID completo |
| Tamanho em disco | Cresce linear | Compacto com índices |
| Instalação | Nenhuma | Nenhuma (embutido no JAR) |
| Migração de dados | Simples | Simples |

### 9.2 Esquema Recomendado

```sql
-- Tabela principal de controle de processamento
CREATE TABLE IF NOT EXISTS processados (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    empresa_id  TEXT    NOT NULL,
    caminho     TEXT    NOT NULL,
    sha256      TEXT    NOT NULL,
    tamanho     INTEGER NOT NULL,
    modificado  TEXT    NOT NULL,   -- ISO-8601
    status      TEXT    NOT NULL,
    destino     TEXT,
    processado_em TEXT NOT NULL,    -- ISO-8601
    UNIQUE (sha256)                 -- garante idempotência por conteúdo
);
CREATE INDEX IF NOT EXISTS idx_sha256     ON processados(sha256);
CREATE INDEX IF NOT EXISTS idx_empresa    ON processados(empresa_id);
CREATE INDEX IF NOT EXISTS idx_processado ON processados(processado_em);

-- Tabela de índice de duplicatas fiscais
CREATE TABLE IF NOT EXISTS duplicatas (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    empresa_id  TEXT NOT NULL,
    chave_fiscal TEXT NOT NULL,
    layout      TEXT NOT NULL,
    destino     TEXT NOT NULL,
    registrado_em TEXT NOT NULL,
    UNIQUE (empresa_id, chave_fiscal, layout)
);
CREATE INDEX IF NOT EXISTS idx_chave ON duplicatas(empresa_id, chave_fiscal);
```

### 9.3 Dependência a Adicionar no pom.xml

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.46.0.0</version>
</dependency>
```

### 9.4 Compatibilidade na Migração

O `InvoiceProcessingPipeline` já checa tanto o ledger novo quanto o legado (`legacyLedger`). A migração pode ser feita gradualmente:

1. Criar `SqliteLedger` implementando a mesma interface de `ProcessingLedger`
2. Ao iniciar, migrar entradas do TSV existente para o SQLite automaticamente
3. Novos registros vão apenas para SQLite
4. Verificação ainda consulta ambos (por um período de transição)

---

## 10. Roteiro de Evolução Priorizado

### Fase 1 — Produção Segura (implementar agora, antes de grande escala)

| # | Mudança | Arquivo | Risco sem ela |
|---|---------|---------|---------------|
| 1.1 | Lock no ledger (`FileLock`) | `ProcessingLedger` | Corrupção de dados em uso simultâneo |
| 1.2 | Timeout por arquivo (60s) | `InvoiceProcessingPipeline` | Trava em PDF problemático |
| 1.3 | Limite de tamanho de arquivo (500MB) | `InputScanner` | OutOfMemory |
| 1.4 | Cache do ledger em `HashSet` | `ProcessingLedger` | Lentidão com ledger grande |
| 1.5 | Rotação de logs (90 dias) | `logback.xml` | Disco cheio em meses |
| 1.6 | Validação de espaço em disco | `BatchModeRunner` | Falha no meio do processamento |

### Fase 2 — Performance (quando o volume superar 500 PDFs/dia)

| # | Mudança | Arquivo | Ganho esperado |
|---|---------|---------|----------------|
| 2.1 | Pool de threads com fila (`BlockingQueue`) | `BatchModeRunner` | 4–8× throughput |
| 2.2 | Índice por CNPJ no roteamento (`HashMap`) | `CompanyRouteDirectory` | Roteamento de O(n) para O(1) |
| 2.3 | Extração única de PDF por arquivo | `PdfTextExtractor` + `InvoiceSplitter` | 66% menos I/O em PDFs multi-página |
| 2.4 | Métricas e resumo detalhado | `BatchModeRunner` | Visibilidade operacional |
| 2.5 | Shutdown gracioso | `App.main()` | Zero perda de arquivos em andamento |

### Fase 3 — Grande Escala (quando superar 5.000 PDFs/dia ou 500 empresas)

| # | Mudança | Arquivo | Benefício |
|---|---------|---------|-----------|
| 3.1 | Migrar ledger para SQLite | `ProcessingLedger`, `DuplicateInvoiceIndex` | Escala indefinidamente |
| 3.2 | Circuit breaker simples | `InvoiceProcessingPipeline` | Proteção contra falhas em cascata |
| 3.3 | Watcher com fila de eventos deduplicada | `WatchModeRunner` | Evita processar mesmo arquivo 3× |
| 3.4 | Log operacional com campos estendidos | `ProcessingLogger` | Relatórios e auditoria |
| 3.5 | Alertas automáticos no log | `BatchModeRunner` | Visibilidade de anomalias |
| 3.6 | Política de retenção do ledger (purge anual) | `ProcessingLedger` | Controle de crescimento |

### Fase 4 — Excelência Operacional (futuro)

| # | Mudança | Benefício |
|---|---------|-----------|
| 4.1 | Métricas com Micrometer (JVM + custom) | Monitoramento em tempo real |
| 4.2 | Testes de carga automatizados | Detectar regressões de performance |
| 4.3 | Modo de dry-run detalhado | Simular processamento sem mover arquivos |
| 4.4 | API REST mínima (Spring Boot embedded) | Permitir acionar por outras ferramentas |
| 4.5 | Exportação de relatório mensal em Excel/CSV | Relatório para contador |

---

## 11. Checklist de Produção

Use esta lista antes de implantar em produção em grande escala.

### Configuração

- [ ] `empresas.yaml` validado com `config check`
- [ ] Todas as pastas REST acessíveis (permissão de escrita verificada)
- [ ] Espaço em disco disponível: mínimo 2GB livres na unidade dos originais
- [ ] JVM configurada com heap adequado: `-Xmx512m` para escala pequena, `-Xmx2g` para escala grande
- [ ] Rotação de logs configurada no `logback.xml`
- [ ] `empresas-dga-temp.yaml` removido (arquivo de teste temporário)

### Testes antes do primeiro uso em produção

- [ ] `mvn test` — todos os 113 testes passando
- [ ] `batch --homologacao` na pasta real — verificar nomes e destinos
- [ ] Verificar `backend/empresas/<id>/execucao.log` — sem erros inesperados
- [ ] Verificar `backend/empresas/<id>/revisar/` — PDFs em revisão fazem sentido
- [ ] Reexecução imediata — todos os arquivos ignorados por ledger (0 reprocessados)
- [ ] PDF com data de mês diferente — verificar roteamento automático correto

### Operação contínua

- [ ] Escolher entre `batch` agendado (via Agendador de Tarefas do Windows) **ou** `watch` contínuo — **nunca ambos ao mesmo tempo com o mesmo `empresas.yaml`**
- [ ] Se usar `watch --planilha`: a planilha é reimportada automaticamente ao salvar
- [ ] Se usar `batch` agendado: rodar `config import-excel` antes de cada `batch`
- [ ] Monitorar crescimento dos logs em `backend/` mensalmente
- [ ] Arquivar/purgar ledger anualmente se ultrapassar 200.000 linhas

### Parâmetros de JVM recomendados

```bat
:: Para uso normal (até 200 PDFs/dia):
java -Xmx512m -Xms128m -jar renomeador-nfse-0.1.0-SNAPSHOT.jar batch ...

:: Para lotes grandes (500+ PDFs/dia):
java -Xmx2g -Xms512m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 ^
     -jar renomeador-nfse-0.1.0-SNAPSHOT.jar batch ...

:: Para watch contínuo de longa duração:
java -Xmx1g -Xms256m -XX:+UseG1GC ^
     -Djava.awt.headless=true ^
     -jar renomeador-nfse-0.1.0-SNAPSHOT.jar watch ...
```

---

## Resumo das Prioridades

```
AGORA (antes de grande escala):
  1. Lock no ledger                → evita corrupção de dados
  2. Timeout por arquivo (60s)    → evita travamento do servidor
  3. Limite de tamanho (500MB)    → evita OutOfMemory
  4. Cache do ledger em HashSet   → evita lentidão com ledger grande
  5. Rotação de logs              → evita disco cheio

PRÓXIMO (quando volume aumentar):
  6. Pool de threads com fila     → multiplica throughput por 4–8×
  7. Índice por CNPJ (HashMap)    → roteamento instantâneo
  8. Extração única de PDF        → menos I/O, menos memória

MAIS TARDE (grande escala):
  9. Ledger SQLite                → escala indefinidamente
 10. Circuit breaker              → proteção contra falhas em cascata
 11. Métricas e alertas           → visibilidade operacional
```

---

*Documento gerado em 07/05/2026. Atualizar conforme a fase de implantação avançar.*
