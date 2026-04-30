---
name: layout-nfse
description: Use sempre que tocar em deteccao de layout, extracao de campos, regras de retencao ou tratamento de nota cancelada. Esta skill e o conhecimento-dominio do projeto.
---

# Layout NFS-e — Conhecimento de Dominio

Voce e o especialista nos dois layouts de NFS-e homologados. Voce conhece onde cada campo fica e como interpretar os valores corretamente.

## Dois layouts homologados

### Layout 1: Portal Nacional (DANFSe v1.0)

**Assinatura de deteccao (AMBAS devem estar presentes no texto):**
```
"DANFSe v1.0"
"Numero da DPS"
```

**Campos e onde ficam no texto extraido:**

| Campo | Rotulo no texto | Observacao |
|---|---|---|
| Numero da NFS-e | `Numero da NFS-e` (na linha seguinte vem o valor) | Pode ser apenas numerico |
| Competencia | `Competencia da NFS-e` | Data no formato dd/MM/yyyy |
| Data de emissao | `Data e Hora da emissao da NFS-e` | Formato: dd/MM/yyyy HH:mm:ss |
| CNPJ Prestador | Bloco `EMITENTE DA NFS-e` → `CNPJ / CPF / NIF` | Formato com pontos e barra |
| Nome Prestador | `Nome / Nome Empresarial` (primeiro apos EMITENTE) | Pode incluir CNPJ no inicio |
| CNPJ Tomador | Bloco `TOMADOR DO SERVICO` → `CNPJ / CPF / NIF` | Formato com pontos e barra |
| Nome Tomador | `Nome / Nome Empresarial` (segundo, apos TOMADOR) | Razao social |
| Valor do Servico | Bloco `VALOR TOTAL DA NFS-E` → `Valor do Servico` | R$ com virgula decimal |
| Valor Liquido | `Valor Liquido da NFS-e` | Mesma linha ou proxima |
| Retencao ISSQN | `Retencao do ISSQN` | Valor `Nao Retido` ou valor numerico |
| Retencoes Federais | `Total das Retencoes Federais` | `-` ou valor numerico |
| Chave de acesso | `Chave de Acesso da NFS-e` | 50 digitos numericos |

**Amostras confirmadas:**
- `NF 5 OK.pdf` — NF 5, Nova Mutum MT, CNPJ tomador 25.014.360/0001-73, R$ 256,50, sem retencao
- `NF 6 OK.pdf`, `NF 7 OK.pdf`, `NF 8 OK.pdf`, `NF 9 OK.pdf` — mesmo padrao
- `NF 14 OK.pdf` — NF 14, Itumbiara GO, R$ 6.000,00, sem retencao
- `NF 15 OK.pdf` — mesmo padrao

---

### Layout 2: ABRASF municipal

**Assinatura de deteccao (AMBAS devem estar presentes no texto):**
```
"Nota Fiscal de Servico Eletronica"
"Cod. de Autenticidade"
```

**Campos e onde ficam no texto extraido:**

| Campo | Rotulo no texto | Observacao |
|---|---|---|
| Numero da NFS-e | `Numero da Nota Fiscal` (canto superior direito) | Numerico |
| Data de geracao | `Data de Geracao da NFS-e` | Formato: dd/MM/yyyy HH:mm:ss |
| Data de competencia | `Data de Competencia` | Formato: dd/MM/yyyy |
| CNPJ Prestador | `CPF/CNPJ` (bloco do prestador) | Apos `Inscricao Municipal` |
| Nome Prestador | Primeira linha em negrito do bloco prestador | Razao social + nome fantasia |
| CNPJ Tomador | `CNPJ/CPF :` | Com dois pontos e espaco |
| Nome Tomador | `Razao Social :` | Com dois pontos e espaco |
| Valor Total | `Vl. Total dos Servicos` | Tabela Detalhamento dos Tributos |
| Valor Liquido | `Vl. Liquido da NotaFiscal` | Mesma tabela |
| ISSQN Retido | `ISSQN Retido` (coluna da tabela) | `Nao` ou valor numerico |
| Vl. ISSQN Retido | `Vl. ISSQN Retido` | Valor numerico; `R$ 0,00` = sem retencao |
| Outras Retencoes | `Outras Retencoes` | Verificar se > 0 |
| Cancelada | `NOTA CANCELADA` ou `Data de cancelamento` | Watermark ou rodape |
| Chave SEFIN | `Chave de acesso no Ambiente de Dados Nacional` | 50 digitos + datestamp |

**Amostras confirmadas:**
- `NF 55034 OK.pdf` — ABRASF Cuiaba MT, NF 55034, R$ 114,00, tomador 04.116.617/0002-09
- `NF 7022107 OK.pdf` — verificar ao implementar
- `NotasPdf.pdf` — arquivo multi-nota ABRASF de Anapolis GO:
  - NF 346928: tomador 33.265.761/0001-24, R$ 1.288,52
  - NF 889: tomador 33.265.761/0001-24, R$ 125,97
  - NF 252: tomador 33.265.761/0001-24, R$ 5.115,88
  - NF 362: tomador 33.265.761/0001-24, R$ 450,00
  - **NF 48: CANCELADA** — texto contem "NOTA CANCELADA SEM VALOR LEGAL"
  - NF 49: tomador 33.265.761/0001-24, R$ 25.000,00
  - NF 50: tomador 33.265.761/0001-24, R$ 20.000,00

---

## Regra de retencao de impostos

Uma NFS-e tem retencao quando **qualquer** das condicoes abaixo for verdadeira:

```
valorLiquido < valorServico
OU
ISSQN Retido != "Nao Retido" / "Nao" / "-"
OU
Total das Retencoes Federais != "-"  (Portal Nacional)
OU
Vl. ISSQN Retido > 0
OU
IRRF > 0 OU INSS > 0 OU PIS > 0 OU COFINS > 0 OU CSLL > 0 OU Outras Retencoes > 0
```

Marcador no nome quando retida: `##RETIDO##`
Marcador quando cancelada: `##CANCELADA##` + pasta `revisar/canceladas/`

---

## Separacao de arquivo multi-nota

**Estrategia para ABRASF:**
- Cada nota ocupa exatamente uma pagina (confirmado no `NotasPdf.pdf`)
- Usar `PDDocument.split()` para separar por pagina
- Contar ocorrencias de `"Numero da Nota Fiscal"` no texto total
- Se `count(marcadores) != count(paginas)` → ambiguo → enviar para `revisar/` sem separar

**Estrategia para Portal Nacional:**
- Verificar NFs com multiplas paginas no lote piloto antes de implementar
- Marcador de nova nota: `"DANFSe v1.0"` + `"Chave de Acesso da NFS-e"`

---

## Formato do nome de arquivo final

```
NFSE_<numero>_<prestador>_<dataAAAAMMDD>_<valor>[##RETIDO##].pdf
```

Exemplos:
```
NFSE_5_KELLE_EVANETE_LEMES_DE_ALMEIDA_20260417_256,50.pdf
NFSE_55034_UMA_MEDICINA_E_SEGURANCA_DO_TRABALHO_LTDA_20260305_114,00.pdf
NFSE_346928_UNIMED_ANAPOLIS_20260302_1288,52##RETIDO##.pdf   (se retida)
MODELO_NAO_SUPORTADO_<sha8>_<dataChegada>.pdf
```

Regras de sanitizacao do nome:
- Remover: `/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`
- Espacos → `_`
- Acentos → versao sem acento (NFD normalize + strip combining)
- Tamanho maximo: 200 caracteres antes da extensao
- Colisao: sufixo `_01`, `_02` etc.

---

## Quando entrar

- mudanca em `LayoutDetector`, `PortalNacionalParser` ou `AbrasfParser`
- adicao de novo campo a extrair
- duvida sobre onde um campo especifico esta no texto do PDF
- novo layout a homologar
- bug em extracao de valor, data ou CNPJ

## Fechamento padrao

Ao final, responda:

1. qual layout foi analisado
2. quais campos foram verificados ou corrigidos
3. se o teste com PDF real confirma a extracao
4. se algum caso de borda foi identificado (cancelada, sem campo, multi-nota)
