# CLAUDE.md

Este arquivo e curto de proposito.

## Leitura inicial

Em sessao nova deste projeto:

1. leia `AGENTS.md`
2. leia `SITUACAO_ATUAL.md`
3. leia trecho relevante de `ESPECIFICACAO_RENOMEADOR_NFSE.md`

## Regras operacionais

- Codigo Java segue a arquitetura de camadas definida em `AGENTS.md` — nao misturar camadas.
- Dois layouts homologados: **Portal Nacional (DANFSe v1.0)** e **ABRASF municipal**. Qualquer outro vai para `revisar/`.
- O original do PDF nunca pode ser perdido ou sobrescrito — preservar sempre em `originais/`.
- Se a tarefa tocar em parser, extracao ou deteccao de layout, use a skill `layout-nfse` para garantir que os campos corretos estao sendo lidos do lugar certo.
- Se tocar em performance, watcher ou pipeline, use `validacao-performance-java` antes de declarar melhoria.

## Regras de prudencia

- Nao presumir MCP ativo so porque esta instalado na maquina.
- Nao presumir plugin Java ativo sem ver em `.claude/settings.json` deste projeto.
- Nao guardar segredo nem CNPJ real de empresa neste arquivo.
- `jdtls-lsp` esta habilitado somente neste projeto — nao afeta o projeto INTERFACE.
- `codebase-memory-mcp` e global — este projeto ja foi indexado, reindexar apos mudancas relevantes.
