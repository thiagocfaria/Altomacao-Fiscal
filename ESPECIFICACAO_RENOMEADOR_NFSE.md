# Plano de Implementacao - Renomeador e Separador Automatico de PDFs de NFS-e

> **For agentic workers:** REQUIRED: Use `superpowers:subagent-driven-development` (if subagents available) or `superpowers:executing-plans` to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** automatizar o tratamento inicial de PDFs de NFS-e diretamente na estrutura de pastas ja usada por cada empresa, sem copiar arquivos para dentro do repositorio do codigo, separando notas agrupadas, identificando o layout, validando o CNPJ do tomador, detectando retencao, tratando cancelamentos e renomeando o PDF com um nome operacional claro.

**Architecture:** a aplicacao Java ficara em um repositorio externo e sera implantada separadamente na maquina de uso. A V1 deve suportar os dois modos no mesmo binario: `watch`, para vigiar somente as pastas ativas e processar quando um arquivo novo entrar, e `batch`, para execucao manual ou por `.bat`/Agendador do Windows como rede de seguranca. A configuracao sera externa, em cadastro de empresas com CNPJ esperado do tomador, estrategia de pasta mensal e caminhos de destino; o sistema processa direto na pasta da empresa, preserva o original, evita reprocessamento com ledger persistente e manda excecoes para revisao.

**Tech Stack:** Java 17, Maven, Apache PDFBox, Apache POI, `java.nio.file`, `WatchService`, arquivo externo de configuracao em `.yaml` lido com Jackson YAML, JUnit 5, AssertJ, SLF4J/Logback, Picocli para CLI, `.bat` opcional para modo `batch`. A V1 trabalha apenas com PDFs textuais; PDF sem texto selecionavel suficiente vai para revisao, sem OCR.

---

## 0. Estado atual da implementacao

Este documento continua como roteiro tecnico da V1. O diario operacional fica em `SITUACAO_ATUAL.md`.

Status em 05/05/2026:

- [x] repositorio Git inicializado;
- [x] projeto Java 17/Maven criado;
- [x] nucleo inicial de leitura PDFBox criado;
- [x] detector de layout Portal Nacional, ABRASF/ISSNet, sem texto e nao suportado criado;
- [x] parsers iniciais de Portal Nacional e ABRASF/ISSNet criados;
- [x] validacao inicial de CNPJ, cancelamento, retencao e nomeacao criada;
- [x] testes automatizados iniciais passando com os PDFs modelo;
- [x] configuracao externa `empresas.yaml`;
- [x] processamento `batch` completo com movimentacao;
- [x] ledger persistente;
- [x] preservacao de originais;
- [x] separacao fisica dos PDFs agrupados por pagina segura;
- [x] modo `watch`;
- [x] importacao de planilha Excel para `empresas.yaml`;
- [x] preparacao de `PLANILHA_FISCAL_MODELO.xlsm` com visual PROTONS e 30 linhas extras para novos clientes;
- [x] scripts Windows basicos;
- [ ] homologacao operacional em pasta real.

## 1. Resumo simples da ideia

O sistema vai funcionar como um organizador automatico de NFS-e, mas sem depender da pasta do projeto.

O codigo fica no seu repositório. Depois ele sera levado para a maquina deles e recebera uma configuracao externa com:

- qual empresa esta sendo observada;
- qual CNPJ do tomador deve aparecer nas notas daquela empresa;
- qual pasta ou pastas mensais devem ser processadas;
- para onde mandar `processados/`, `revisar/`, `originais/` e `logs/`.

O fluxo desejado e este:

1. o programa roda em `watch` ou em `batch`;
2. ele descobre quais empresas e meses estao ativos;
3. se estiver em `watch`, ele so acorda quando entrar PDF novo;
4. se estiver em `batch`, ele faz uma passada unica e termina;
5. antes de processar, ele espera o PDF estabilizar;
6. ele confere no ledger se o arquivo ja foi tratado;
7. ele preserva o original;
8. ele le o texto do PDF;
9. ele descobre se ha uma nota ou varias;
10. ele separa quando houver fronteira confiavel;
11. ele identifica se o layout e Portal Nacional ou ABRASF;
12. se nao for um layout homologado, marca `MODELO NAO SUPORTADO`;
13. ele extrai os campos principais;
14. ele valida se o tomador bate com o CNPJ da empresa configurada;
15. se nao bater, marca `CNPJ INCORRETO PARA REPOSITORIO`;
16. ele detecta se a nota esta cancelada;
17. ele detecta se existe retencao e, quando houver, acrescenta `##IR_RETIDO##`;
18. ele renomeia e move para o destino correto;
19. se houver qualquer duvida, manda para `revisar/` com motivo claro.

A V1 continua gradual: homologacao empresa por empresa, sem OCR, sem banco, sem tela e sem integracoes externas.

## 2. Problema que o projeto resolve

Hoje os PDFs chegam com nomes aleatorios e ficam dispersos na estrutura de pastas da empresa, o que dificulta:

- localizar notas rapidamente;
- conferir valores;
- identificar prestador;
- identificar tomador correto;
- identificar retencao;
- detectar nota na pasta errada;
- separar arquivos com varias notas juntas;
- reduzir trabalho manual repetitivo.

O projeto resolve isso usando o conteudo real da nota e o contexto da empresa para organizar o documento no lugar certo, com nome padrao, preservando o original e isolando casos duvidosos para revisao humana.

## 3. Escopo da primeira versao

### Inclui

- codigo mantido em repositorio externo ao ambiente da empresa;
- implantacao separada do codigo na maquina deles;
- dois modos de execucao no mesmo binario:
  - `watch`, recomendado para operacao;
  - `batch`, para execucao manual ou por `.bat`;
- cadastro externo de uma ou mais empresas em arquivo `.yaml`;
- homologacao inicial gradual, empresa por empresa;
- leitura de caminhos externos configurados por empresa;
- resolucao de pasta mensal por estrategia configuravel;
- processamento direto na arquitetura de pastas da empresa, sem trazer PDF para dentro do projeto;
- leitura de PDFs com texto selecionavel/copiavel;
- identificacao de uma ou varias notas no mesmo PDF;
- separacao das notas quando necessario;
- identificacao de layout homologado;
- extracao dos principais campos fiscais;
- validacao do CNPJ do tomador contra o CNPJ esperado da empresa configurada;
- tratamento de `MODELO NAO SUPORTADO`;
- tratamento de `CNPJ INCORRETO PARA REPOSITORIO`;
- tratamento de nota cancelada com `##CANCELADA##`;
- deteccao de retencao com `##IR_RETIDO##`;
- ledger persistente para evitar reprocessamento;
- renomeacao padronizada;
- envio de casos duvidosos para revisao;
- log do processamento.

### Nao inclui

- OCR para PDFs escaneados ou imagem;
- interface grafica;
- banco de dados relacional;
- interface manual no Excel alem da importacao/preparacao da planilha fiscal;
- integracao com prefeitura;
- processamento paralelo complexo entre varias empresas;
- validacoes fiscais avancadas fora do escopo da V1;
- envio automatico de e-mail;
- uso de IA.

## 4. Modelo de implantacao e configuracao

O projeto tera duas partes separadas:

1. **Repositorio do codigo**
   - fica no seu GitHub;
   - serve para desenvolver, versionar e publicar o programa;
   - nao guarda os PDFs operacionais das empresas.

2. **Execucao na maquina da empresa**
   - recebe o programa compilado;
   - usa um arquivo de configuracao externo;
   - trabalha direto na arquitetura de pastas da empresa.

### 4.1 Cadastro de empresas

A V1 deve nascer com cadastro externo preparado para varias empresas, mesmo que a homologacao inicial use apenas uma.

Exemplo conceitual:

```yaml
empresas:
  - id: empresa_a
    habilitada: true
    cnpjTomador: "12.345.678/0001-99"
    estrategiaMes: "atual" # atual | informado | lista | direto
    meses: []
    pastaBase: "D:/Empresas/EmpresaA/NFSe"
    subpastaMes: "{AAAA}/{MM}"
    pastas:
      entrada: "entrada"
      processados: "processados"
      revisar: "revisar"
      originais: "originais"
      logs: "logs"
      canceladas: "revisar/canceladas"
      ledger: "logs/processados.idx"
```

Observacoes:

- o sistema nao deve impor pastas dentro do repositorio do codigo;
- a configuracao deve aceitar uma pasta de entrada ja existente, com PDFs ja presentes;
- a empresa pode usar caminho direto ou pasta mensal;
- quando a empresa usa pasta mensal, o sistema deve resolver apenas o mes ativo ou a lista configurada;
- a mesma estrutura deve servir para crescer para varias empresas sem refatoracao estrutural;
- para homologacao com os PDFs modelo dentro do projeto, o `batch` deve ter uma opcao de preservar a pasta de entrada, gerando saidas em uma pasta de destino sem mover nem apagar os PDFs de exemplo.

### 4.2 Estrategia de pasta mensal

Para reduzir processamento inutil, a configuracao deve suportar estes modos:

- `atual`: processa apenas o mes atual;
- `informado`: processa um mes informado via CLI;
- `lista`: processa uma lista controlada de meses;
- `direto`: usa o caminho exato sem resolver subpasta mensal.

## 5. Fluxo operacional esperado

```text
modo watch ou batch e iniciado
-> sistema carrega o cadastro de empresas
-> sistema resolve empresas e meses ativos
-> em watch: registra somente as pastas ativas no WatchService
-> em batch: varre somente as pastas ativas uma vez
-> ao encontrar PDF candidato, espera o arquivo estabilizar
-> consulta o ledger para evitar reprocessamento
-> preserva o original em originais/
-> extrai o texto do PDF
-> detecta se ha uma nota ou varias
-> separa quando houver fronteira confiavel
-> identifica o layout
-> se nao houver layout homologado, marca MODELO NAO SUPORTADO e envia para revisar
-> extrai campos principais
-> detecta cancelamento
-> se cancelada, marca ##CANCELADA## e envia para revisar/canceladas
-> valida o CNPJ do tomador
-> se divergir, marca CNPJ INCORRETO PARA REPOSITORIO e envia para revisar
-> detecta retencao
-> verifica duplicidade fiscal Portal Nacional x ABRASF
-> monta o nome final
-> move para processados/ ou revisar/
-> registra tudo no log e no ledger
```

## 6. Premissas de negocio para a V1

- o codigo fica fora da estrutura de documentos das empresas;
- o processamento acontece diretamente nas pastas da empresa configurada;
- os caminhos de entrada, processados, revisar, originais, logs, canceladas e ledger sao externos e configuraveis;
- a configuracao deve suportar pasta mensal para evitar varredura desnecessaria;
- a V1 deve suportar `watch` e `batch`;
- o cadastro de empresas ja nasce preparado para varias empresas;
- a homologacao e a ativacao em producao devem acontecer empresa por empresa;
- a automacao so decide sozinha quando o layout e homologado e os campos obrigatorios forem encontrados;
- qualquer incerteza deve ir para `revisar/`, nao para `processados/`;
- o arquivo original nunca pode ser perdido ou sobrescrito;
- a primeira versao sera homologada com um conjunto controlado de PDFs reais;
- os primeiros layouts foco do piloto serao os PDFs da pasta `NF MODELO ABRASP E PORTAL NACIONAL/`, embora o nome tecnico correto do padrao seja ABRASF;
- quando o CNPJ do tomador nao corresponder ao CNPJ configurado da empresa, a nota deve receber status `CNPJ INCORRETO PARA REPOSITORIO`;
- quando o layout nao corresponder a um padrao homologado, a nota deve receber status `MODELO NAO SUPORTADO`;
- quando a nota estiver cancelada, nunca deve seguir fluxo normal de processados;
- retencao deve ser identificada na V1 e refletida no nome final;
- se houver duplicidade fiscal entre Portal Nacional e ABRASF, o Portal Nacional tem preferencia e a ABRASF duplicada nao deve gerar copia operacional;
- o sistema nao deve reler indefinidamente o mesmo PDF quando ele ja tiver sido processado.

## 7. Padrao minimo de dados a extrair

Campos obrigatorios para aprovacao automatica:

- numero da NFS-e;
- data de emissao;
- nome do prestador ou razao social;
- CNPJ do prestador quando existir;
- nome do tomador;
- CNPJ/CPF do tomador quando existir no documento;
- valor total do servico;
- valor liquido da NFS-e;
- campos de retencao disponiveis no layout;
- indicador de cancelamento quando existir.

Campos obrigatorios para validacao da empresa:

- CNPJ esperado da empresa configurada;
- CNPJ/CPF extraido do tomador;
- resultado da comparacao entre esperado e encontrado.

Campos desejaveis para log e conferencias futuras:

- municipio;
- competencia;
- codigo de verificacao;
- data de cancelamento;
- justificativa de cancelamento.

## 8. Padrao sugerido de nome de arquivo

Padrao recomendado para a V1, alinhado com o processo manual desejado:

```text
NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valorServico>[_##IR_RETIDO##].pdf
```

Exemplos:

```text
NFSE_7_KELLE_EVANETE_LEMES_DE_ALMEIDA_24.04.2026_256,50.pdf
NFSE_252_INVENTO_MARKETING_DIGITAL_E_TREINAMENTOS_LTDA_04.03.2026_1400,00_##IR_RETIDO##.pdf
NFSE_252_INVENTO_MARKETING_DIGITAL_E_TREINAMENTOS_LTDA_04.03.2026_##CANCELADA##.pdf
```

Padroes de erro no nome:

```text
NFSE_<numero-ou-DESCONHECIDA>_MODELO_NAO_SUPORTADO_<data-ou-sem-data>.pdf
NFSE_<numero-ou-DESCONHECIDA>_CNPJ_INCORRETO_<prestador-ou-desconhecido>_<data-ou-sem-data>.pdf
NFSE_<numero-ou-DESCONHECIDA>_DADOS_AUSENTES_<data-ou-sem-data>.pdf
```

Regras:

- remover apenas caracteres invalidos para nome de arquivo;
- usar `_` como separador para facilitar automacao e evitar ambiguidades em scripts;
- normalizar acentos somente se o sistema de arquivos exigir;
- numero da nota deve preservar o numero real, sem completar com zeros a esquerda;
- limitar o nome operacional final a 150 caracteres para reduzir risco em caminhos Windows profundos;
- se houver colisao, acrescentar sufixo incremental como `_01`, `_02`;
- `##IR_RETIDO##` so aparece quando houver evidencia objetiva de retencao;
- `##CANCELADA##` tem prioridade sobre nome normal, e nota cancelada nao vai para `processados/`.

### 8.1 Prioridade de status

Quando houver mais de uma condicao especial, o sistema deve priorizar assim:

1. `##CANCELADA##`
2. `MODELO NAO SUPORTADO`
3. `CNPJ INCORRETO PARA REPOSITORIO`
4. `DADOS OBRIGATORIOS AUSENTES`
5. nome normal, com ou sem `##IR_RETIDO##`

### 8.2 Regra de retencao

A V1 deve marcar `##IR_RETIDO##` quando houver evidencia objetiva de retencao:

- campo `Retencao do ISSQN`, `ISSQN Retido`, `Vl. ISSQN Retido` ou equivalente indicando retencao positiva;
- algum campo de retencao federal positivo: PIS, COFINS, INSS, IRRF, CSLL ou outras retencoes;
- valor liquido menor que valor do servico, desde que a diferenca nao seja explicada claramente por desconto ou deducao registrada no proprio documento.

Se a regra encontrar valores conflitantes, o arquivo deve ir para `revisar/`. O sistema nao deve marcar retencao por chute.

### 8.3 Layouts homologados na V1

A V1 deve homologar apenas os dois layouts reais do piloto:

#### Portal Nacional / DANFSe v1.0

Assinatura textual minima:

- contem `DANFSe v1.0`;
- contem `Numero da DPS` ou `Numero da DPS`;
- contem `TOMADOR DO SERVICO`.

Campos principais:

- numero da nota: label `Numero da NFS-e`;
- data de emissao: `Data e Hora da emissao da NFS-e`;
- CNPJ do tomador: bloco `TOMADOR DO SERVICO` -> `CNPJ / CPF / NIF`;
- valor do servico: bloco `VALOR TOTAL` -> `Valor do Servico`;
- valor liquido: `Valor Liquido da NFS-e`;
- retencao: `ISSQN Retido` positivo, `Total das Retencoes Federais` positivo ou diferenca valida entre valor do servico e valor liquido.

#### ABRASF municipal / ISSNet

Assinatura textual minima:

- contem `Nota Fiscal de Servico Eletronica` ou `NFS-e Nota Fiscal de Servico Eletronica`;
- contem `Cod. de Autenticidade` ou `Cód. de Autenticidade`;
- contem `Detalhamento dos Tributos`;
- contem `Dados do Tomador de Servicos`.

Campos principais:

- numero da nota: `Numero da Nota Fiscal`;
- data de emissao: `Data de Geracao da NFS-e`;
- CNPJ do tomador: bloco `Dados do Tomador de Servicos` -> `CNPJ/CPF`;
- valor do servico: `Vl. Total dos Servicos`;
- valor liquido: `Vl. Liquido da Nota Fiscal`;
- retencao: `ISSQN Retido = Sim`, `Vl. ISSQN Retido > 0` ou qualquer retencao federal positiva.

#### Modelo nao suportado

Se o texto extraido nao casar com nenhum dos dois conjuntos acima, o arquivo deve ser tratado como `MODELO NAO SUPORTADO`.

#### PDF sem texto suficiente

Se o PDF nao tiver texto selecionavel suficiente para classificacao segura:

- nao tentar OCR na V1;
- enviar para `revisar/`;
- registrar motivo claro no log.

### 8.4 Nota cancelada

Sinais de cancelamento a observar:

- `NOTA CANCELADA`;
- `SEM VALOR LEGAL`;
- `Data de cancelamento`;
- `Justificativa de Cancelamento`.

Regra:

- se qualquer marcador confiavel de cancelamento for encontrado, a nota recebe `##CANCELADA##`;
- nota cancelada vai para `revisar/canceladas/`, ou para `revisar/` se a subpasta dedicada nao existir;
- nota cancelada nunca deve ser tratada como nota normal de `processados/`.

### 8.5 Arquitetura interna recomendada

Para manter o Java limpo, profissional e facil de evoluir, a implementacao deve ser separada por responsabilidades pequenas:

- `App`: ponto de entrada CLI, recebe modo de execucao, empresa e mes opcional.
- `CompanyRegistryLoader`: carrega e valida o cadastro `empresas.yaml`.
- `CompanySelector`: decide quais empresas estao ativas naquela execucao.
- `MonthlyPathResolver`: resolve mes atual, mes informado, lista de meses ou caminho direto.
- `WatchModeRunner`: registra apenas as pastas ativas no `WatchService`.
- `BatchModeRunner`: faz varredura unica das pastas ativas.
- `InputScanner`: encontra PDFs candidatos sem percorrer diretorios desnecessarios.
- `StableFileGuard`: evita processar arquivo ainda em copia, usando estabilidade de tamanho e tentativa de abertura segura.
- `ProcessingLedger`: persiste `companyId`, `sourcePath`, `size`, `lastModified`, `sha256`, `statusFinal`, `destinoFinal` e `processedAt`.
- `OriginalArchiveService`: preserva o PDF original antes de qualquer alteracao.
- `PdfTextExtractor`: extrai texto por pagina usando PDFBox.
- `InvoiceSplitter`: separa PDFs multipagina quando houver uma nota por pagina ou fronteira segura.
- `LayoutDetector`: classifica Portal Nacional, ABRASF ou nao suportado.
- `CancellationDetector`: detecta cancelamento antes do roteamento final.
- `InvoiceParser`: interface comum para parsers.
- `PortalNacionalParser` e `AbrasfParser`: parsers especificos por layout.
- `CompanyValidator`: compara CNPJ/CPF do tomador com o CNPJ esperado da configuracao.
- `RetentionDetector`: decide `##IR_RETIDO##` por campos explicitos e comparacao segura de valores.
- `FileNameBuilder`: monta nome final e nomes de erro.
- `DestinationService`: move para destino correto, tratando colisao de nomes.
- `ProcessingLogger`: gera log tecnico e relatorio simples por execucao.

Regra de projeto: parser nao move arquivo, mover arquivo nao interpreta nota, detector de layout nao extrai todos os campos fiscais, ledger nao decide regra de negocio. Essa separacao reduz acoplamento e facilita testes.

## 9. Ordem de implementacao da V1

Esta ordem substitui a ideia de fases soltas. Cada etapa deve terminar com teste automatizado, commit pequeno e um criterio claro de aceite. A proxima etapa so deve comecar depois da anterior estar verde.

### Etapa 0 - Preparacao do repositorio e lote piloto

**Objetivo:** deixar o projeto pronto para evolucao controlada e separar os PDFs que vao provar o comportamento da V1.

**Implementar/organizar**

- manter o codigo em repositorio Git separado das pastas operacionais das empresas;
- manter os PDFs de exemplo apenas como lote de regressao/homologacao;
- definir a empresa piloto, CNPJ esperado do tomador e estrutura de pastas de teste;
- registrar, para cada PDF do lote, o resultado esperado.

**Criterios de aceite**

- o repositorio compila com Java 17 e Maven;
- o lote piloto tem pelo menos: Portal Nacional valido, ABRASF/ISSNet valido, PDF agrupado, cancelada, CNPJ do tomador errado, sem texto e modelo nao suportado;
- cada arquivo do lote tem status esperado documentado;
- nenhuma pasta operacional real da empresa e usada como pasta do codigo.

**Testes obrigatorios**

- `mvn test`;
- conferencia manual do lote piloto;
- `pdfinfo` nos PDFs agrupados para confirmar quantidade de paginas. Na amostra atual, `NotasPdf.pdf` tem 7 paginas/notas.

### Etapa 1 - Nucleo de leitura, layout e parsing

**Objetivo:** conseguir ler PDF textual, identificar layout homologado e extrair campos obrigatorios sem mover arquivos.

**Implementar**

- `PdfTextExtractor`;
- `LayoutDetector`;
- `InvoiceParser`;
- `PortalNacionalParser`;
- `AbrasfIssnetParser`;
- `InvoiceExtractionService`;
- testes com PDFs reais do lote.

**Criterios de aceite**

- Portal Nacional / DANFSe v1.0 e identificado corretamente;
- ABRASF municipal / ISSNet e identificado corretamente;
- PDF textual fora dos layouts homologados vira `MODELO NAO SUPORTADO`;
- PDF sem texto selecionavel suficiente vira revisao por `PDF SEM TEXTO`;
- campos obrigatorios sao extraidos nos PDFs homologados: numero, data, prestador, CNPJ do prestador quando existir, tomador, CNPJ do tomador, valor do servico, valor liquido, cancelamento e retencao.

**Testes obrigatorios**

- teste unitario de `LayoutDetector` para Portal Nacional, ABRASF, nao suportado e sem texto;
- teste integrado de parser usando `NF 9 OK.pdf` ou equivalente Portal Nacional;
- teste integrado de parser usando primeira pagina de `NotasPdf.pdf` ou equivalente ABRASF;
- teste de PDF sem texto usando `NF 55034 OK.pdf`;
- `mvn test`.

### Etapa 2 - Regras fiscais operacionais

**Objetivo:** decidir status da nota com base nos dados extraidos, sem tocar em arquivos ainda.

**Implementar**

- `CompanyValidator`;
- `CancellationDetector` ou regra equivalente isolada;
- `RetentionDetector` ou regra equivalente isolada;
- `ProcessingDecisionService`;
- prioridade de status conforme secao 8.1.

**Criterios de aceite**

- cancelada sempre tem prioridade e nunca segue fluxo normal;
- layout nao suportado vai para revisao;
- CNPJ do tomador divergente vira `CNPJ INCORRETO PARA REPOSITORIO`;
- dados obrigatorios ausentes viram `DADOS OBRIGATORIOS AUSENTES`;
- `##IR_RETIDO##` so aparece com evidencia objetiva;
- valores conflitantes de retencao vao para revisao, sem chute.

**Testes obrigatorios**

- teste de prioridade: cancelada vence qualquer outro status;
- teste de CNPJ com pontuacao diferente, mas mesmos digitos;
- teste de CNPJ divergente;
- teste com retencao positiva explicita;
- teste sem retencao;
- teste de valor liquido menor que valor do servico;
- teste de valores conflitantes indo para revisao;
- `mvn test`.

### Etapa 3 - Nomeacao segura

**Objetivo:** gerar nomes finais previsiveis, legiveis e seguros para o sistema de arquivos.

**Implementar**

- `FileNameBuilder`;
- sanitizacao de caracteres invalidos;
- limite de 150 caracteres para o nome operacional final;
- sufixo incremental para colisao;
- nomes de erro para `MODELO NAO SUPORTADO`, `CNPJ INCORRETO PARA REPOSITORIO` e `DADOS OBRIGATORIOS AUSENTES`.

**Criterios de aceite**

- nome normal segue `NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valorServico>.pdf`;
- nota retida acrescenta `##IR_RETIDO##`;
- nota cancelada acrescenta `##CANCELADA##`;
- nomes de erro indicam motivo operacional;
- colisao nao sobrescreve arquivo existente;
- nome invalido ou muito longo nao derruba o processamento.

**Testes obrigatorios**

- teste de nome normal;
- teste de nome com `##IR_RETIDO##`;
- teste de nome com `##CANCELADA##`;
- teste de caracteres invalidos;
- teste de truncamento;
- teste de colisao gerando `_01`, `_02`;
- `mvn test`.

### Etapa 4 - Separacao de PDFs agrupados

**Objetivo:** transformar PDFs com varias notas em unidades independentes somente quando a fronteira for confiavel.

**Implementar**

- `InvoiceSplitter`;
- deteccao de uma nota por pagina;
- geracao fisica de um PDF por nota quando a pagina inteira representa uma nota;
- fallback para revisao quando a fronteira nao for confiavel.

**Criterios de aceite**

- PDF com uma unica nota gera um unico resultado;
- PDF agrupado por pagina gera um resultado por pagina/nota;
- nenhuma nota e descartada silenciosamente;
- PDF agrupado com corte ambiguo vai inteiro para `revisar/`;
- PDFs separados abrem e contem apenas a nota esperada.

**Testes obrigatorios**

- teste integrado com `NotasPdf.pdf`, esperando 7 notas na amostra atual;
- teste de PDF de uma pagina;
- teste de PDF multipagina com pagina sem layout suportado;
- teste manual abrindo os PDFs separados gerados em uma pasta temporaria;
- `mvn test`.

### Etapa 5 - Configuracao externa e caminhos mensais

**Objetivo:** permitir que o mesmo binario processe empresas diferentes sem alterar codigo.

**Implementar**

- `CompanyRegistryLoader`;
- `CompanySelector`;
- `MonthlyPathResolver`;
- validacao do `empresas.yaml`;
- exemplo de `empresas.example.yaml`.

**Criterios de aceite**

- cadastro com uma ou varias empresas carrega corretamente;
- empresa desabilitada nao e processada;
- estrategias `atual`, `informado`, `lista` e `direto` resolvem as pastas corretas;
- caminhos de entrada, processados, revisar, originais, logs, canceladas e ledger sao configuraveis;
- configuracao invalida falha com mensagem clara.

**Testes obrigatorios**

- teste unitario de leitura de YAML valido;
- teste de YAML invalido;
- teste de cada estrategia mensal;
- teste de empresa habilitada/desabilitada;
- teste de caminho direto sem subpasta mensal;
- `mvn test`.

### Etapa 6 - Ledger, estabilidade e preservacao do original

**Objetivo:** garantir que o sistema nao perca original, nao processe arquivo incompleto e nao retrabalhe arquivo ja tratado.

**Implementar**

- `StableFileGuard`;
- `ProcessingLedger`;
- calculo de `sha256`;
- `OriginalArchiveService`;
- politica de reprocessamento.

**Criterios de aceite**

- arquivo ainda em copia nao e processado;
- original e copiado para `originais/` antes de qualquer movimentacao;
- ledger registra `companyId`, caminho original, tamanho, data, hash, status, destino e data de processamento;
- reexecutar o lote nao duplica arquivo nem reprocessa item ja registrado;
- falha ao preservar original interrompe aquele arquivo e registra erro.

**Testes obrigatorios**

- teste de arquivo estavel;
- teste de arquivo alterando tamanho;
- teste de hash;
- teste de ledger gravando e lendo;
- teste de reexecucao ignorando item ja processado;
- teste de preservacao do original em pasta temporaria;
- `mvn test`.

### Etapa 7 - Processamento batch completo

**Objetivo:** executar uma passada unica em pastas configuradas, processando PDFs e encerrando.

**Implementar**

- `InputScanner`;
- `BatchModeRunner`;
- `DestinationService`;
- integracao entre configuracao, extracao, decisao, nomeacao, arquivo original, destino, log e ledger;
- CLI `batch`;
- opcao de homologacao para preservar a pasta de entrada, usada quando o lote de PDFs modelo dentro do projeto for a origem do teste.

**Criterios de aceite**

- `batch` varre apenas as pastas ativas;
- pasta vazia encerra sem erro;
- `batch` aceita uma pasta existente configurada como entrada, sem exigir que o sistema crie uma estrutura nova antes;
- PDF valido termina em `processados/`;
- PDF cancelado termina em `revisar/canceladas/`;
- PDF com CNPJ do tomador errado, modelo nao suportado, sem texto ou dados ausentes termina em `revisar/`;
- cada arquivo tem registro de log e ledger;
- nao ha sobrescrita silenciosa;
- em modo de homologacao com preservacao de entrada, os PDFs originais da pasta de entrada continuam no lugar.

**Testes obrigatorios**

- teste integrado em diretorio temporario com lote misto;
- teste manual apontando para a pasta `NF MODELO ABRASP E PORTAL NACIONAL/` em modo de homologacao/preservacao;
- teste de pasta vazia;
- teste de reexecucao do mesmo lote;
- teste de CNPJ divergente;
- teste de cancelada;
- teste de sem texto;
- `mvn test`;
- execucao manual: `java -jar target/renomeador-nfse-*.jar batch --config <arquivo>`.

### Etapa 8 - Processamento watch

**Objetivo:** vigiar apenas as pastas ativas e processar PDF novo quando chegar.

**Implementar**

- `WatchModeRunner`;
- registro de pastas com `WatchService`;
- reuso do mesmo pipeline do `batch`;
- encerramento limpo.

**Criterios de aceite**

- `watch` registra apenas pastas ativas;
- novo PDF copiado para entrada e processado apos estabilidade;
- reiniciar `watch` respeita ledger existente;
- falha de um arquivo nao derruba o processo inteiro;
- logs permitem entender cada evento observado.

**Testes obrigatorios**

- teste automatizado de componente observavel quando viavel;
- teste manual colocando PDF novo na pasta de entrada;
- teste manual copiando arquivo grande gradualmente;
- teste manual reiniciando o processo;
- `mvn test`.

### Etapa 9 - Logs, relatorio e pacote de implantacao

**Objetivo:** entregar uma V1 operavel por usuario sem precisar abrir o codigo.

**Implementar**

- `ProcessingLogger`;
- relatorio simples por execucao;
- empacotamento Maven;
- `README.md` operacional;
- `.bat` opcional para Windows;
- `empresas.example.yaml`.

**Criterios de aceite**

- cada arquivo processado tem status, motivo e destino no log;
- execucao gera resumo com total processado, aprovados, revisar, canceladas e erros;
- artefato `jar` e gerado por Maven;
- comando de `batch` e `watch` esta documentado;
- implantacao nao depende dos PDFs estarem dentro do repositorio.

**Testes obrigatorios**

- `mvn package`;
- execucao do `jar` fora da pasta do projeto apontando para config externa;
- conferencia manual de logs;
- conferencia manual de relatorio;
- teste em Windows antes de liberar `.bat`.

### Etapa 10 - Homologacao controlada

**Objetivo:** liberar uso operacional apenas depois de validar com arquivos reais da empresa piloto.

**Executar**

- rodar primeiro o lote piloto dos PDFs modelo dentro do projeto em modo de homologacao, preservando a pasta de entrada;
- depois rodar lote piloto completo apontando para uma pasta existente fora do projeto, igual a estrutura real da empresa;
- revisar resultado com o responsavel do processo;
- ajustar regras apenas com novo teste de regressao;
- congelar versao V1.

**Criterios de aceite**

- 100% dos PDFs homologados do lote piloto terminam no destino esperado;
- 100% dos casos fora do padrao ou da empresa errada vao para revisao;
- 100% das canceladas vao para `revisar/canceladas/`;
- 0 perda de original;
- 0 sobrescrita indevida;
- 0 reprocessamento indevido;
- usuario do processo aprova os nomes gerados e os motivos de revisao.

**Testes obrigatorios**

- checklist manual da secao 11.3;
- execucao `batch` completa;
- conferencia de que o teste com PDFs modelo dentro do projeto nao moveu nem apagou os arquivos de entrada;
- teste `watch` com inclusao real de arquivo;
- comparacao do ledger e logs com os arquivos de saida;
- assinatura de homologacao antes de producao.

## 10. Criterios gerais de aceite da V1

A V1 sera considerada pronta quando todos os itens abaixo forem verdadeiros:

- o sistema roda a partir de um repositorio externo a estrutura de documentos das empresas;
- o sistema usa cadastro externo de empresas;
- o sistema processa os arquivos diretamente na pasta da empresa configurada;
- o sistema suporta `watch` e `batch`;
- o sistema resolve corretamente a pasta mensal configurada;
- o sistema nao reprocessa indevidamente arquivo ja registrado no ledger;
- o original sempre e preservado;
- o sistema consegue ler PDFs textuais homologados;
- o sistema separa notas agrupadas dos layouts homologados;
- o sistema extrai os campos obrigatorios definidos;
- o sistema valida o tomador contra a empresa configurada;
- o sistema marca como `CNPJ INCORRETO PARA REPOSITORIO` os casos de CNPJ divergente;
- o sistema marca como `MODELO NAO SUPORTADO` os layouts nao homologados;
- o sistema marca `##CANCELADA##` quando a nota estiver cancelada;
- o sistema marca `##IR_RETIDO##` quando houver retencao confirmada;
- o sistema gera nome padrao consistente;
- o sistema decide corretamente entre `processados/`, `revisar/` e `revisar/canceladas/`;
- o sistema gera log compreensivel por arquivo;
- os testes do lote piloto passam integralmente;
- o `jar` final roda fora da pasta do projeto com configuracao externa.

## 11. Matriz de validacao e testes posteriores

### 11.1 Testes automatizados por componente

| Componente | O que testar | Tipo |
| --- | --- | --- |
| `CompanyRegistryLoader` | YAML valido, invalido, empresa desabilitada, caminhos faltantes | Unitario |
| `MonthlyPathResolver` | `atual`, `informado`, `lista`, `direto` | Unitario |
| `PdfTextExtractor` | PDF textual, PDF sem texto, erro de arquivo | Integrado |
| `LayoutDetector` | Portal Nacional, ABRASF/ISSNet, nao suportado, sem texto | Unitario |
| `PortalNacionalParser` | campos obrigatorios, retencao, cancelamento | Integrado com PDF real |
| `AbrasfIssnetParser` | campos obrigatorios, retencao, cancelamento | Integrado com PDF real |
| `InvoiceSplitter` | PDF unico, PDF agrupado por pagina, corte ambiguo | Integrado |
| `CompanyValidator` | CNPJ igual com mascara diferente, CNPJ divergente, ausente | Unitario |
| `RetentionDetector` | retencao explicita, sem retencao, valores conflitantes | Unitario |
| `FileNameBuilder` | nome normal, retido, cancelado, erro, colisao, caracteres invalidos | Unitario |
| `ProcessingLedger` | grava, le, ignora reprocessamento, hash muda | Unitario |
| `StableFileGuard` | arquivo estavel, arquivo em copia, arquivo bloqueado | Unitario/Integrado |
| `DestinationService` | processados, revisar, canceladas, colisao | Integrado |
| `BatchModeRunner` | pasta vazia, lote misto, reexecucao | Integrado |
| `WatchModeRunner` | chegada de arquivo, estabilidade, reinicio | Manual/Integrado |

### 11.2 Testes funcionais por tipo de arquivo

Para cada PDF do lote piloto, registrar e validar:

- nome original;
- empresa configurada;
- mes resolvido;
- CNPJ esperado do tomador;
- layout esperado;
- quantidade esperada de notas;
- numero esperado da nota;
- data esperada;
- prestador esperado;
- CNPJ do prestador esperado quando existir;
- tomador esperado;
- CNPJ do tomador esperado;
- valor do servico esperado;
- valor liquido esperado;
- status esperado;
- destino esperado;
- retencao esperada;
- cancelamento esperado;
- nome esperado do arquivo final.

### 11.3 Checklist manual de homologacao

- o programa rodou fora do repositorio de desenvolvimento;
- o PDF original foi preservado na pasta configurada de `originais/`;
- a quantidade de notas geradas esta correta;
- os dados principais batem com o conteudo visual da nota;
- o CNPJ do tomador bate com o CNPJ esperado da empresa nos casos corretos;
- notas na pasta errada foram conferidas pelo CNPJ do tomador, nunca pelo CNPJ do prestador;
- quando o CNPJ do tomador pertence a outro cliente com REST ativo, a nota foi enviada para as subpastas da REST correta;
- quando o CNPJ do tomador pertence a outro cliente sem REST ativo, a nota ficou em `revisar/` com `NF_PASTA_INCORRETA_`;
- layouts nao suportados foram desviados com `MODELO NAO SUPORTADO`;
- PDFs sem texto foram desviados para revisao sem OCR;
- notas canceladas foram desviadas com `##CANCELADA##`;
- notas com retencao receberam `##IR_RETIDO##`;
- notas sem retencao nao receberam `##IR_RETIDO##`;
- o nome final esta no padrao;
- o destino final do arquivo esta correto;
- o ledger registrou o processamento;
- o log explica claramente o resultado;
- reexecutar o lote nao duplicou arquivos.

### 11.4 Regra para evolucao futura

Qualquer nova prefeitura, nova empresa ou nova regra de negocio so entra em producao depois de:

- adicionar PDFs reais daquele novo caso ao lote de regressao;
- cadastrar o novo caminho e o CNPJ esperado da empresa;
- criar regra de identificacao do novo layout;
- criar ou ajustar parser especifico quando necessario;
- validar novamente o fluxo de `processados/`, `revisar/` e `canceladas/`;
- rodar `mvn test` e a homologacao funcional do lote afetado.

## 12. Riscos conhecidos e tratamento

- **PDF sem texto selecionavel suficiente:** nao entra na automacao da V1; vai para `revisar/`.
- **Layout novo nao homologado:** nao deve ser processado automaticamente; vai para `revisar/` com `MODELO NAO SUPORTADO`.
- **CNPJ do tomador ausente ou ilegivel:** nao validar automaticamente; vai para `revisar/`.
- **Nota na pasta da empresa errada:** deve usar somente o CNPJ do tomador para decidir o destino. Se o tomador tiver REST ativo em outro cadastro, processa pelo cadastro correto; se o tomador for conhecido mas sem REST ativo, vai para `revisar/` com `NF_PASTA_INCORRETA_`; se o tomador for desconhecido, vai para `revisar/` como CNPJ incorreto.
- **Nota cancelada:** deve ir para `revisar/canceladas/` com `##CANCELADA##`.
- **Valores de retencao conflitantes:** deve ir para `revisar/`, sem marcar retencao por chute.
- **Arquivo travado ou ainda em copia:** aguardar estabilidade, registrar no log e tentar novamente quando aplicavel.
- **Falha no WatchService ou reinicio do programa:** o `batch` continua sendo rede de seguranca.
- **Permissao insuficiente nas pastas externas:** registrar falha de ambiente e interromper o lote com mensagem clara.
- **Arquivos com varias notas em sequencia irregular:** se a fronteira da nota nao estiver clara, priorizar seguranca e revisar manualmente.
- **Dados faltantes no proprio PDF:** o sistema nao inventa informacao; apenas sinaliza revisao.

## 13. Evolucao apos a V1

Depois da V1 estabilizada, as proximas evolucoes naturais sao:

- ampliar o cadastro ativo para varias empresas em producao;
- processar varias empresas em sequencia em um unico comando de producao;
- transformar o modo `watch` em servico do Windows;
- adicionar OCR para PDFs escaneados;
- exportar para planilhas;
- criar conferencias fiscais mais complexas;
- adicionar painel simples para fila de revisao.

## 14. Definicao de pronto

Este projeto esta pronto para liberar a V1 operacional quando houver:

- todas as etapas da secao 9 implementadas na ordem definida;
- todos os criterios gerais da secao 10 atendidos;
- matriz de testes da secao 11 executada para o lote piloto;
- empresa piloto escolhida e configurada em `empresas.yaml`;
- caminhos reais da empresa piloto validados em ambiente controlado;
- CNPJ esperado do tomador aprovado para validacao;
- estrategia mensal aprovada;
- lote piloto separado, nomeado e com resultados esperados registrados;
- padrao final de nome aprovado pelo responsavel do processo;
- campos obrigatorios aprovados;
- decisao clara do que e sucesso automatico, revisao manual, `CNPJ INCORRETO PARA REPOSITORIO`, `MODELO NAO SUPORTADO` e `##CANCELADA##`;
- `mvn test` e `mvn package` passando;
- rodada `batch` completa aprovada;
- teste `watch` aprovado;
- aceite deste documento como guia da V1.
