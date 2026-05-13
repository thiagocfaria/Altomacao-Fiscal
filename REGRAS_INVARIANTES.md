# REGRAS INVARIANTES DO PROJETO

Este arquivo lista regras de comportamento que NAO PODEM SER REMOVIDAS sem alinhamento
explicito com o dono do projeto. Antes de alterar qualquer codigo que afete uma destas
regras, leia este documento e confirme a mudanca.

Ultima atualizacao: 13/05/2026
Dono: Thiago Caetano Faria

---

## REGRA #1 — Fonte de verdade: Portal Nacional + pasta destino do cliente

### O que e

A decisao de "preciso importar/processar essa nota?" **NUNCA** pode ser tomada olhando
apenas para indices internos do sistema (`processados.idx`, `duplicadas.idx`, ledger do
IMPORT API PN). A fonte de verdade e composta por dois lugares:

1. **Portal Nacional (ADN)** — define quais notas existem
2. **Pasta destino do cliente, configurada na PLANILHA_FISCAL.xlsm** — define o que ja
   foi entregue ao cliente

### Comportamento esperado

Para cada nota disponivel no Portal, o sistema verifica o que existe no destino do cliente:

- XML existe em `CAMINHO REST/XML/processados/`? Se NAO → re-importa do Portal
- PDF existe em `CAMINHO REST/PDF/processados/`? Se NAO → re-baixa via DANFSe
- XML existe em `CAMINHO DMS/`? Se NAO → re-publica no DMS

Se um funcionario apagar manualmente qualquer arquivo (PDF ou XML) da pasta do cliente,
**o sistema deve detectar essa ausencia e re-importar do Portal automaticamente** na
proxima rodada.

### Por que essa regra existe

O dono precisa poder limpar/refazer pastas a qualquer momento (recuperacao de backup,
correcao manual, organizacao) sem que isso quebre a re-importacao. Indices internos sao
**otimizacao**, nunca verdade.

### Onde a regra esta implementada (referencias de codigo)

| Componente | Arquivo | Mecanismo |
|---|---|---|
| RENOMEADOR | `RENOMEADOR/src/main/java/br/com/nfse/renomeador/pipeline/InvoiceProcessingPipeline.java` | Metodo `destinoExisteNoDisco` em `findDuplicate`. Ignora entrada do indice de duplicatas se o arquivo destino sumiu. |
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/AppImportadorPn.java` | `executar-janelas` chama `capturarEmpresas(..., recomporPdf=true)`. Isso faz `deveRegistrarDanfse` em `RegistroConsultaAdn` re-baixar o PDF sempre que ele estiver ausente em entrada-rest (que por sua vez recria o ciclo de roteamento ate o destino do cliente). |
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/portal/ReconciliadorPortalDestino.java` | `GET /DFe/{NSU}` com `HTTP 404` encerra a varredura daquele CNPJ/NSU como ausencia natural de lote, sem virar `ERRO_EXTERNO`. |
| RENOMEADOR | `RENOMEADOR/src/main/java/br/com/nfse/renomeador/ledger/ProcessingLedger.java` | Status `ERRO_PROCESSAMENTO_*` nao bloqueia nova tentativa (transient error). |

### O que e PROIBIDO

- Pular o processamento de uma nota baseado APENAS no estado do ledger
  (`estadoXml=CONCLUIDO`, `estadoPdf=CONCLUIDO`, `concluida()`) sem verificar tambem
  se o arquivo destino existe no disco
- Marcar arquivo como "duplicata, descartar" baseado em `duplicadas.idx` sem confirmar
  que o destino apontado pelo indice ainda existe
- Adicionar caches/otimizacoes que ignorem o estado real do disco

### Testes que protegem essa regra

- `RENOMEADOR/src/test/java/br/com/nfse/renomeador/pipeline/InvoiceProcessingPipelineTest.java`
  (cobertura indireta via cenarios de duplicata)
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/portal/ReconciliadorPortalDestinoTest.java`
  (404 de DFe como parada natural)
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/SimuladorReconciliacaoDryRunTest.java`
  (dry-run do `VERIFICAR TUDO` com a mesma semantica)
- Teste end-to-end manual: apagar arquivo do destino, rodar painel, confirmar que volta

### Historico desta regra

- 10/05/2026: regra foi violada inicialmente — `duplicadas.idx` era consultado sem
  verificar destino, causando arquivos "fantasma" no indice depois que pastas eram
  limpas manualmente. Fix aplicado em `InvoiceProcessingPipeline.findDuplicate`.
- 11/05/2026 (manha): dono reforca a regra. Aplicado fix complementar:
  `executar-janelas` agora chama com `recomporPdf=true` por padrao.
- 11/05/2026 (tarde): dono identifica que o sistema AINDA olhava ledger interno
  para decisao de pular notas, e exige reformulacao completa: **a decisao olha
  APENAS as pastas do destino do cliente (planilha), nunca indices internos**.
  Refatoramento implementado:
  - Nova classe `ChavesPresentesNoDestino` escaneia o destino real (REST + DMS)
    e extrai chaves de acesso dos XMLs presentes
  - A chave so e considerada "presente" se aparecer EM TODOS os destinos
    esperados (intersecao REST ∩ DMS) - se faltar em qualquer um, nao entra no set
  - Novo comando `reconciliar` que aplica esta regra: chama Portal, escaneia destino,
    re-importa apenas as chaves faltantes
  - Painel atualizado para chamar `reconciliar` no loop de 60s (em vez de
    `executar-janelas`), permitindo que arquivos apagados sejam detectados e
    repostos em ate 60 segundos
  - Validado end-to-end: apagar XML do destino do cliente faz o sistema detectar
    e re-importar em menos de 1 ciclo de loop

### Implementacao tecnica (modo reconciliar)

```
Painel.loop_captura (a cada 60s, no mes de atuacao selecionado no painel)
  ↓
java -jar IMPORT_API_PN.jar reconciliar --planilha ... --backend ... --mes AAAA-MM
  ↓
Para cada empresa habilitada:
  1. ChavesPresentesNoDestino.escanear(empresa, mes)
     - Le caminhoRest/XML/{processados,canceladas}/ → extrai chaves dos XMLs
     - Le caminhoDms/ → extrai chaves dos XMLs
     - Retorna INTERSECAO (chave presente em todos os destinos)
  2. Chama Portal Nacional (GET /DFe/{nsu})
  3. RegistroConsultaAdn.registrar(..., chavesPresentesNoDestino)
     - Para cada documento do Portal:
       - Se chave ∈ chavesPresentesNoDestino → SKIP
       - Caso contrario → publica XML em entrada-rest + DANFSe + DMS direto
  4. Watch do RENOMEADOR ja em execucao move de entrada-rest → REST/processados/
```

**Garantia tecnica**: o ledger interno (`processados.idx`, `duplicadas.idx`,
`backend/ledger/2026-05.tsv`) NAO E CONSULTADO para decidir importacao. So existe
para fins de auditoria/historico. Pode ser apagado a qualquer momento sem afetar
correcao - o sistema reconciliara com o Portal na proxima rodada.

---

## REGRA #2 - Linha ativa da planilha e dona da importacao Portal Nacional

### O que e

No `IMPORT API PN`, a dona da consulta/importacao e sempre a linha ativa da aba mensal
da `PLANILHA_FISCAL.xlsm`. A linha ativa e definida por:

1. aba do mes de atuacao (`CADASTRO JANEIRO`, `CADASTRO FEVEREIRO`, etc.);
2. `IMPORT API PN ATIVO = SIM`;
3. CNPJ da propria linha;
4. `CAMINHO REST`, `CAMINHO DMS` e certificado preenchidos na propria linha, quando
   aplicaveis.

Outras empresas do mesmo grupo economico, filiais em linhas vizinhas, linhas sem caminho
ou linhas sem `IMPORT API PN ATIVO = SIM` nao viram destino por heranca e nao podem
bloquear a importacao da linha ativa.

### Comportamento esperado

Para cada empresa ativa no mes:

- o Portal Nacional/ADN e consultado com o CNPJ da propria linha;
- o certificado usado deve pertencer ao CNPJ da propria linha;
- XML/PDF REST sao publicados usando o `CAMINHO REST` da propria linha;
- XML DMS e publicado usando o `CAMINHO DMS` da propria linha;
- se o XML retornado pelo Portal nao contem o CNPJ da linha ativa em nenhum papel fiscal
  reconhecido (`prestador`, `tomador` ou `intermediario`), o documento e ignorado como
  `fora da empresa consultada`;
- `fora da empresa consultada` nao e erro de cadastro e nao deve exigir caminho para outro
  CNPJ;
- arquivos REST gerados pelo `IMPORT API PN` devem preservar o dono da consulta no nome
  `PN_<cnpjConsulta>_NSU_...`, para o RENOMEADOR rotear pelo CNPJ consultado antes de
  aplicar a regra normal por tomador.

O roteamento normal do RENOMEADOR para entradas manuais ou pastas `SOMENTE ORIGEM` sem
prefixo `PN_<cnpjConsulta>` continua podendo usar o CNPJ do tomador para achar a REST
correta. Essa excecao nao se aplica aos arquivos publicados pelo `IMPORT API PN`.

### Por que essa regra existe

O usuario escolhe, na planilha, quais linhas o sistema deve consultar e para onde cada
linha deve publicar. Um certificado ou uma resposta do Portal pode expor documentos com
outros CNPJs relacionados, mas isso nao autoriza o sistema a criar trabalho para filiais
sem caminho, empresas inativas ou linhas que o usuario nao marcou para importacao.

### Onde a regra esta implementada (referencias de codigo)

| Componente | Arquivo | Mecanismo |
|---|---|---|
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/configuracao/LeitorPlanilhaFiscal.java` | `lerTodasAbas` e leitura mensal ignoram linhas sem `IMPORT API PN ATIVO = SIM`; linha inativa nao cria rota tecnica. |
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/portal/PlanejadorDocumentoDfe.java` | Antes de planejar REST/DMS, verifica se o CNPJ da linha ativa aparece no XML; senao retorna `NAO_PERTENCE_EMPRESA`. |
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/portal/RoteadorDmsPorEmissao.java` | DMS resolve somente para `empresa.caminhoDms()` da linha ativa, no mes de comando e com XML pertencente a empresa. |
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/SimuladorReconciliacaoDryRun.java` | `VERIFICAR TUDO` usa o mesmo planejador e conta documentos fora da empresa consultada sem pedir rota de outro CNPJ. |
| IMPORT API PN | `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/portal/RegistroConsultaAdn.java` | Modo real respeita o mesmo plano; documentos fora da empresa nao sao publicados. |
| RENOMEADOR | `RENOMEADOR/src/main/java/br/com/nfse/renomeador/pipeline/InvoiceProcessingPipeline.java` | Arquivos `PN_<cnpjConsulta>_NSU_...` sao roteados pelo CNPJ da consulta antes da regra normal por tomador. |

### O que e PROIBIDO

- Resolver `CAMINHO DMS` pelo CNPJ do tomador extraido do XML para arquivo vindo do
  `IMPORT API PN`.
- Herdar `CAMINHO REST` ou `CAMINHO DMS` por grupo economico sem decisao explicita.
- Exigir caminho para linha sem `IMPORT API PN ATIVO = SIM`.
- Transformar CNPJ de outra linha, filial ou empresa do grupo em bloqueio do
  `VERIFICAR TUDO` quando a linha ativa esta correta.
- Remover o prefixo `PN_<cnpjConsulta>` dos arquivos publicados pelo `IMPORT API PN`.

### Testes que protegem essa regra

- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/configuracao/LeitorPlanilhaFiscalTest.java`
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/portal/RoteadorDmsPorEmissaoTest.java`
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/portal/PlanoDocumentoDfeTest.java`
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/SimuladorReconciliacaoDryRunTest.java`
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/portal/RegistroConsultaAdnTest.java`
- `RENOMEADOR/src/test/java/br/com/nfse/renomeador/app/BatchModeRunnerTest.java`

### Historico desta regra

- 13/05/2026: corrigida a interpretacao errada que fazia DMS/dry-run dependerem do CNPJ
  do tomador e das rotas de outras linhas. A partir desta data, `VERIFICAR TUDO` e modo
  real usam a linha ativa como dona da importacao.

---

## Como adicionar uma nova regra invariante neste documento

1. Numerar (REGRA #N)
2. Escrever em portugues claro
3. Descrever **o que e**, **comportamento esperado**, **por que existe**,
   **onde esta implementada** (referencias de codigo), **o que e proibido**,
   **testes que protegem**, e **historico**
4. Atualizar a data no topo do documento
5. Mencionar a mudanca no `SITUACAO_ATUAL.md`
