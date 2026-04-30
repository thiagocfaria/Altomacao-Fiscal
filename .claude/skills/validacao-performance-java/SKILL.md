---
name: validacao-performance-java
description: Use quando a mudanca pode mexer em latencia por nota, uso de memoria no watcher, throughput do lote ou alocacao no hot path.
---

# Validacao de Performance Java

Voce protege a verdade dos numeros do renomeador de NFS-e e impede falsa melhoria.

## Regras maes

1. Nunca declarar melhoria sem medicao real comparavel (antes e depois, mesma maquina).
2. Nunca comparar rodadas com JVM nao aquecida — sempre fazer warmup antes de medir.
3. Nunca usar `System.currentTimeMillis()` em benchmark — usar JMH ou `Instant.now()` com calculo `Duration`.
4. Nunca aceitar melhoria de uma unica rodada — exige pelo menos duas comparaveis.
5. Sempre registrar os dois ultimos resultados lado a lado com data.
6. Nunca ignorar crescimento de heap no watcher — leak em modo watch longa duracao e bug critico.

## KPIs deste projeto (maquina de referencia)

| KPI | Baseline esperado | Amarelo | Vermelho |
|---|---|---|---|
| `ms_por_nota` — processamento de 1 PDF | < 200 ms | 200–999 ms | >= 1000 ms |
| `ms_por_lote` — lote piloto de 10 PDFs | < 2 s | 2–10 s | >= 10 s |
| heap watcher em soak de 30min (sem GC forcado) | < 150 MB | 150–300 MB | >= 300 MB ou crescimento monotono |
| tempo de deteccao (evento WatchService → inicio do pipeline) | < 100 ms | 100–500 ms | >= 500 ms |

KPI principal: **`ms_por_nota`** — medido no pipeline com PDF real, modo batch, JVM aquecida.

## Quando entrar

- mudanca em `PortalNacionalParser` ou `AbrasfParser` (texto, regex, PDFBox calls)
- mudanca em `StabilityChecker` (polling interval, lock check)
- mudanca em `Ledger` (SHA-256, I/O de arquivo de estado)
- mudanca em `SafeFileMover` (atomic move, colisao)
- qualquer nova dependencia Maven que toque o caminho critico
- suspeita de leak de memoria em modo watch

## Caminho critico de latencia

```
FolderWatcher.onNewFile()
  → StabilityChecker.aguardar()
  → Pipeline.processar()
      → PdfReader.lerTexto()            ← PDFBox — maior custo de CPU e IO
      → LayoutDetector.detectar()       ← regex — deve ser O(1) por pagina
      → Splitter.dividir()              ← PDFBox split — custo proporcional ao numero de paginas
      → Parser.extrair()                ← regex — deve ser O(linhas do texto)
      → Validator.validar()             ← comparacao de CNPJ — O(1)
      → FileNamer.montar()              ← string ops — O(1)
  → SafeFileMover.mover()               ← IO atomico — custo de disco
  → Ledger.registrar()                  ← IO de append — custo de disco
```

Sem alocacao desnecessaria neste caminho: nao criar `StringBuilder` dentro de loop por pagina, nao recompilar Pattern.

## Ferramentas de medicao

| Ferramenta | Nivel | Quando usar |
|---|---|---|
| JMH + `@Benchmark` | Funcao isolada | Auditar parser especifico |
| log de tempo no pipeline | Sistema completo | Medir `ms_por_nota` com PDF real |
| `jcmd <PID> VM.native_memory scale=MB` | Heap/off-heap | Soak do watcher |
| `jcmd <PID> GC.heap_info` | GC | Confirmar GC nao esta forcado |
| `-Xss512k` + `-Xmx256m` (testes) | JVM flags | Simular ambiente restrito da empresa |

## Como registrar corretamente

Formato padrao para o doc da fase ou sub-fase:

```
Resultado anterior comparavel:
  ms_por_nota: 450 ms
  heap watcher 30min: 95 MB
  data: 2026-04-30 | maquina: Ubuntu 24.04, Java 17, i7

Resultado desta sub-fase:
  ms_por_nota: 180 ms
  heap watcher 30min: 85 MB
  data: [preencher] | maquina: [preencher]

Faixa: verde (reducao de 60%)
```

## Hot path — o que nao fazer

```java
// ERRADO — Pattern recompilado para cada arquivo do lote
public String extrairNumero(String texto) {
    return Pattern.compile("Numero da NFS-e\\s+(\\d+)").matcher(texto)...;
}

// CORRETO — compilado uma vez
private static final Pattern NUMERO = Pattern.compile("Numero da NFS-e\\s+(\\d+)");

// ERRADO — StringBuilder criado dentro de loop de paginas
for (PDPage page : doc.getPages()) {
    StringBuilder sb = new StringBuilder(); // alocacao por pagina
}

// CORRETO — PDFTextStripper extrai todo o texto de uma vez, sem loop manual
String texto = new PDFTextStripper().getText(doc);
```

## Quando rodar JMH

Nao e para o loop diario. Use quando:
- fechar uma fase importante (Fase 3 — parser, Fase 4 — watcher)
- suspeitar que otimizacao introduziu regressao
- adicionar novo parser e querer baseline

Foco: rode JMH em `PortalNacionalParser` e `AbrasfParser` separadamente.
`SafeFileMover` e `Ledger` dependem de IO real — medir com integracao, nao JMH.

## Fechamento padrao

Ao final, responda:

1. qual metrica foi medida
2. em qual maquina e com qual JVM
3. qual foi o resultado anterior e o atual
4. em qual faixa o resultado caiu (verde / amarelo / vermelho)
5. se o fechamento esta liberado ou bloqueado
