# SITUACAO ATUAL

Atualizado em 30/04/2026.

## 1. Onde estamos

**Fase:** V1 em implementacao. O nucleo Java ja existe e o proximo passo e o processamento `batch` completo.

- Repositorio Git inicializado e preparado para publicar no GitHub.
- Projeto Java 17/Maven criado em `src/main/java`.
- Lote piloto disponivel em `NF MODELO ABRASP E PORTAL NACIONAL/`.
- `NotasPdf.pdf` tem 7 paginas/notas na amostra atual.
- MCP `codebase-memory-mcp` indexado para este projeto.
- LSP Java documentado via `.claude/settings.json`.

## 2. O que ja foi implementado

- [x] Extracao de texto PDF com PDFBox.
- [x] Detector de layout: Portal Nacional, ABRASF/ISSNet, sem texto e nao suportado.
- [x] Parsers iniciais de Portal Nacional e ABRASF/ISSNet.
- [x] Separacao logica e fisica de PDF agrupado por pagina segura.
- [x] Validacao de CNPJ do tomador.
- [x] Deteccao de cancelamento.
- [x] Deteccao conservadora de retencao e conflito de retencao.
- [x] Nomeacao no padrao `NFSE_<numero>_<prestador>_<dataAAAAMMDD>_<valor>.pdf`.
- [x] Configuracao externa `empresas.yaml` com Jackson YAML.
- [x] Resolucao de pasta por estrategia `atual`, `informado`, `lista` e `direto`.
- [x] Ledger persistente basico.
- [x] Hash SHA-256.
- [x] Guarda de estabilidade de arquivo.
- [x] Preservacao de original com colisao.
- [x] Logback configurado.
- [x] Jar Maven empacotado com dependencias.

## 3. Ainda falta para fechar a V1

- [ ] `InputScanner`.
- [ ] `DestinationService`.
- [ ] `BatchModeRunner`.
- [ ] CLI `batch` com Picocli.
- [ ] Modo de homologacao/preservacao de entrada para testar com `NF MODELO ABRASP E PORTAL NACIONAL/`.
- [ ] Log/relatorio operacional por execucao.
- [ ] Reexecucao sem duplicar processamento.
- [ ] `WatchModeRunner`.
- [ ] README operacional final e teste de homologacao.

## 4. Proximo passo recomendado

Implementar a Etapa 7 do plano:

```text
batch completo -> scanner -> pipeline -> destino -> ledger/log -> CLI
```

O primeiro teste manual deve apontar para a pasta existente `NF MODELO ABRASP E PORTAL NACIONAL/` em modo de homologacao, preservando esses PDFs de entrada. Depois disso, apontar para uma pasta real fora do projeto.

## 5. Comandos de verificacao

Use o repositorio Maven local em `/tmp` neste ambiente:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse package
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar
```
