# Coding com agentes

Mapa curto para Codex/Claude trabalharem neste repositorio sem depender de memoria.

## Leitura minima

1. `REGRAS_INVARIANTES.md`
2. `AGENTS.md`
3. `SITUACAO_ATUAL.md`
4. este arquivo
5. `docs/operacao/ARQUITETURA_PROFISSIONAL.md`, se tocar arquitetura, refatoracao, performance ou modulo grande
6. `RENOMEADOR/AGENTS.md`, se tocar RENOMEADOR/PDF/XML/NFS-e
7. `IMPORT API PN/AGENTS.md`, se tocar Portal Nacional/ADN/importacao/DMS
8. documento tecnico do modulo afetado

## Mapa dos modulos

| Caminho | Papel | Nao fazer |
|---|---|---|
| `PLANILHA_FISCAL.xlsm` | cadastro compartilhado de clientes/caminhos/certificados | mover para modulo |
| `RENOMEADOR/` | organiza PDFs/XMLs NFS-e na REST do cliente | misturar importador ADN aqui |
| `IMPORT API PN/` | consulta Portal Nacional/ADN, publica XML/PDF/DMS | usar ledger como fonte de verdade |
| `docs/operacao/` | decisoes de ambiente e ferramentas | guardar segredo/log operacional |

## Regras anti-alucinacao

- Se o assunto for estado atual, primeiro leia `SITUACAO_ATUAL.md`.
- Se o assunto for regra fiscal ou operacional, primeiro ache a regra no documento do modulo.
- Se nao achar a regra, diga que nao achou e investigue no codigo/testes; nao invente.
- Antes de mexer em roteamento, ledger ou duplicidade, releia `REGRAS_INVARIANTES.md`.
- O usuario nao precisa pedir ferramentas. Acione MCP, LSP, skills, `rg` e testes
  automaticamente conforme o gatilho da tarefa.
- Use `codebase-memory-mcp` automaticamente quando `index_status` mostrar `ready` com
  grafo preenchido e a tarefa envolver codigo Java, entendimento amplo do sistema,
  impacto, chamadas, dependencias, arquitetura, refatoracao, limpeza, performance,
  qualidade, bugs, testes, regra implementada no codigo ou quando o usuario disser
  "use o MCP".
- Nao use MCP por reflexo em pergunta simples ou edicao textual conhecida; economizar
  tokens tambem faz parte da precisao. Confirme comportamento com `rg`, leitura dirigida
  e testes Maven.
- Trate `.claude/settings.local.json`, caches, certificados, XML/PDF reais e backends como ambiente local, nao baseline.

## Gatilhos de skills no Codex

Estas skills sao de ambiente, nao do repositorio. O agente deve usar automaticamente por
gatilho; o usuario nao precisa pedir:

| Situacao | Skill esperada |
|---|---|
| bug, falha de teste, comportamento estranho | `superpowers:systematic-debugging` |
| feature, bugfix, refatoracao ou mudanca de comportamento | `superpowers:test-driven-development` |
| plano grande ou varias etapas | `superpowers:writing-plans` |
| execucao de plano ja escrito | `superpowers:executing-plans` |
| antes de dizer que esta pronto/passando | `superpowers:verification-before-completion` |
| criar ou alterar skill | `skill-creator` e `superpowers:writing-skills` |

Nao criar skill local de NFS-e enquanto o fluxo XML+PDF ainda estiver mudando. Se uma regra precisa valer neste projeto, coloque em `AGENTS.md`, no `AGENTS.md` do modulo ou na documentacao canonica.

## Comandos de validacao

Use a partir da raiz:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" test
```

Antes de afirmar que MCP/LSP ajudam:

```bash
jdtls --version
/home/u/.local/bin/codebase-memory-mcp cli list_projects
```

Status verificado em 12/05/2026 neste chat `codex-tulio`:

- `jdtls` usa JDK 21 local pelo wrapper `/home/u/.local/bin/jdtls`; o `java` global
  continua Java 17 para Maven/comandos do projeto.
- o MCP da sessao respondeu `index_status` como `ready`, com grafo preenchido para
  este projeto. Se o CLI direto mostrar estado vazio, trate o MCP da sessao como fonte
  da conversa e confirme com `index_status`/`search_graph` antes de confiar.

## Padrao de trabalho

1. Identifique o modulo.
2. Leia o `AGENTS.md` do modulo.
3. Localize codigo e testes com `rg`.
4. Escreva ou atualize teste antes da mudanca quando houver comportamento.
5. Faca edicoes pequenas e alinhadas aos pacotes existentes.
6. Rode a validacao mais estreita primeiro; depois a escada do modulo quando o risco pedir.
7. Atualize documentacao canonica se a regra operacional mudou.
