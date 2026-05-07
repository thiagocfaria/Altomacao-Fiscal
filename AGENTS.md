# AGENTS.md

Verdades duraveis do projeto que valem para Claude, Codex e qualquer agente.

## Ordem de leitura em sessao nova

1. leia `AGENTS.md`
2. leia `SITUACAO_ATUAL.md`
3. leia o trecho relevante de `ESPECIFICACAO_RENOMEADOR_NFSE.md`
4. so depois proponha ou execute qualquer acao

## Matriz de verdade

| Documento | O que guarda |
|---|---|
| `AGENTS.md` | regras duraveis de arquitetura, implementacao e validacao |
| `SITUACAO_ATUAL.md` | onde paramos, o que foi feito, qual e o proximo passo |
| `ESPECIFICACAO_RENOMEADOR_NFSE.md` | plano tecnico completo — fases, criterios de aceite, regras de negocio |
| `docs/operacao/MCP_AVALIACAO.md` | decisoes sobre MCPs e LSPs adotados |
| `docs/FERRAMENTAS_E_SKILLS.md` | catalogo de todas as ferramentas ativas e como uslas |

Regra simples:

```text
AGENTS.md nao vira diario.
SITUACAO_ATUAL.md nao vira copia da arquitetura.
ESPECIFICACAO_RENOMEADOR_NFSE.md nao vira log de sessao.
```

## Arquitetura atual do codigo Java

O codigo Java segue responsabilidades separadas por pacote. A V1 ainda nao tem todos os nomes finais de `domain/pipeline/io/app`, mas as fronteiras abaixo devem ser preservadas.

```
src/main/java/br/com/nfse/renomeador/
├── config/      -> empresas.yaml, selecao e resolucao de caminhos
├── extraction/  -> servico de extracao e separacao de PDFs
├── files/       -> hash, estabilidade e preservacao de originais
├── layout/      -> deteccao de layout
├── ledger/      -> anti-reprocessamento persistente
├── naming/      -> nome final operacional
├── parser/      -> parsers Portal Nacional e ABRASF/ISSNet
├── pdf/         -> PDFBox e extracao de texto
├── processing/  -> validacao de empresa e decisao de status
├── text/        -> normalizacao textual
└── App          -> ponto de entrada CLI; batch/watch/config orquestram o pipeline
```

Regras de fronteira (nao violar):

- `parser/` nao move arquivo e nao conhece ledger.
- `layout/` apenas classifica; nao extrai todos os campos fiscais.
- `files/` e `ledger/` nao interpretam NFS-e.
- `processing/` decide status com base em dados extraidos, sem fazer IO.
- `config/` carrega caminhos e empresas, sem regra fiscal.
- `batch/watch` deve orquestrar componentes existentes, nao duplicar parsing.

## Dois layouts homologados na V1

| Layout | Identificacao no texto | Pasta de amostras |
|---|---|---|
| **Portal Nacional (DANFSe v1.0)** | `"DANFSe v1.0"` + `"Numero da DPS"` | `NF MODELO ABRASP E PORTAL NACIONAL/` |
| **ABRASF municipal** | `"Nota Fiscal de Servico Eletronica"` + `"Cod. de Autenticidade"` | mesma pasta |
| **Nao suportado** | nenhum dos dois | renomear com prefixo `MODELO_NAO_SUPORTADO_` |

## Regras de implementacao

1. `PDDocument` sempre em try-with-resources — nunca fechar no finally manual.
2. `Pattern.compile` sempre como constante estatica — nunca dentro de metodo chamado por arquivo.
3. Records Java sem setters e sem estado mutavel para dados carregados/extrados.
4. Campos ausentes devem ser tratados de forma explicita e conservadora; qualquer incerteza tecnica vai para backend/revisar.
5. Mudanca pequena nao justifica espalhar alteracao por varias camadas sem necessidade.
6. Nao inventar segundo modo de execucao quando os comandos `watch` e `batch` ja cobrem.

## Pastas operacionais na REST do cliente

A pasta cadastrada em `CAMINHO REST` deve ficar limpa e operacional. O sistema so deve criar nela:

- `processados/` para notas validas sem retencao;
- `RETIDO/` para notas validas com imposto retido;
- `canceladas/` para notas canceladas;
- `TOMADOR NAO ENCONTRADO/` apenas quando a nota caiu em uma REST errada e o CNPJ do tomador nao tem caminho REST ativo no cadastro Excel.

Nao criar `logs/`, `ledger`, `originais/`, `split-work/` ou indices tecnicos dentro da REST do cliente. Esses dados ficam no `backend/` do sistema, ao lado do `empresas.yaml` usado na execucao.

Se uma nota ficar em `TOMADOR NAO ENCONTRADO/` e depois o CNPJ do tomador ganhar caminho REST ativo na planilha/cadastro, `batch` e a varredura inicial/reload do `watch` devem recuperar essa nota, mover para a REST correta com o nome operacional normal e apagar `TOMADOR NAO ENCONTRADO/` quando a pasta ficar vazia.
Se o mesmo PDF ja tiver sido processado no destino correto, a copia pendente em `TOMADOR NAO ENCONTRADO/` deve ser descartada para nao manter duplicidade operacional.

## Regras de validacao

- Comando canonico de teste unitario: `mvn test`
- Comando canonico de integracao: `mvn verify -Pintegration`
- KPI principal: **tempo de processamento por NF** (ms/nota, medido no pipeline)
- KPI secundario: **memoria do watcher em soak de 1h** (heap MB via jcmd)
- Benchmark offline nao substitui teste de integracao com PDF real.

Escada oficial de teste:

1. `mvn test` (unitarios puros, sem IO real)
2. `mvn verify -Pintegration` (com PDFs do lote piloto)
3. teste end-to-end em pasta sintetica da empresa piloto
4. soak do watcher (1h, pasta com novos PDFs chegando a cada 5min)
5. JMH no parser (quando mudar logica de extracao)

## Retencao de impostos — regra de negocio

Uma NFS-e tem imposto retido quando:
- `Valor Liquido da NFS-e` < `Valor do Servico` (Portal Nacional)
- `Vl. Liquido da NotaFiscal` < `Vl. Total dos Servicos` (ABRASF)
- OU qualquer campo de retencao explicito > 0 (ISSQN Retido, IRRF, INSS, PIS, COFINS, CSLL, Outras Retencoes)

Quando retido: pasta `RETIDO/` + sufixo `##IR_RETIDO##` no nome final.
Quando cancelada: sufixo `##CANCELADA##` + pasta `canceladas/`.

## Duplicidade Portal Nacional x ABRASF

Quando existirem duas representacoes da mesma NFS-e, uma em Portal Nacional e outra em ABRASF/ISSNet, o Portal Nacional tem preferencia operacional.

A ABRASF so pode ser descartada automaticamente quando todos estes campos fiscais baterem:
- numero da nota;
- CNPJ do prestador;
- nome do prestador;
- CNPJ do tomador;
- data de emissao;
- valor do servico;
- valor liquido.

Horario de emissao nao entra na chave de duplicidade da V1.
O PDF original recebido continua preservado no `backend/` tecnico do sistema; o descarte automatico remove somente copia operacional duplicada em pasta controlada.

Notas duplicadas no mesmo layout tambem nao devem gerar duas copias operacionais. O sistema so pode descartar/remover duplicata quando a chave fiscal completa bater e o arquivo a remover estiver em uma pasta operacional controlada pelo sistema.

## Ativacao por projeto

```text
ferramenta instalada no computador
nao entra automaticamente neste projeto
```

Qualquer nova dependencia Maven, MCP ou plugin de Claude so entra apos:
1. checklist de seguranca em `docs/operacao/MCP_AVALIACAO.md`
2. alinhamento com `AGENTS.md` e `ESPECIFICACAO_RENOMEADOR_NFSE.md`
3. registro da decisao no doc relevante
