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
└── App          -> ponto de entrada; CLI batch/watch ainda pendente
```

Regras de fronteira (nao violar):

- `parser/` nao move arquivo e nao conhece ledger.
- `layout/` apenas classifica; nao extrai todos os campos fiscais.
- `files/` e `ledger/` nao interpretam NFS-e.
- `processing/` decide status com base em dados extraidos, sem fazer IO.
- `config/` carrega caminhos e empresas, sem regra fiscal.
- O futuro `batch/watch` deve orquestrar componentes existentes, nao duplicar parsing.

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
4. Campos ausentes devem ser tratados de forma explicita e conservadora; qualquer incerteza vai para `revisar/`.
5. Mudanca pequena nao justifica espalhar alteracao por varias camadas sem necessidade.
6. Nao inventar segundo modo de execucao quando `--mode=watch` e `--mode=batch` ja cobrem.

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

Quando retido: sufixo `##RETIDO##` no nome final.
Quando cancelada: sufixo `##CANCELADA##` + pasta `revisar/canceladas/`.

## Ativacao por projeto

```text
ferramenta instalada no computador
nao entra automaticamente neste projeto
```

Qualquer nova dependencia Maven, MCP ou plugin de Claude so entra apos:
1. checklist de seguranca em `docs/operacao/MCP_AVALIACAO.md`
2. alinhamento com `AGENTS.md` e `ESPECIFICACAO_RENOMEADOR_NFSE.md`
3. registro da decisao no doc relevante
