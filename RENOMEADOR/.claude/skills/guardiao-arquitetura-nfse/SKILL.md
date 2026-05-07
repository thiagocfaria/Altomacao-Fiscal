---
name: guardiao-arquitetura-nfse
description: Use quando a mudanca tocar em nome de pacote, fronteira entre camadas, novo modulo Java, ou quando houver duvida sobre onde nova classe deve morar.
---

# Guardiao de Arquitetura NFSe

Proteja a separacao de responsabilidades do renomeador de NFS-e.

## Mapa atual

```text
br.com.nfse.renomeador/
├── config/      configuracao externa, empresas e caminhos
├── extraction/  extracao de notas e separacao de PDFs
├── files/       estabilidade, hash e preservacao de originais
├── layout/      classificacao de layout
├── ledger/      anti-reprocessamento
├── naming/      nome final do arquivo
├── parser/      Portal Nacional e ABRASF/ISSNet
├── pdf/         PDFBox e leitura de texto
├── processing/  regras de status e validacao de empresa
├── text/        normalizacao textual
└── App          entrada da aplicacao; CLI batch/watch pendente
```

## Regras

1. `parser/` nunca move arquivo nem escreve ledger.
2. `layout/` classifica; nao extrai todos os campos fiscais.
3. `files/` e `ledger/` nao conhecem regra fiscal.
4. `processing/` decide status com dados ja extraidos, sem IO.
5. `config/` carrega empresas e caminhos, sem decisao fiscal.
6. `batch/watch` devem orquestrar componentes existentes, sem duplicar parser.
7. Se uma mudanca exigir renomear pacotes, fazer em commit separado e com teste completo.

## Onde colocar novas classes

| Necessidade | Pacote |
|---|---|
| Scanner de entrada | `files/` ou novo `batch/`, conforme responsabilidade |
| Destino/movimentacao final | `files/` |
| Orquestracao batch | novo `batch/` ou `processing/runner`, sem misturar parser |
| WatchService | novo `watch/` |
| Novo layout | `parser/` + `layout/` |
| Nova regra fiscal | `processing/` |
| Novo campo textual compartilhado | `parser/`/`text/` |
