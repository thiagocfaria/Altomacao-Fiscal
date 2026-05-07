# Ferramentas e Skills — Renomeador NFS-e

Catalogo das ferramentas e skills ativas neste projeto.
Ultima atualizacao: 2026-05-07.

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
| Funcao | Protege a separacao descrita em `AGENTS.md`: `config`, `extraction`, `files`, `layout`, `ledger`, `naming`, `parser`, `pdf`, `processing`, `text` e orquestracao em `app`/pipeline |
| Mapa que conhece | Arquitetura completa de pacotes do `nfse-renomeador` |

### implementacao-java

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/implementacao-java/SKILL.md` |
| Ativa quando | Qualquer mudanca relevante de codigo Java |
| Funcao | Garante: try-with-resources em PDDocument, Pattern estatico, records imutaveis, Optional em vez de null, sem System.out |
| Checklist | `mvn compile` → `mvn test`; quando tocar pipeline, parser ou PDF real, tambem `mvn verify -Pintegration` |

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
| Funcao | Escada de testes: unitario → integracao PDF real → e2e sintetico → soak watcher → JMH quando houver mudanca relevante em extracao/parser |
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
| Funcao | KPIs com faixas verde/amarelo/vermelho, como registrar corretamente e como medir heap do watcher com `jcmd` |
| KPIs | `ms_por_nota`, `ms_por_lote`, heap watcher 30min, latencia WatchService |

### pesquisa-tecnica-java

| Item | Detalhe |
|---|---|
| Arquivo | `.claude/skills/pesquisa-tecnica-java/SKILL.md` |
| Ativa quando | Antes de nova dep Maven, novo MCP, nova lib |
| Funcao | Checklist de licenca/manutencao, candidatos mapeados (PDFBox alternativas, WatchService alternativas, OCR V2) |
| Decisoes | Registrar sempre em `docs/operacao/MCP_AVALIACAO.md` |

---

## Stack Java — resumo do pom.xml

| Area | Dependencias ativas |
|---|---|
| Runtime | Java 17 |
| PDF | Apache PDFBox |
| CLI | Picocli |
| YAML | Jackson YAML |
| Excel | Apache POI |
| Logging | SLF4J/Logback e ponte `log4j-to-slf4j` para logs internos do POI |
| Testes | JUnit 5 e AssertJ |

JMH e ferramentas como SpotBugs/Checkstyle nao estao no baseline atual do `pom.xml`. Se entrarem no projeto, precisam passar pelo checklist de `docs/operacao/MCP_AVALIACAO.md` e ser registradas aqui.

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
- [ ] Rodar `mvn verify -Pintegration` quando houver PDFs reais disponiveis para validacao

### Nunca precisa fazer manualmente

- skills sao carregadas automaticamente pelo Claude Code ao abrir este diretorio
- jdtls e iniciado automaticamente pelo plugin ao abrir arquivo `.java`
- codebase-memory-mcp e global e nao precisa de configuracao adicional aqui
