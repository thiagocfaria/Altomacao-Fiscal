# AGENTS.md

Verdades duraveis da raiz do projeto ALTOMACAO Fiscal.

## Ordem de leitura em sessao nova

1. leia este `AGENTS.md`
2. leia `SITUACAO_ATUAL.md`
3. se a tarefa tocar PDFs/NFS-e/renomeacao, leia `RENOMEADOR/AGENTS.md`
4. depois leia o trecho relevante de `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`

## Matriz de verdade da raiz

| Documento | O que guarda |
|---|---|
| `AGENTS.md` | regras duraveis da raiz e separacao entre modulos |
| `SITUACAO_ATUAL.md` | mapa atual dos modulos e proximos passos gerais |
| `README.md` | orientacao rapida do projeto |
| `RENOMEADOR/AGENTS.md` | regras duraveis do modulo de PDFs/NFS-e |
| `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md` | documentacao unica operacional e tecnica do modulo RENOMEADOR |
| `AUDITORIA_RISCOS_RENOMEADOR.md` | auditoria pessimista de falhas possiveis do RENOMEADOR |
| `docs/operacao/MCP_AVALIACAO.md` | decisoes sobre MCPs e LSPs adotados |
| `docs/operacao/AMBIENTE_SKILLS_MCP_LSP.md` | como reproduzir skills, MCPs e LSPs em outra maquina |

## Separacao por modulo

- `PLANILHA_FISCAL.xlsm` fica na raiz e e a base compartilhada de clientes/caminhos para modulos atuais e futuros.
- `RENOMEADOR/` contem tudo que pertence ao modulo de renomear e organizar PDFs de NFS-e.
- Novas automacoes devem nascer em novas pastas de modulo, sem misturar codigo dentro de `RENOMEADOR/`.
- Artefatos operacionais gerados pelo RENOMEADOR ficam em `RENOMEADOR/operacao/` ou em caminho externo informado na empresa; nao entram no Git.

## Regras de prudencia

- Nao mover a planilha para dentro de um modulo sem decisao explicita.
- Nao criar PDF operacional, backend, ledger ou logs na raiz do repositorio.
- Para alterar regras fiscais, parsers, layout ou pipeline de PDFs, siga primeiro as regras em `RENOMEADOR/AGENTS.md`.
- Antes de declarar producao pronta, rode a escada de validacao do modulo afetado.
