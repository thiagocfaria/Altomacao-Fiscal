---
name: implementacao-java
description: Use em qualquer alteracao relevante de codigo Java neste projeto — parser, pipeline, watcher, IO ou config.
---

# Implementacao Java

Voce implementa com foco em corretude, performance e codigo limpo no renomeador de NFS-e.

## Regras maes

1. Corretude primeiro, performance depois — mas performance importa de verdade neste projeto.
2. `PDDocument` **sempre** em try-with-resources — fechar no finally manual e bug de leak de memoria.
3. `Pattern.compile(...)` **sempre** como `static final` — compilar regex dentro de metodo chamado por arquivo e desperdicio em lote grande.
4. Records Java para `domain/` — sem setters, sem estado mutavel, campos `final` por padrao.
5. `Optional<T>` quando campo pode nao existir — nunca retornar `null` de metodo de parser.
6. Sem `e.printStackTrace()` — sempre logar com SLF4J: `log.error("contexto", e)`.
7. Sem `System.out.println` — apenas SLF4J em todo o codigo de producao.
8. Nenhum warning de compilador aceito sem justificativa explicita.

## Stack obrigatoria e versoes

| Biblioteca | Versao | Proposito |
|---|---|---|
| Java | **17 LTS** | Records, sealed types, switch patterns |
| PDFBox | **3.x** | Extracao de texto de PDF textual |
| Picocli | **4.x** | CLI com modos `--mode=watch` e `--mode=batch` |
| Jackson + jackson-dataformat-yaml | **2.x** | Leitura de `empresas.yaml` |
| SLF4J + Logback | **2.x / 1.5.x** | Logging async via `AsyncAppender` |
| JUnit 5 + AssertJ | ultima estavel | Testes — nunca JUnit 4 |
| JMH | **1.37+** | Benchmark de parser (quando mudar logica de extracao) |

## Padroes de codigo obrigatorios

### PDFBox — ciclo de vida correto

```java
// CORRETO
try (PDDocument doc = Loader.loadPDF(arquivo)) {
    String texto = new PDFTextStripper().getText(doc);
    // processar texto
}

// ERRADO — leak de PDDocument
PDDocument doc = Loader.loadPDF(arquivo);
String texto = new PDFTextStripper().getText(doc);
// ... esqueceu de fechar
```

### Regex — compilar uma vez

```java
// CORRETO — compilar como constante estatica
private static final Pattern NUMERO_NF = Pattern.compile("Numero da NFS-e\\s+(\\d+)");

public Optional<String> extrairNumero(String texto) {
    Matcher m = NUMERO_NF.matcher(texto);
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
}

// ERRADO — compilar dentro do metodo chamado por cada arquivo
public Optional<String> extrairNumero(String texto) {
    Pattern p = Pattern.compile("Numero da NFS-e\\s+(\\d+)"); // compilado N vezes
    Matcher m = p.matcher(texto);
    return m.find() ? Optional.of(m.group(1)) : Optional.empty();
}
```

### Domain record imutavel

```java
// CORRETO
public record NotaFiscal(
    String numero,
    LocalDate dataEmissao,
    String cnpjPrestador,
    String nomePrestador,
    String cnpjTomador,
    BigDecimal valorServico,
    BigDecimal valorLiquido,
    Layout layout,
    boolean retida,
    boolean cancelada
) {}

// ERRADO — POJO com setters
public class NotaFiscal {
    private String numero;
    public void setNumero(String n) { this.numero = n; }
    // ...
}
```

### Valor monetario

```java
// CORRETO — BigDecimal para qualquer valor fiscal
private static final Pattern VALOR = Pattern.compile("R\\$\\s*([\\d.,]+)");

public Optional<BigDecimal> extrairValor(String texto, String rotulo) {
    // normalizar: trocar '.' milhar por '' e ',' decimal por '.'
    return Optional.of(raw)
        .map(v -> v.replace(".", "").replace(",", "."))
        .map(BigDecimal::new);
}

// ERRADO — double para valor fiscal (arredondamento)
double valor = Double.parseDouble("1.288,52"); // NumerFormatException + imprecisao
```

## Checklist de saida obrigatorio

```bash
mvn compile                         # sem warnings de compilacao
mvn test                            # todos os unitarios passando
mvn spotbugs:check                  # sem bugs criticos (quando plugin configurado)
mvn checkstyle:check                # formato padrao (quando plugin configurado)
```

Com mudanca em parser ou extracao, adicionar:
```bash
mvn verify -Pintegration            # testes com PDFs reais do lote piloto
```

## Quando nao abstrair

- Dois parsers (PortalNacional e Abrasf) nao justificam framework de plugin — Strategy direto basta.
- Um unico Ledger nao justifica interface — so criar `LedgerPort` quando houver segundo impl real.
- Pipeline com 6 etapas fixas nao precisa de DI container — injecao manual no `Main` e suficiente na V1.

## Fechamento padrao

Ao final, responda:

1. o que foi alterado e por que
2. quais regras acima foram aplicadas
3. quais checagens foram rodadas
4. se ha risco de regressao no hot path (parser, watcher, pipeline)
