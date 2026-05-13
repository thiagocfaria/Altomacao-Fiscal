# SITUACAO ATUAL

Atualizado em 13/05/2026.

## Regra prioritaria

A fonte de verdade para decidir importacao e:

1. Portal Nacional/ADN, que informa quais notas existem.
2. Pastas destino do cliente na `PLANILHA_FISCAL.xlsm`, que informam o que ja foi entregue.
3. A linha ativa da aba mensal da planilha, que define quem e dono da consulta e quais
   caminhos REST/DMS podem ser usados pelo `IMPORT API PN`.

Ledger, indice de duplicatas e logs sao auditoria/otimizacao. Eles nao podem impedir a
recomposicao de arquivo apagado no destino. Leia `REGRAS_INVARIANTES.md` antes de mexer
em roteamento, ledger, duplicidade, reconciliacao, DMS ou entrada REST.

Regra critica em vigor desde 13/05/2026: o `IMPORT API PN` nao herda caminho por grupo
economico e nao resolve DMS pelo CNPJ do tomador. Para arquivos vindos do Portal, a linha
ativa da planilha e a dona da importacao.

## Estrutura viva

| Caminho | Papel |
|---|---|
| `PLANILHA_FISCAL.xlsm` | cadastro compartilhado de clientes, caminhos e certificados |
| `RENOMEADOR/` | organiza XML/PDF de NFS-e dentro do `CAMINHO REST` correto |
| `IMPORT API PN/` | consulta Portal Nacional/ADN, baixa XML/PDF e publica REST/DMS |
| `painel.py` | operacao local: verificar, ligar watch, reconciliar e desligar |
| `docs/operacao/` | mapas logicos, decisoes de MCP, LSP, skills e trabalho com agentes |

Arquivos operacionais gerados (`target/`, `IMPORT API PN/backend/`, `RENOMEADOR/operacao/`,
backups, caches, XML/PDF reais e atalhos antigos) nao fazem parte do baseline do projeto.

## Fluxo operacional atual

1. O painel executa `VERIFICAR TUDO`.
2. O painel importa a planilha para `RENOMEADOR/operacao/empresas.yaml` com todos os meses.
3. O painel liga o RENOMEADOR em `watch`.
4. O painel define o mes de atuacao:
   - automatico ligado: usa o mes vigente do computador;
   - automatico desligado: usa o mes escolhido manualmente no painel.
5. A cada ciclo, o painel chama `IMPORT API PN reconciliar --mes AAAA-MM`.
6. O reconciliador consulta Portal Nacional x destino real do cliente naquele mes.
7. O que falta e republicado:
   - XML/PDF REST entram na entrada REST tecnica e o RENOMEADOR roteia para `CAMINHO REST`;
   - XML DMS e publicado diretamente pelo `IMPORT API PN` em `CAMINHO DMS`.

Comportamento esperado: se um XML/PDF/DMS for apagado da pasta destino, a proxima
reconciliacao deve detectar a falta e importar novamente, sem usar ledger como autoridade.

## Estado do RENOMEADOR

Modulo Maven independente no diretorio `RENOMEADOR/`.

Responsabilidades atuais:

- ler `empresas.yaml` gerado da planilha;
- monitorar entrada REST tecnica;
- separar XML e PDF;
- identificar cliente por CNPJ/data de emissao;
- preservar o dono da consulta em arquivos `PN_<cnpjConsulta>_NSU_...` gerados pelo
  `IMPORT API PN`, roteando esses arquivos pelo CNPJ da consulta antes da regra normal
  por tomador;
- criar estrutura `XML/` e `PDF/` dentro do `CAMINHO REST`;
- classificar `processados`, `RETIDO`, `canceladas` e `TOMADOR NAO ENCONTRADO`;
- manter logs, ledger, indice e healthcheck fora da REST do cliente;
- recuperar pendencias quando o cadastro passa a ter caminho correto;
- evitar que duplicata stale bloqueie reprocessamento quando o destino sumiu.

Documentacao canonica do modulo:

- `RENOMEADOR/AGENTS.md`
- `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`

## Estado do IMPORT API PN

Modulo Maven no diretorio `IMPORT API PN/`.

Responsabilidades atuais:

- ler empresas ativas da aba mensal da `PLANILHA_FISCAL.xlsm`;
- validar cadastro e certificados;
- operar cliente ADN em modo somente leitura;
- consultar lotes por NSU;
- extrair XML de `LoteDFe`;
- baixar DANFSe por chave enquanto a API oficial existir;
- publicar XML/PDF REST na entrada tecnica para o RENOMEADOR;
- publicar XML DMS direto no `CAMINHO DMS` da linha ativa, no mes de comando;
- escanear destino real (`CAMINHO REST` e `CAMINHO DMS`) antes de decidir o que falta;
- ignorar como `fora da empresa consultada` documentos cujo XML retornado pelo Portal nao
  contem o CNPJ da linha ativa em papel fiscal reconhecido;
- reconciliar Portal x destino com limite padrao de 500 lotes por rodada;
- rodar `verificar-tudo` em modo somente leitura, usando o mesmo motor do
  `reconciliar` em dry-run para contar o que seria importado sem publicar XML/PDF,
  sem baixar DANFSe e sem alterar ledger.

Estado da correcao aplicada em 13/05/2026:

- `VERIFICAR TUDO` e modo real usam a mesma regra: linha ativa da planilha e dona da
  importacao.
- `GET /DFe/{NSU}` retornando `HTTP 404` e tratado como parada natural da varredura
  daquele CNPJ/NSU, nao como `ERRO_EXTERNO`.
- `LeitorPlanilhaFiscal` nao cria rotas tecnicas a partir de linhas inativas.
- `PlanejadorDocumentoDfe` bloqueia a publicacao de XML que nao pertence ao CNPJ da
  linha ativa e classifica como `NAO_PERTENCE_EMPRESA`.
- O dry-run do `VERIFICAR TUDO` agora mostra `Documentos fora da empresa consultada`.
- `RoteadorDmsPorEmissao` nao procura mais DMS pelo tomador; ele usa somente
  `empresa.caminhoDms()` da linha ativa.
- O RENOMEADOR preserva o dono `PN_<cnpjConsulta>` para XML/PDF publicados pelo
  `IMPORT API PN`, evitando que o tomador mande o arquivo para outra linha.

Rodada real anterior, em 12/05/2026, antes desta correcao, mostrou falsos bloqueios de
DMS por tomadores/filiais de outras linhas. Esses bloqueios nao devem mais ser tratados
como regra valida do sistema.

Bloqueios atuais para fazer o `VERIFICAR TUDO` passar:

1. Nenhum bloqueio conhecido por `HTTP 404` do ADN. Essa resposta agora encerra a
   varredura do CNPJ/NSU como ausencia natural de lote/documento.

Limites atuais:

- PDF ainda depende da API DANFSe como caminho principal, que e instavel e tem prazo externo
  de suspensao informado para 01/07/2026.
- O gerador local de DANFSe a partir do XML ainda nao foi implementado.
- Ainda falta painel operacional tecnico do `IMPORT API PN` com arquivos de health/alerta.
- O controle persistente de progresso por empresa/NSU ainda precisa ser separado do loop
  bruto de reconciliacao.

Documentacao canonica do modulo:

- `IMPORT API PN/AGENTS.md`
- `IMPORT API PN/PLANO_GERADOR_DANFSE_LOCAL.md`, apenas para a proxima feature de PDF local.

## Mapa logico

O fluxo humano do sistema esta desenhado em `docs/operacao/MAPA_LOGICO_SISTEMA.md`.
Use esse mapa para revisar a linha do tempo antes de mexer em painel, reconciliacao,
DMS, entrada REST ou RENOMEADOR.

## Estado do painel

`painel.py` e o painel atual.

Controles operacionais:

- `Mes de atuacao`: campo `AAAA-MM` que controla o `--mes` do importador.
- `Automatico: mes vigente`: ligado por padrao; ao ligar o sistema, atualiza o campo
  para o mes atual. Em 12/05/2026, por exemplo, usa `2026-05`.
- `LIGAR SISTEMA` / `DESLIGAR SISTEMA`: mesmo botao alterna o estado do painel.
- `VERIFICAR TUDO`: confere JARs, chama `IMPORT API PN verificar-tudo` com o mesmo
  `--mes`, `--ambiente`, `--nsu` e `--max-lotes` usados ao ligar, consulta o Portal
  em modo somente leitura e simula a reconciliacao contra o destino real. Nao publica
  XML/PDF, nao baixa DANFSe, nao move arquivo e nao altera ledger. Depois atualiza o
  YAML do RENOMEADOR com todos os meses e roda `RENOMEADOR config preflight --mes`.
- O painel so registra verificacao valida para ligar quando o resultado final e `OK`
  ou `ATENCAO` para os mesmos parametros. Se mes/ambiente/NSU/max-lotes mudarem, exige
  nova verificacao. `TUDO OK` so aparece quando todos os comandos retornam `OK`; se
  `max-lotes` truncar a simulacao, o resultado e `ATENCAO`.
- Apos cada `reconciliar`, o painel resume o resultado real. Ele nao promete janela fixa
  de 05h/12h/17h porque o modo atual e continuo. Quando a rodada reimporta `0`
  documentos, o health do RENOMEADOR esta atual e nao tem `total`, `revisar` nem
  `erros`, a primeira rodada vazia apenas confirma a estabilidade; a segunda rodada
  vazia declara tudo conferido e informa a proxima conferencia pelo intervalo real do
  painel. Se o health estiver velho, a rodada nao pode ser tratada como tudo conferido.

Observacao atual: com a planilha e os certificados presentes nesta maquina, o bloqueio
conhecido anterior do `HTTP 404` do ADN foi tratado como parada natural da varredura.

Comando:

```bash
python3 painel.py
```

Variaveis opcionais:

```bash
ALTOMACAO_ROOT="/caminho/do/projeto"
ALTOMACAO_AMBIENTE=PRODUCAO
ALTOMACAO_INTERVALO_SEGUNDOS=60
ALTOMACAO_NSU_INICIAL=1
ALTOMACAO_MAX_LOTES_RECONCILIACAO=500
```

Atalhos antigos em `PAINEL/` foram removidos da base porque duplicavam comportamento e
podiam chamar fluxo antigo.

## Proximas correcoes

Ordem recomendada:

1. Implementar gerador local de DANFSe a partir do XML.
2. Criar controle persistente de progresso por empresa/NSU e politica de retomada.
3. Criar painel/health tecnico do `IMPORT API PN`.
4. Refatorar classes grandes com teste dedicado antes: `InvoiceProcessingPipeline`,
   `ExcelCompanyImporter`, `ExcelWorkbookPreparer` e `AppImportadorPn`.
5. Fazer teste assistido pelo painel: apagar arquivos finais, ligar o painel e confirmar
   recomposicao por destino real.

## Documentacao consolidada ou obsoleta

Documentos que nao devem mais ser tratados como fonte viva:

- `AUDITORIA_RISCOS_RENOMEADOR.md`: a auditoria antiga virou historico; as regras vivas
  do RENOMEADOR estao em `RENOMEADOR/AGENTS.md` e
  `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`.
- `IMPORT API PN/PLANO_IMPORTACAO_NFSE_PORTAL_NACIONAL.md`,
  `IMPORT API PN/REVISAO_CRITICA_PLANO_IMPORTACAO.md`,
  `IMPORT API PN/PESQUISA_PROBLEMAS_API_PORTAL_NACIONAL.md` e
  `IMPORT API PN/PLANO_TESTES_HOMOLOGACAO_PRODUCAO.md`: serviram para pesquisa e plano
  inicial, mas foram substituidos por `IMPORT API PN/AGENTS.md`, por este estado atual,
  por `REGRAS_INVARIANTES.md` e pelo codigo/testes do importador.
- `PLANO_MELHORIA_VERIFICAR_TUDO.md`: manter apenas como historico do plano e das fases.
  Para estado operacional atual do botao, usar este `SITUACAO_ATUAL.md`.
- `docs/referencias/planilha/*`: referencias visuais antigas da planilha nao fazem parte
  do baseline operacional. A planilha viva e `PLANILHA_FISCAL.xlsm`.

## Validacao esperada

Use a partir da raiz:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
python3 -m py_compile painel.py test_painel.py
python3 -m pytest test_painel.py -q
```
