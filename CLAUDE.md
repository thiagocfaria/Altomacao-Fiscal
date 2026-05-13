# CLAUDE.md

Este arquivo orienta a raiz do projeto.

## Leitura inicial

Em sessao nova:

1. **leia OBRIGATORIAMENTE `REGRAS_INVARIANTES.md`** — regras de comportamento
   que NAO podem ser removidas sem alinhamento explicito com o dono
2. leia `AGENTS.md`
3. leia `SITUACAO_ATUAL.md`
4. leia `docs/operacao/CODING_AGENTES.md`
5. se a tarefa tocar arquitetura, refatoracao, performance ou modulo grande, leia `docs/operacao/ARQUITETURA_PROFISSIONAL.md`
6. se a tarefa tocar PDFs/NFS-e/renomeacao, leia `RENOMEADOR/AGENTS.md`
7. depois leia `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`
8. se a tarefa tocar Portal Nacional/ADN/importacao/DMS, leia `IMPORT API PN/AGENTS.md`

## Separacao

- A planilha `PLANILHA_FISCAL.xlsm` e compartilhada e fica na raiz.
- O modulo de PDFs fica em `RENOMEADOR/`.
- Novos modulos devem ser criados em novas pastas, sem reaproveitar a pasta do renomeador como area geral.

## Como evitar erro de agente

- Nao assumir que MCP/LSP estao funcionando; conferir `docs/operacao/AMBIENTE_SKILLS_MCP_LSP.md`.
- Para Codex/Claude, usar `docs/operacao/CODING_AGENTES.md` como mapa curto antes de abrir muitos arquivos.
- O usuario nao precisa pedir ferramentas; use MCP/LSP/skills automaticamente quando o gatilho da tarefa pedir.
- Nao declarar pronto sem validacao real do modulo afetado.
