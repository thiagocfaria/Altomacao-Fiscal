# AGENTS.md

Verdades duraveis da raiz do projeto ALTOMACAO Fiscal.

## Ordem de leitura em sessao nova

1. leia `REGRAS_INVARIANTES.md`
2. leia este `AGENTS.md`
3. leia `SITUACAO_ATUAL.md`
4. leia `docs/operacao/CODING_AGENTES.md`
5. se a tarefa tocar arquitetura, refatoracao, performance ou modulo grande, leia `docs/operacao/ARQUITETURA_PROFISSIONAL.md`
6. se a tarefa tocar PDFs/NFS-e/renomeacao, leia `RENOMEADOR/AGENTS.md`
7. depois leia o trecho relevante de `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`
8. se a tarefa tocar Portal Nacional/ADN/importacao/DMS, leia `IMPORT API PN/AGENTS.md`

## Matriz de verdade da raiz

| Documento | O que guarda |
|---|---|
| `AGENTS.md` | regras duraveis da raiz e separacao entre modulos |
| `SITUACAO_ATUAL.md` | mapa atual dos modulos e proximos passos gerais |
| `README.md` | orientacao rapida do projeto |
| `REGRAS_INVARIANTES.md` | regras que nao podem ser quebradas por refatoracao ou otimizacao |
| `docs/operacao/ARQUITETURA_PROFISSIONAL.md` | principios atuais de arquitetura, performance e trabalho com agentes |
| `RENOMEADOR/AGENTS.md` | regras duraveis do modulo de PDFs/NFS-e |
| `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md` | documentacao unica operacional e tecnica do modulo RENOMEADOR |
| `IMPORT API PN/AGENTS.md` | regras duraveis do modulo Portal Nacional/ADN |
| `docs/operacao/MAPA_LOGICO_SISTEMA.md` | mapa visual em linha do tempo do funcionamento do sistema |
| `docs/operacao/CODING_AGENTES.md` | mapa curto para Codex/Claude trabalharem sem alucinar |
| `docs/operacao/MCP_AVALIACAO.md` | decisoes sobre MCPs e LSPs adotados |
| `docs/operacao/AMBIENTE_SKILLS_MCP_LSP.md` | como reproduzir skills, MCPs e LSPs em outra maquina |
| `docs/operacao/historico/CORRECAO_ARQUITETURA_2026-05-12.md` | historico da correcao de arquitetura ja aplicada |

## Separacao por modulo

- `PLANILHA_FISCAL.xlsm` fica na raiz e e a base compartilhada de clientes/caminhos para modulos atuais e futuros.
- `RENOMEADOR/` contem tudo que pertence ao modulo de renomear e organizar PDFs de NFS-e.
- Novas automacoes devem nascer em novas pastas de modulo, sem misturar codigo dentro de `RENOMEADOR/`.
- Artefatos operacionais gerados pelo RENOMEADOR ficam em `RENOMEADOR/operacao/` ou em caminho externo informado na empresa; nao entram no Git.

## Regras de prudencia

- Nao mover a planilha para dentro de um modulo sem decisao explicita.
- Nao criar PDF operacional, backend, ledger ou logs na raiz do repositorio.
- Para alterar regras fiscais, parsers, layout ou pipeline de PDFs, siga primeiro as regras em `RENOMEADOR/AGENTS.md`.
- Para alterar Portal Nacional/ADN, certificados, ledger de importacao, DMS ou entrada REST, siga primeiro `IMPORT API PN/AGENTS.md`.
- Se a tarefa tocar roteamento, ledger, indice de duplicatas ou recomposicao de arquivo apagado, trate `REGRAS_INVARIANTES.md` como bloqueio obrigatorio.
- Antes de declarar producao pronta, rode a escada de validacao do modulo afetado.

## Contrato para agentes neste projeto

- Primeiro use os documentos acima como fonte de verdade; nao complete lacunas com memoria ou suposicao.
- O usuario nao precisa pedir MCP, LSP, skill ou ferramenta; o agente deve acionar automaticamente o que for aplicavel.
- Use `codebase-memory-mcp` automaticamente quando `index_status` estiver `ready` e a tarefa envolver codigo Java, entendimento amplo do sistema, impacto, chamadas, dependencias, arquitetura, refatoracao, limpeza, performance, qualidade, bugs, testes, regra implementada no codigo ou quando o usuario disser "use o MCP"; confirme com `rg`, leitura dirigida e testes Maven.
- Se MCP/LSP nao estiverem operacionais, use `rg`, leitura dirigida de arquivos e testes Maven como base de verdade.
- Skills globais do agente devem ser usadas automaticamente por gatilho: debugging para bug/falha, TDD para feature/bugfix, verification-before-completion antes de afirmar pronto, writing-plans para plano grande.
- Nao crie skill local nova para NFS-e enquanto o fluxo XML+PDF estiver instavel; registre regra de projeto nos `AGENTS.md` e documentos do modulo.
- Toda mudanca de comportamento precisa de teste ou justificativa explicita de por que nao ha teste automatizado viavel.
