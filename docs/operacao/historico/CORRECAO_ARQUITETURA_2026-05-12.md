# CORRECAO_ARQUITETURA.md

Diagnostico vivo de arquitetura do projeto.

Atualizado em 12/05/2026 apos limpeza de documentos e artefatos historicos.

## Principios que ficam

- `PLANILHA_FISCAL.xlsm` continua sendo a fonte compartilhada de clientes/caminhos.
- `RENOMEADOR/` nao consulta Portal Nacional.
- `IMPORT API PN/` consulta Portal Nacional/ADN e publica artefatos para REST/DMS.
- `entrada-rest` e a fronteira entre importacao e organizacao REST.
- `REGRAS_INVARIANTES.md` bloqueia qualquer otimizacao que use ledger como verdade.
- Docs canonicos sao poucos: `AGENTS.md`, `SITUACAO_ATUAL.md`,
  `REGRAS_INVARIANTES.md`, este arquivo, `CLAUDE.md`, `RENOMEADOR/AGENTS.md`,
  `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`, `IMPORT API PN/AGENTS.md`,
  `IMPORT API PN/PLANO_GERADOR_DANFSE_LOCAL.md` e `docs/operacao/*.md`.

## O que foi limpo

- Removidos artefatos operacionais/cache: `target/`, `IMPORT API PN/backend/`,
  `RENOMEADOR/operacao/`, `__pycache__/`, locks e backups locais.
- Removidos atalhos/scripts antigos em `PAINEL/`; o painel vivo e `painel.py`.
- Removida pasta visual/historica `docs/referencias/planilha/`.
- Removidos planos/pesquisas historicos do `IMPORT API PN` que contradiziam o codigo atual.
- Mantido `IMPORT API PN/PLANO_GERADOR_DANFSE_LOCAL.md`, porque ainda e a proxima
  entrega critica por causa da suspensao da API DANFSe.

## Diagnostico atual

### Correto e deve ser preservado

| Item | Estado |
|---|---|
| Separacao Maven por modulos | correta |
| Leitura da planilha mensal | implementada |
| RENOMEADOR roteando XML/PDF REST | implementado |
| IMPORT API PN consultando ADN | implementado |
| Reconciliacao Portal x destino | implementada no comando `reconciliar` |
| DMS direto por emissao/CNPJ | implementado no fluxo de reconciliacao |
| Painel local | `painel.py` |
| Regra de recompor arquivo apagado | protegida por codigo/testes e por `REGRAS_INVARIANTES.md` |

### Riscos reais restantes

**C1. PDF local ainda nao existe**

A API DANFSe e instavel e tem prazo externo de suspensao informado para 01/07/2026.
Sem gerador local, o sistema continua dependente de um servico que pode falhar ou sumir.

**C2. Progresso por empresa/NSU ainda e bruto**

O reconciliador consulta a partir de um NSU inicial e anda ate `--max-lotes`.
Isso funciona para recompor, mas ainda nao e a arquitetura ideal de producao para muitas
empresas. Falta persistir progresso por empresa com retomada e politica de recuperacao.

**C3. Observabilidade do IMPORT API PN ainda e fraca**

O painel grafico mostra logs, mas o modulo ainda nao gera painel tecnico proprio
(`health.json`, alertas e resumo por empresa/nota) no mesmo padrao do RENOMEADOR.

**A1. `AppImportadorPn.java` esta grande demais**

Ele orquestra CLI, reconciliacao, consulta, publicacao e mensagens. Deve ser quebrado
em comandos/servicos menores depois que houver testes de comportamento suficientes.

**A2. `InvoiceProcessingPipeline.java` ainda concentra muita regra**

Continua funcional, mas mistura orquestracao, duplicidade fiscal, reparacao de nome e
decisoes de destino. Refatorar apenas depois de adicionar testes diretos do pipeline.

**A3. `ExcelCompanyImporter.java` e `ExcelWorkbookPreparer.java` sao grandes**

Fazem trabalho real e estao cobertos por testes, mas ainda misturam POI, parsing e regra
de planilha. Sao candidatos a extracao incremental, nao a reescrita grande.

## Correcoes aplicadas nesta rodada

- `.gitignore` passou a ignorar caches, backups, painel antigo, backend operacional e
  artefatos locais.
- `painel.py` deixou de depender de caminho fixo: usa o diretorio do proprio arquivo por
  padrao e aceita variaveis `ALTOMACAO_*`.
- `RENOMEADOR/pom.xml` passou a herdar o parent Maven da raiz, igual ao `IMPORT API PN`.
- `WatchFolderProcessor` teve duplicacao pequena de registro unificada.
- `SITUACAO_ATUAL.md` foi reescrito para refletir o fluxo atual.
- `IMPORT API PN/AGENTS.md` deixou de apontar agentes para planos historicos removidos.

## Sequencia senior recomendada

1. **PDF local:** implementar `GeradorDanfseLocal` com testes sobre XML real/sintetico.
2. **Progresso por empresa:** persistir estado de NSU/rodada por CNPJ e validar retomada.
3. **Health do IMPORT API PN:** gerar arquivos tecnicos de status para painel e auditoria.
4. **Refatorar `AppImportadorPn`:** extrair comandos e servicos depois dos testes.
5. **Refatorar RENOMEADOR:** separar duplicidade fiscal e reparacao de nomes do pipeline.
6. **Teste real assistido:** apagar arquivos finais, ligar painel e confirmar recomposicao.

## Regra para proximas limpezas

Nao apagar documento ou codigo so porque parece antigo. Antes:

1. confirmar que nao e referenciado por `AGENTS.md`, `README.md`, testes ou scripts;
2. mover o conhecimento ainda util para `SITUACAO_ATUAL.md` ou este arquivo;
3. rodar a validacao Maven;
4. conferir `git status --short` para garantir que so saiu o planejado.

## Validacao obrigatoria

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
python3 -m py_compile painel.py
```
