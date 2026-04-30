# Ferramentas e Skills — Renomeador NFS-e

Catalogo completo de todas as ferramentas e skills ativas neste projeto.
Ultima atualizacao: 2026-04-30.

---

## LSP (Language Server Protocol)

### jdtls-lsp — Eclipse JDT Language Server

| Item | Detalhe |
|---|---|
| Plugin | `jdtls-lsp@claude-plugins-official` v1.0.0 |
| Binario | `/home/u/.local/bin/jdtls` |
| Instalacao jdtls | `/home/u/.local/share/jdtls/` (versao 1.58.0-202604151538) |
| Escopo | **Somente este projeto** — desativado globalmente, ativo via `.claude/settings.json` |
| Ativa para | Arquivos `.java` |
| Nao afeta | Projeto INTERFACE (Rust) — sem conflito |
| Funcoes | Navegacao por tipo, diagnostico de erro, refactor, hover de assinatura, go-to-definition |
| Requerimento | Java 17 disponivel em `/usr/lib/jvm/java-17-openjdk-amd64` |

**Como verificar se esta ativo:**
Abrir qualquer `.java` no Claude Code — o LSP inicializa automaticamente e aparece diagnostico em tempo real.

**Como desativar temporariamente:**
No `.claude/settings.json`, trocar `"jdtls-lsp@claude-plugins-official": true` para `false`.

---

## MCP (Model Context Protocol)

### codebase-memory-mcp

| Item | Detalhe |
|---|---|
| Escopo | Global (instalado no usuario, funciona em todos os projetos) |
| Status neste projeto | Ativo e ja indexado para este repositorio Java |
| Para reindexar | `index_repository` apontando para a raiz deste projeto |
| `.cbmignore` | Configurado para excluir `NF MODELO ABRASP E PORTAL NACIONAL/` e `docs/operacao/` |
| Funcoes | Grafo de chamadas, blast radius, navegacao estrutural, busca por simbolo |
| Documentacao | `docs/operacao/MCP_AVALIACAO.md` |

**Quando reindexar:**
Apos mudancas relevantes de codigo Java ou reorganizacao de pacotes.
Comando: via MCP `codebase-memory-mcp` com `index_repository`.

---

## Skills personalizadas deste projeto

As skills ficam em `.claude/skills/`. O Claude Code as carrega automaticamente nesta sessao.

### guardiao-arquitetura-nfse

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/guardiao-arquitetura-nfse/SKILL.md` |
| Ativa quando | Mudanca em nome de pacote, fronteira entre camadas, novo modulo Java |
| Funcao | Protege a separacao `domain/parser/pipeline/watcher/config/io/app` |
| Mapa que conhece | Arquitetura completa de pacotes do `nfse-renomeador` |

### implementacao-java

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/implementacao-java/SKILL.md` |
| Ativa quando | Qualquer mudanca relevante de codigo Java |
| Funcao | Garante: try-with-resources em PDDocument, Pattern estatico, records imutaveis, Optional em vez de null, sem System.out |
| Checklist | `mvn compile` → `mvn test` → `mvn spotbugs:check` → `mvn checkstyle:check` |

### layout-nfse

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/layout-nfse/SKILL.md` |
| Ativa quando | Toque em parser, extracao de campo, deteccao de layout, retencao, cancelada |
| Funcao | **Skill-dominio**: frases-assinatura de cada layout, posicao de cada campo, regras de retencao, NF cancelada, separacao multi-nota |
| Golden values | Campos verificados manualmente em NF 5, NF 14, NF 55034, NotasPdf.pdf |

### equipe-testes-nfse

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/equipe-testes-nfse/SKILL.md` |
| Ativa quando | Validacao, regressao, mudanca em parser/pipeline, risco de pular etapas |
| Funcao | Escada de testes: unit → integracao PDF real → e2e sintetico → soak watcher → JMH |
| KPI principal | `ms_por_nota` (ms por NF, modo batch, JVM aquecida) |

### validacao-extracao-pdf

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/validacao-extracao-pdf/SKILL.md` |
| Ativa quando | Mudanca em parser ou novo campo — protege contra regressao silenciosa |
| Funcao | Golden values dos 10 PDFs do lote piloto; templates de teste JUnit 5 |
| Lote piloto | `NF MODELO ABRASP E PORTAL NACIONAL/` (10 arquivos, 2 layouts, 1 multi-nota, 1 cancelada) |

### validacao-performance-java

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/validacao-performance-java/SKILL.md` |
| Ativa quando | Mudanca no hot path: parser, watcher, pipeline |
| Funcao | KPIs com faixas verde/amarelo/vermelho, como registrar corretamente, JMH e jcmd |
| KPIs | `ms_por_nota`, `ms_por_lote`, heap watcher 30min, latencia WatchService |

### pesquisa-tecnica-java

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/pesquisa-tecnica-java/SKILL.md` |
| Ativa quando | Antes de nova dep Maven, novo MCP, nova lib |
| Funcao | Checklist de licenca/manutencao, candidatos mapeados (PDFBox alternativas, WatchService alternativas, OCR V2) |
| Decisoes | Registrar sempre em `docs/operacao/MCP_AVALIACAO.md` |

---

## Stack Java — resumo para o pom.xml

```xml
<!-- Java 17 -->
<java.version>17</java.version>

<!-- PDF -->
<dependency>
  <groupId>org.apache.pdfbox</groupId>
  <artifactId>pdfbox</artifactId>
  <version>3.0.x</version>
</dependency>

<!-- CLI -->
<dependency>
  <groupId>info.picocli</groupId>
  <artifactId>picocli</artifactId>
  <version>4.7.x</version>
</dependency>

<!-- Config YAML -->
<dependency>
  <groupId>com.fasterxml.jackson.dataformat</groupId>
  <artifactId>jackson-dataformat-yaml</artifactId>
  <version>2.x</version>
</dependency>

<!-- Logging -->
<dependency>
  <groupId>ch.qos.logback</groupId>
  <artifactId>logback-classic</artifactId>
  <version>1.5.x</version>
</dependency>

<!-- Testes -->
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.x</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.assertj</groupId>
  <artifactId>assertj-core</artifactId>
  <version>3.x</version>
  <scope>test</scope>
</dependency>

<!-- Benchmark (escopo test) -->
<dependency>
  <groupId>org.openjdk.jmh</groupId>
  <artifactId>jmh-core</artifactId>
  <version>1.37</version>
  <scope>test</scope>
</dependency>
```

---

## Passos manuais necessarios

### Ja feitos (nao e necessario repetir)

- [x] jdtls binario instalado em `/home/u/.local/share/jdtls/`
- [x] jdtls wrapper em `/home/u/.local/bin/jdtls`
- [x] Plugin `jdtls-lsp@claude-plugins-official` instalado (user scope)
- [x] Plugin desativado globalmente (nao afeta INTERFACE)
- [x] Plugin ativo somente aqui via `.claude/settings.json`
- [x] Skills criadas e ativas em `.claude/skills/`
- [x] AGENTS.md, CLAUDE.md, SITUACAO_ATUAL.md criados
- [x] `.cbmignore` configurado

### A fazer ao clonar em outra maquina

- [ ] Instalar Java 17 e Maven
- [ ] Verificar se `.claude/settings.json` deve ser usado nessa maquina
- [ ] Rodar `mvn test`
- [ ] Reindexar o projeto no `codebase-memory-mcp`, se ele estiver disponivel
- [ ] Ajustar o arquivo externo `empresas.yaml` para a pasta real de homologacao
- [ ] Configurar Maven profiles `integration` e `jmh`

### Nunca precisa fazer manualmente

- skills sao carregadas automaticamente pelo Claude Code ao abrir este diretorio
- jdtls e iniciado automaticamente pelo plugin ao abrir arquivo `.java`
- codebase-memory-mcp e global e nao precisa de configuracao adicional aqui
