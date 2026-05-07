# Cadastros Mensais Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gerar e importar abas mensais `CADASTRO ABRIL` a `CADASTRO DEZEMBRO`, com caminhos mensais separados e certificado fixo por cliente.

**Architecture:** O preparador da planilha cria as abas mensais a partir da aba de abril e limpa apenas os caminhos mensais das abas futuras. O importador seleciona a aba mensal com base no `--mes` ou na data atual, mantendo fallback para a aba antiga `CADASTRO`.

**Tech Stack:** Java 17, Apache POI, Maven/JUnit 5, AssertJ.

---

### Task 1: Planilha Mensal

**Files:**
- Modify: `src/main/java/br/com/nfse/renomeador/config/excel/ExcelWorkbookPreparer.java`
- Test: `src/test/java/br/com/nfse/renomeador/config/excel/ExcelWorkbookPreparerTest.java`

- [x] Write failing tests for monthly tabs, preserved April paths, blank future paths, shared certificate fields, and combined `ENTRADA/SAIDA` header.
- [x] Run focused test and verify it fails.
- [x] Implement monthly sheet generation and column migration.
- [x] Run focused test and verify it passes.

### Task 2: Importacao Pelo Mes

**Files:**
- Modify: `src/main/java/br/com/nfse/renomeador/config/excel/ExcelCompanyImporter.java`
- Modify: `src/main/java/br/com/nfse/renomeador/App.java`
- Modify: `src/main/java/br/com/nfse/renomeador/app/WatchModeRunner.java`
- Test: `src/test/java/br/com/nfse/renomeador/config/excel/ExcelCompanyImporterTest.java`

- [x] Write failing tests for choosing `CADASTRO MAIO` or `CADASTRO JUNHO`.
- [x] Run focused importer test and verify it fails.
- [x] Implement monthly sheet selection and pass `--mes` from CLI/watch.
- [x] Run focused tests and verify they pass.

### Task 3: Planilha Real, Validacao e Publicacao

**Files:**
- Modify: `PLANILHA_FISCAL.xlsm`
- Modify: `SITUACAO_ATUAL.md`

- [x] Build jar.
- [x] Regenerate `PLANILHA_FISCAL.xlsm`.
- [x] Validate workbook package and LibreOffice conversion.
- [x] Run `mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration`.
- [ ] Stage all current repository changes, commit, and push `main` to `origin`.
