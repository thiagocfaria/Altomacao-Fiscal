---
name: equipe-testes-nfse
description: Use quando a tarefa envolver validacao, regressao, mudanca em parser ou pipeline, ou quando houver risco de pular etapas e gastar teste manual cedo demais.
---

# Equipe Testes NFSe

Voce protege a verdade dos testes do renomeador de NFS-e.

```text
fazer o teste certo
na ordem certa
e registrar o resultado certo
```

## Regras maes

1. Nunca declarar extracao correta sem rodar o teste de integracao com PDF real.
2. Nunca declarar watcher estavel sem soak test de pelo menos 30 minutos.
3. Nunca tratar mock de PDFBox como substituto de PDF real.
4. Nunca aceitar resultado de uma unica rodada — exige pelo menos duas comparaveis.
5. Sempre anotar os dois ultimos resultados comparaveis lado a lado.
6. Sempre destacar a metrica `ms_por_nota` no relatorio de performance.

## Escada oficial de testes

### Degrau 1: unitarios puros

```bash
mvn test
```

O que prova:
- logica de deteccao de layout (textos sinteticos — sem PDF real)
- sanitizacao de nome de arquivo (casos de borda: acento, barra, tamanho)
- regra de retencao (valorLiquido < valorServico, campos de retencao > 0)
- validacao de CNPJ (digito verificador)
- logica do Ledger (anti-reprocessamento via SHA-256)
- montagem do nome final com e sem `##RETIDO##`

O que nao prova: extracao real de PDF, watcher, IO de arquivo.

Resultado esperado: 0 falhas, 0 warnings, cobertura >= 80% das classes de `domain/` e `parser/`.

### Degrau 2: integracao com PDFs reais

```bash
mvn verify -Pintegration
```

PDFs do lote piloto disponivel em `NF MODELO ABRASP E PORTAL NACIONAL/`:

| Arquivo | Layout | Notas | CNPJ Tomador esperado | Retida? | Obs |
|---|---|---|---|---|---|
| `NF 5 OK.pdf` | Portal Nacional | 1 | 25.014.360/0001-73 | Nao | baseline PN simples |
| `NF 6 OK.pdf` | Portal Nacional | 1 | (confirmar) | Nao | |
| `NF 7 OK.pdf` | Portal Nacional | 1 | (confirmar) | Nao | |
| `NF 8 OK.pdf` | Portal Nacional | 1 | (confirmar) | Nao | |
| `NF 9 OK.pdf` | Portal Nacional | 1 | (confirmar) | Nao | |
| `NF 14 OK.pdf` | Portal Nacional | 1 | 25.014.360/0001-73 | Nao | baseline PN simples |
| `NF 15 OK.pdf` | Portal Nacional | 1 | (confirmar) | Nao | |
| `NF 55034 OK.pdf` | ABRASF | 1 | 04.116.617/0002-09 | Nao | baseline ABRASF simples |
| `NF 7022107 OK.pdf` | ABRASF | 1 | (confirmar) | (confirmar) | |
| `NotasPdf.pdf` | ABRASF | multi (6+) | 33.265.761/0001-24 | Varios | inclui 1 cancelada (NF 48) |

O que prova: extracao correta dos campos dos PDFs reais, deteccao de layout, separacao de multi-nota, deteccao de cancelada.

Regressao de campo obrigatorio → **faixa vermelha**, bloqueia fechamento.

### Degrau 3: end-to-end em pasta sintetica

Configurar empresa piloto em `empresas.yaml` apontando para uma pasta temporaria.
Copiar subconjunto do lote piloto para a pasta de entrada.
Rodar em modo batch:

```bash
java -jar nfse-renomeador.jar --mode=batch --config=config/empresas.yaml --empresa=piloto
```

O que prova: fluxo completo — leitura, processamento, renomeacao, roteamento (`processados/` vs `revisar/`), log, preservacao do original.

Verificar manualmente:
- original preservado em `originais/`
- arquivo renomeado em `processados/` com nome correto
- arquivo ambiguo ou de outro CNPJ em `revisar/`
- log explica cada decisao

### Degrau 4: soak do watcher

```bash
java -jar nfse-renomeador.jar --mode=watch --config=config/empresas.yaml --empresa=piloto
```

Inserir um PDF novo a cada 2 minutos por 30 minutos. Verificar:
- nenhum arquivo reprocessado (Ledger funcionando)
- sem leak de memoria (heap nao cresce monotonicamente)
- sem erro em arquivo ainda sendo copiado (StabilityChecker)
- log registra cada novo arquivo detectado

Monitorar heap durante o soak:
```bash
jcmd <PID> VM.native_memory scale=MB
```

### Degrau 5: benchmark JMH do parser

Obrigatorio quando mudar logica de extracao. Nao e para o loop diario.

```bash
mvn test -Pjmh -Dbenchmark=PortalNacionalParserBench
```

Unidade de medida: `ms por nota` (throughput ops/s × 1000).
Registrar: resultado anterior + resultado atual + data.

## KPIs deste projeto

| KPI | Baseline esperado | Faixa vermelha |
|---|---|---|
| `ms_por_nota` (lote de 10 PDFs) | < 500 ms/nota | >= 2000 ms/nota |
| heap do watcher em soak 30min | < 150 MB | >= 500 MB (sem GC entre chegadas) |
| tempo end-to-end lote de 10 | < 5 segundos total | >= 30 segundos |

## Como decidir o proximo teste

1. mudanca interna de logica (parser, Ledger, nome) → apenas `mvn test`
2. mudanca em extracao de campo → adiciona degrau 2 (PDF real)
3. mudanca no pipeline ou IO → adiciona degrau 3 (end-to-end sintetico)
4. mudanca no watcher ou ciclo de vida → adiciona degrau 4 (soak)
5. mudanca em hot path do parser ou suspicao de regressao de performance → adiciona degrau 5 (JMH)

## Fechamento padrao

Ao final, responda:

1. o que foi testado
2. em que ordem foi testado
3. o que passou
4. o que falhou
5. o que ainda falta testar
