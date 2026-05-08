# RENOMEADOR NFS-e

Documentacao unica do modulo `RENOMEADOR/`.
Atualizado em 08/05/2026.

## 1. Objetivo

O RENOMEADOR automatiza o tratamento operacional de PDFs e XMLs de NFS-e recebidos nas pastas REST dos clientes.

Ele foi desenhado para:

- ler PDFs textuais e XMLs de NFS-e;
- separar PDFs com varias notas quando a divisao for segura;
- identificar layout homologado;
- extrair dados fiscais principais;
- validar o CNPJ do tomador;
- rotear notas que entraram na pasta errada;
- detectar cancelamento, retencao e duplicidade fiscal;
- gerar nome operacional padronizado;
- manter a pasta REST do cliente organizada em `PDF/` e `XML/`;
- registrar controles tecnicos fora da pasta do cliente.

O modulo nao faz OCR, nao consulta prefeitura, nao usa banco relacional, nao envia e-mail e nao usa IA.

## 2. Estado Atual

Versao operacional: V1.4, com limpeza operacional, controle de armazenamento, cadastro mensal, planilha compartilhada e organizacao de XML.

Estado tecnico em 08/05/2026:

- codigo Java 17/Maven em `RENOMEADOR/`;
- `PLANILHA_FISCAL.xlsm` permanece na raiz como cadastro compartilhado;
- `pom.xml` da raiz agrega o modulo `RENOMEADOR`;
- `batch`, `watch` e ferramentas `config` implementados;
- importacao multi-mes da planilha implementada;
- roteamento por data de emissao da nota implementado;
- duplo clique da planilha corrigido por macro de workbook para todas as abas `CADASTRO ...`;
- batch de producao evita dupla importacao com `--sem-atualizar-planilha`;
- logs, ledger, indice de duplicidade e temporarios ficam em `backend/`;
- ledger e indice de duplicidade ficam particionados por mes de execucao;
- batch/watch gravam painel operacional em `backend/painel-operacional.tsv`;
- watch grava saude em `backend/health/watch-status.json` com status `OK` ou `ATENCAO` e faz varredura periodica;
- recuperacao de `TOMADOR NAO ENCONTRADO` respeita o mes de emissao da nota antes de mover a pendencia;
- movimentacao em rede usa copia verificada antes de apagar a origem quando nao ha movimento atomico seguro;
- `empresas.yaml` e validado em modo estrito: campo desconhecido gera erro antes da execucao;
- `backendRoot` pode ser definido no YAML para fixar logs, indices, painel, healthcheck e lock em local oficial;
- PDFs acima de 50MB ou acima de 80 paginas caem em revisao antes da extracao textual pesada;
- XMLs do Portal Nacional sao lidos, renomeados e organizados com a mesma regra fiscal usada para PDFs;
- REST do cliente separa saida final por tipo: `PDF/...` e `XML/...`;
- batch/watch usam fila tecnica limitada para processamento com timeout, evitando crescimento sem controle de threads;
- logs TSV, ledger e indice de duplicidade escapam tab/quebra de linha e preservam linhas corrompidas em `.corrompidas`;
- manutencao tecnica compacta logs antigos, limpa `split-work/` antigo e gera `relatorio-revisar.tsv`;
- release Windows tem rotina para `dependency:tree`, integracao, JAR final e `java -jar --help`;
- caminhos tecnicos relativos sao validados para nao escapar da REST da empresa e `backendRoot` nao pode ficar dentro da REST;
- suite de regressao exige lote minimo de PDFs reais/extremos em `src/test/resources/nfse-modelos/`;
- REST do cliente recebe apenas entradas operacionais na raiz e saidas organizadas por tipo.

Validacao recente:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR test
# 148 testes, 0 falhas

mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration
# 148 testes unitarios + 1 teste de integracao, 0 falhas
```

Pendente antes de declarar producao final:

- validar no Windows/Excel oficial da operacao;
- confirmar unidade `W:` mapeada;
- executar `config check` com os caminhos reais;
- rodar batch em homologacao com conferencia humana;
- definir rotina final: batch agendado ou watch continuo.

## 3. Estrutura do Modulo

```text
RENOMEADOR/
├── AGENTS.md
├── DOCUMENTACAO_RENOMEADOR_NFSE.md
├── docs/
│   └── fluxo-sistema-nfse.svg
├── empresas.example.yaml
├── pom.xml
├── scripts/windows/
│   ├── compilar.bat
│   ├── corrigir-macro-planilha.vbs
│   ├── importar-planilha.bat
│   ├── preparar-planilha.bat
│   ├── rodar-batch-homologacao.bat
│   ├── rodar-batch-producao.bat
│   ├── rodar-watch.bat
│   └── validar-config.bat
└── src/
    ├── main/java/br/com/nfse/renomeador/
    └── test/
```

Arquivos gerados nao entram no Git:

- `RENOMEADOR/target/`;
- `RENOMEADOR/operacao/`;
- `backend/`;
- logs;
- YAML operacional gerado;
- PDFs/XMLs operacionais.

## 4. Arquitetura Java

Pacotes principais:

| Pacote | Responsabilidade |
|---|---|
| `config/` | carrega `empresas.yaml`, seleciona empresas, resolve caminhos e valida cadastro |
| `config/excel/` | importa/prepara a planilha Excel compartilhada |
| `extraction/` | extrai dados de NFS-e em PDF/XML e separa PDFs agrupados |
| `files/` | hash SHA-256 e estabilidade de arquivo |
| `layout/` | classifica layout textual do PDF |
| `ledger/` | ledger de anti-reprocessamento e indice de duplicidade |
| `naming/` | nome final operacional |
| `parser/` | parsers Portal Nacional PDF/XML e ABRASF/ISSNet |
| `pdf/` | integracao com PDFBox |
| `pipeline/` | orquestracao, destino, logs, backend e scanner |
| `processing/` | decisao de status fiscal/operacional |
| `text/` | normalizacao textual |
| `app/` | runners batch/watch e trava de instancia |
| `App.java` | CLI Picocli |

Fronteiras importantes:

- parser nao move arquivo;
- layout apenas classifica;
- files e ledger nao interpretam NFS-e;
- processing decide status sem fazer IO;
- config nao contem regra fiscal;
- batch/watch orquestram componentes existentes.

## 5. Entradas e Saidas

Entrada principal:

- PDFs e XMLs soltos na raiz das pastas REST cadastradas na planilha;
- `PLANILHA_FISCAL.xlsm` na raiz do projeto;
- `RENOMEADOR/operacao/empresas.yaml` gerado a partir da planilha.

Saidas na REST do cliente:

```text
CAMINHO REST/
├── PDF/
│   ├── processados/
│   ├── RETIDO/
│   ├── canceladas/
│   └── TOMADOR NAO ENCONTRADO/
└── XML/
    ├── processados/
    ├── RETIDO/
    ├── canceladas/
    └── TOMADOR NAO ENCONTRADO/
```

Semantica das pastas:

- `processados/`: notas validas sem retencao;
- `RETIDO/`: notas validas com retencao;
- `canceladas/`: notas canceladas;
- `TOMADOR NAO ENCONTRADO/`: nota entrou em REST errada e o tomador ainda nao tem REST ativo no cadastro.

Saidas tecnicas no backend do sistema:

```text
RENOMEADOR/operacao/backend/
├── painel-operacional.tsv
├── health/
│   └── watch-status.json
├── locks/
└── empresas/<empresa_id>/
    ├── execucao-AAAA-MM.tsv
    ├── AAAA-MM/
    │   ├── processados.idx
    │   └── duplicadas.idx
    ├── revisar/
    └── split-work/
```

`painel-operacional.tsv` e o painel simples para a operacao e para conferencia
no Dashboard do Excel. Ele recebe uma linha por empresa/resumo com:

- `OK` quando nao ha pendencia;
- `ATENCAO` quando houve documento em revisao, erro de movimentacao/renomeacao, timeout ou item ignorado;
- totais de `ok`, `revisar`, `canceladas`, `duplicadas`, `ignorados` e `erros`;
- acao operacional `VERIFICAR_REVISAR_E_LOG` quando alguem precisa olhar a pasta `revisar/` ou o log.

Saida de saude do watch:

```text
RENOMEADOR/operacao/backend/health/watch-status.json
```

O `watch-status.json` tambem muda para `status: "ATENCAO"` quando a ultima
varredura/evento encontrou revisao, erro ou ignorado.

O sistema nao cria `logs/`, `ledger`, `originais/` ou `split-work/` dentro da REST do cliente.
IMPORT API PN deve depositar XML/PDF apenas na raiz do `CAMINHO REST`; `PDF/` e `XML/` sao pastas de saida do RENOMEADOR.

## 6. Planilha Fiscal

Arquivo compartilhado:

```text
PLANILHA_FISCAL.xlsm
```

Abas esperadas:

- `DASHBOARD`;
- `CADASTRO ABRIL`;
- `CADASTRO MAIO`;
- `CADASTRO JUNHO`;
- `CADASTRO JULHO`;
- `CADASTRO AGOSTO`;
- `CADASTRO SETEMBRO`;
- `CADASTRO OUTUBRO`;
- `CADASTRO NOVEMBRO`;
- `CADASTRO DEZEMBRO`;
- `CONFIG`.

Colunas relevantes para o RENOMEADOR:

| Coluna | Uso pelo RENOMEADOR |
|---|---|
| `CLIENTE` | gera identificador legivel da empresa |
| `CNPJ` | CNPJ esperado do tomador |
| `CAMINHO REST` | pasta direta monitorada, entrada unica de PDF/XML e destino operacional do mes |
| `SOMENTE ORIGEM` | marca pasta generica/pasta errada que nunca deve ser destino por CNPJ |

Colunas que ficam para outros modulos ou operacao:

- `CAMINHO DMS`;
- `CAMINHO ENTRADA/SAIDA`;
- `CAMINHO CERTIFICADO DIGITAL`;
- `VALIDADE CERTIFICADO DIGITAL`;
- `SENHA CERTIFICADO DIGITAL`.

Regra mensal:

- sem `--mes`, a importacao le todas as abas `CADASTRO MES`;
- cada linha importada recebe `mes: "AAAA-MM"`;
- PDF e XML sao roteados pela data de emissao extraida da propria nota;
- documento de abril usa caminho de abril;
- documento de maio usa caminho de maio;
- se o mes correto nao tiver `CAMINHO REST`, a nota vai para `PDF/TOMADOR NAO ENCONTRADO/` ou `XML/TOMADOR NAO ENCONTRADO/`.

Observacao deliberada:

- o ano das abas mensais usa o ano da execucao atual;
- essa regra foi mantida por decisao operacional e sera revista somente no proximo ano, se necessario.

## 6.1 Configuracao Tecnica YAML

O operador trabalha na `PLANILHA_FISCAL.xlsm`. O arquivo `empresas.yaml` e o
arquivo tecnico gerado/validado pelo RENOMEADOR.

Campo tecnico opcional da raiz:

```yaml
backendRoot: "W:/ALTOMACAO/RENOMEADOR/backend"
```

Quando `backendRoot` existe, o sistema guarda nesse local:

- painel operacional;
- healthcheck do watch;
- logs de execucao;
- ledger;
- indice de duplicidade;
- pasta `revisar/`;
- temporarios `split-work/`;
- lock de instancia.

Se `backendRoot` nao existir, o padrao continua sendo `backend/` ao lado do
`empresas.yaml`.
Se `backendRoot` for relativo, ele tambem e resolvido a partir da pasta onde
esta o `empresas.yaml`.

O YAML e estrito. Campos desconhecidos geram erro, por exemplo:

```yaml
habilitda: false
```

Resultado esperado:

```text
Campo desconhecido em empresa: habilitda
```

Isso nao muda o preenchimento da planilha por duplo clique. A protecao serve
para impedir YAML editado manualmente ou gerado errado de rodar silenciosamente.

Regras de seguranca de caminhos:

- `entrada`, `processados`, `revisar`, `canceladas`, `logs`, `ledger` e demais subpastas tecnicas devem ser relativas;
- subpasta com `..` ou caminho absoluto e recusada quando escapar da pasta da empresa;
- `backendRoot` nao pode ficar dentro da REST de nenhum cliente.

## 7. Macro de Duplo Clique

O preparo da planilha pelo script Windows executa:

```bat
scripts\windows\corrigir-macro-planilha.vbs C:\caminho\PLANILHA_FISCAL.xlsm
```

Esse script instala `Workbook_SheetBeforeDoubleClick` em `EstaPastaDeTrabalho`, cobrindo todas as abas `CADASTRO ...`.

O duplo clique abre seletor de pasta nas colunas:

- `CAMINHO DMS`;
- `CAMINHO REST`;
- `CAMINHO ENTRADA/SAIDA`;
- `CAMINHO CERTIFICADO DIGITAL`.

Se o Excel bloquear o script, habilite:

```text
Arquivo > Opcoes > Central de Confiabilidade > Configuracoes de Macro
```

E marque:

```text
Confiar no acesso ao modelo de objeto do projeto VBA
```

Tambem confirme:

- arquivo salvo como `.xlsm`;
- macros habilitadas;
- arquivo desbloqueado nas propriedades do Windows, se veio de download;
- pasta de rede adicionada como local confiavel, se aplicavel.

## 8. Layouts Homologados

| Layout | Assinatura textual |
|---|---|
| Portal Nacional DANFSe v1.0 | contem `DANFSe v1.0` e `Numero da DPS` |
| ABRASF/ISSNet municipal | contem `Nota Fiscal de Servico Eletronica` e `Cod. de Autenticidade` |

PDF sem texto selecionavel ou layout nao homologado vai para revisao tecnica no backend.

## 9. Regras Operacionais

Nome operacional:

```text
NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valor>.pdf
NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valor>.xml
```

Retencao:

- quando retida, vai para `PDF/RETIDO/` ou `XML/RETIDO/`;
- nome recebe `##IR_RETIDO##`;
- a deteccao usa valor liquido menor que valor do servico ou campos explicitos de retencao positivos.

Cancelamento:

- cancelada vai para `PDF/canceladas/` ou `XML/canceladas/`;
- nome recebe `##CANCELADA##`;
- cancelamento tem prioridade sobre fluxo normal.

Tomador incorreto:

- se o CNPJ do tomador nao bate com a REST atual, o sistema procura o CNPJ no cadastro;
- se houver REST ativa no mes correto, move para o destino correto;
- se nao houver REST ativa, move para `PDF/TOMADOR NAO ENCONTRADO/` ou `XML/TOMADOR NAO ENCONTRADO/`;
- quando o cliente ganhar caminho ativo no cadastro, batch/watch recuperam a pendencia;
- a recuperacao so tira uma pendencia de `PDF/TOMADOR NAO ENCONTRADO/` ou `XML/TOMADOR NAO ENCONTRADO/` quando o mes de emissao da nota tem caminho REST ativo;
- se a pendencia for de abril e so houver caminho de maio, ela permanece na pasta de tomador nao encontrado do proprio tipo.

Movimentacao de arquivos:

- quando o movimento atomico nao e seguro ou nao e suportado, o sistema usa `copiar -> verificar conteudo -> renomear destino -> verificar destino -> apagar origem`;
- a origem nao e apagada antes da copia final ser validada;
- falha de movimentacao, renomeacao, PDF/XML invalido ou layout nao suportado aparece no log operacional e no `painel-operacional.tsv` como `ATENCAO`.

PDF pesado ou suspeito:

- arquivo acima de 50MB vai direto para `backend/empresas/<empresa>/revisar/` com prefixo `ARQUIVO_MUITO_GRANDE_`;
- PDF acima de 80 paginas vai direto para revisao com prefixo `PAGINAS_DEMAIS_`;
- batch/watch usam executor limitado para impedir criacao sem controle de threads;
- timeout tambem envia o PDF para revisao e aparece no painel como `ATENCAO`.

Logs e controles tecnicos:

- campos TSV escapam tab, `\r` e `\n`;
- ledger e indice de duplicidade continuam lendo linhas validas quando encontram linha ruim;
- linha malformada e movida para arquivo `.corrompidas` ao lado do ledger/indice para auditoria.

Duplicidade:

- ledger evita reprocessar o mesmo PDF por SHA-256;
- indice fiscal evita duas copias operacionais da mesma NFS-e;
- Portal Nacional tem preferencia sobre ABRASF quando os campos fiscais equivalentes batem;
- duplicata so e descartada/removida quando a chave fiscal completa bate e o arquivo esta em pasta operacional controlada.

## 10. CLI

Ajuda:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar --help
```

Preparar planilha:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config preparar-planilha --entrada ../PLANILHA_ORIGINAL.xlsm --saida ../PLANILHA_FISCAL.xlsm
```

Importar todos os meses:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config import-excel --planilha ../PLANILHA_FISCAL.xlsm --saida operacao/empresas.yaml --sobrescrever
```

Importar mes especifico:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config import-excel --planilha ../PLANILHA_FISCAL.xlsm --saida operacao/empresas.yaml --sobrescrever --mes 2026-05
```

Validar configuracao:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config check --config operacao/empresas.yaml
```

Batch em homologacao:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config operacao/empresas.yaml --homologacao
```

Batch usando YAML ja importado/validado:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config operacao/empresas.yaml --sem-atualizar-planilha
```

Watch:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --config operacao/empresas.yaml --planilha ../PLANILHA_FISCAL.xlsm
```

Manutencao tecnica:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar manutencao limpar-tecnicos --backend operacao/backend
```

A manutencao compacta logs antigos, aplica limite de armazenamento, remove
`split-work/` antigo e gera `relatorio-revisar.tsv` quando houver PDF pendente
em revisao.

Nao rode `batch` e `watch` ao mesmo tempo com o mesmo `empresas.yaml`. O sistema possui trava de instancia por config, mas a rotina operacional deve escolher um modo.

## 11. Scripts Windows

Diretorio:

```text
RENOMEADOR\scripts\windows\
```

Scripts:

| Script | Funcao |
|---|---|
| `compilar.bat` | compila o JAR com Maven |
| `preparar-planilha.bat` | prepara a planilha e corrige macro de duplo clique |
| `corrigir-macro-planilha.vbs` | instala macro de workbook para todas as abas mensais |
| `importar-planilha.bat` | importa Excel para YAML |
| `validar-config.bat` | valida YAML |
| `rodar-batch-homologacao.bat` | roda batch preservando entrada |
| `rodar-batch-producao.bat` | importa, valida e roda batch real |
| `rodar-watch.bat` | inicia watcher |
| `verificar-release.bat` | registra dependencias, roda testes, gera JAR e testa `java -jar --help` |

Fluxo recomendado de producao inicial:

```bat
cd RENOMEADOR
scripts\windows\compilar.bat
scripts\windows\rodar-batch-producao.bat --homologacao
```

Depois de conferir as saidas:

```bat
scripts\windows\rodar-batch-producao.bat
```

## 12. Validacao

Comandos canonicos:

```bash
mvn test
mvn verify -Pintegration
```

Neste ambiente Linux, use:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration
```

Escada de producao:

1. `mvn test`;
2. `mvn verify -Pintegration`;
3. `config import-excel` da planilha real;
4. `config check` no Windows com `W:` mapeado;
5. batch em homologacao;
6. conferencia das pastas `PDF/...`, `XML/...` e backend;
7. batch real ou watch continuo;
8. soak de watcher por 1h se a escolha operacional for `watch`.

Rotina de release:

```bat
scripts\windows\verificar-release.bat
```

Essa rotina registra `target\dependency-tree.txt`, roda `mvn verify -Pintegration`,
gera o JAR final e valida a ajuda do JAR com `java -jar`.

Indicadores:

- KPI principal: `duracaoMs` por arquivo no log operacional;
- KPI secundario: memoria do watcher em soak de 1h.

## 13. Como Continuar o Desenvolvimento

Antes de alterar comportamento fiscal ou pipeline:

1. leia `RENOMEADOR/AGENTS.md`;
2. leia este documento;
3. localize pacote responsavel;
4. escreva teste focado;
5. implemente mudanca pequena;
6. rode a escada de validacao aplicavel.

Para adicionar novo layout:

- criar fixture PDF em `src/test/resources/nfse-modelos/`;
- manter a suite minima de PDFs reais/extremos com Portal Nacional, ABRASF, lote multipagina e layout nao suportado;
- estender `LayoutDetector`;
- criar parser isolado em `parser/`;
- manter regras fiscais em `processing/`;
- testar nomeacao, destino e revisao.

Para alterar planilha:

- preservar `PLANILHA_FISCAL.xlsm` na raiz;
- nao mover a planilha para dentro de modulo;
- manter `CAMINHO REST` como fonte do RENOMEADOR;
- nao usar colunas de outros modulos como destino do RENOMEADOR sem decisao explicita.

Para alterar producao:

- nao criar logs/ledger/originais dentro da REST do cliente;
- nao duplicar PDFs originais em backend;
- nao apagar arquivo fora de pasta operacional controlada;
- nao ignorar `config check`.

## 14. Limites Conhecidos

- PDF escaneado/imagem vai para revisao; nao ha OCR.
- Layout nao homologado vai para revisao.
- Ano das abas mensais segue o ano da execucao atual.
- Validacao real de caminhos Windows `W:/...` precisa acontecer no Windows da empresa.
- A macro de duplo clique depende das politicas de seguranca do Excel.
