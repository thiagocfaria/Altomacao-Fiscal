# Plano de Implementacao - Renomeador e Separador Automatico de PDFs de NFS-e

> **For agentic workers:** REQUIRED: Use `superpowers:subagent-driven-development` (if subagents available) or `superpowers:executing-plans` to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** automatizar o tratamento inicial de PDFs de NFS-e diretamente na estrutura de pastas ja usada por cada empresa, sem copiar arquivos para dentro do repositorio do codigo, separando notas agrupadas, identificando o layout, validando o CNPJ do tomador, detectando retencao, tratando cancelamentos e renomeando o PDF com um nome operacional claro.

**Architecture:** a aplicacao Java ficara em um repositorio externo e sera implantada separadamente na maquina de uso. A V1 deve suportar os dois modos no mesmo binario: `watch`, para vigiar somente as pastas ativas e processar quando um arquivo novo entrar, e `batch`, para execucao manual ou por `.bat`/Agendador do Windows como rede de seguranca. A configuracao sera externa, em cadastro de empresas com CNPJ esperado do tomador, estrategia de pasta mensal e caminhos de destino; o sistema processa direto na pasta da empresa, preserva o original, evita reprocessamento com ledger persistente e manda excecoes para revisao.

**Tech Stack:** Java 17, Maven, Apache PDFBox, `java.nio.file`, `WatchService`, arquivo externo de configuracao em `.yaml`, JUnit 5, AssertJ, SLF4J/Logback, Picocli para CLI, `.bat` opcional para modo `batch`. A V1 trabalha apenas com PDFs textuais; PDF sem texto selecionavel suficiente vai para revisao, sem OCR.

---

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
17. ele detecta se existe retencao e, quando houver, acrescenta `##RETIDO##`;
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
- deteccao de retencao com `##RETIDO##`;
- ledger persistente para evitar reprocessamento;
- renomeacao padronizada;
- envio de casos duvidosos para revisao;
- log do processamento.

### Nao inclui

- OCR para PDFs escaneados ou imagem;
- interface grafica;
- banco de dados relacional;
- integracao com Excel;
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
- a empresa pode usar caminho direto ou pasta mensal;
- quando a empresa usa pasta mensal, o sistema deve resolver apenas o mes ativo ou a lista configurada;
- a mesma estrutura deve servir para crescer para varias empresas sem refatoracao estrutural.

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
NF <numero> <prestador> <dataDD.MM.AAAA>[ ##RETIDO##].pdf
```

Exemplos:

```text
NF 00007 KELLE EVANETE LEMES DE ALMEIDA 24.04.2026.pdf
NF 000252 INVENTO MARKETING DIGITAL E TREINAMENTOS LTDA 04.03.2026 ##RETIDO##.pdf
NF 000252 INVENTO MARKETING DIGITAL E TREINAMENTOS LTDA 04.03.2026 ##CANCELADA##.pdf
```

Padroes de erro no nome:

```text
NF <numero-ou-DESCONHECIDA> MODELO NAO SUPORTADO <data-ou-sem-data>.pdf
NF <numero-ou-DESCONHECIDA> CNPJ INCORRETO PARA REPOSITORIO <prestador-ou-desconhecido> <data-ou-sem-data>.pdf
NF <numero-ou-DESCONHECIDA> DADOS OBRIGATORIOS AUSENTES <data-ou-sem-data>.pdf
```

Regras:

- remover apenas caracteres invalidos para nome de arquivo;
- preservar espacos para manter leitura humana;
- normalizar acentos somente se o sistema de arquivos exigir;
- numero da nota pode ser completado com zeros a esquerda para melhorar ordenacao;
- limitar tamanho do nome quando necessario;
- se houver colisao, acrescentar sufixo incremental como `_01`, `_02`;
- `##RETIDO##` so aparece quando houver evidencia objetiva de retencao;
- `##CANCELADA##` tem prioridade sobre nome normal, e nota cancelada nao vai para `processados/`.

### 8.1 Prioridade de status

Quando houver mais de uma condicao especial, o sistema deve priorizar assim:

1. `##CANCELADA##`
2. `MODELO NAO SUPORTADO`
3. `CNPJ INCORRETO PARA REPOSITORIO`
4. `DADOS OBRIGATORIOS AUSENTES`
5. nome normal, com ou sem `##RETIDO##`

### 8.2 Regra de retencao

A V1 deve marcar `##RETIDO##` quando houver evidencia objetiva de retencao:

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
- `RetentionDetector`: decide `##RETIDO##` por campos explicitos e comparacao segura de valores.
- `FileNameBuilder`: monta nome final e nomes de erro.
- `DestinationService`: move para destino correto, tratando colisao de nomes.
- `ProcessingLogger`: gera log tecnico e relatorio simples por execucao.

Regra de projeto: parser nao move arquivo, mover arquivo nao interpreta nota, detector de layout nao extrai todos os campos fiscais, ledger nao decide regra de negocio. Essa separacao reduz acoplamento e facilita testes.

## 9. Fases de execucao

### Fase 0 - Preparacao do piloto e mapeamento da empresa

**Objetivo**

Fechar as decisoes minimas antes de programar: empresa piloto, caminhos reais, CNPJ esperado, estrategia mensal, layouts alvo, nome padrao, campos obrigatorios e lote piloto de testes.

**Entregas**

- empresa piloto definida;
- caminhos reais da empresa mapeados;
- CNPJ esperado do tomador definido;
- estrategia mensal definida;
- padrao de nome aprovado;
- lista de campos obrigatorios aprovada;
- pacote piloto de PDFs reais separado por tipo de caso.

**Casos que devem existir no piloto**

- PDF com 1 nota valida do Portal Nacional;
- PDF com 1 nota valida do layout ABRASF;
- PDF com varias notas no mesmo arquivo;
- PDF com campo obrigatorio ausente ou ilegivel;
- PDF cujo tomador nao corresponde a empresa configurada;
- PDF com retencao;
- PDF sem retencao;
- PDF cancelado;
- PDF que nao seja NFS-e;
- PDF sem texto utilizavel.

**Criterios de aceite**

- existe uma empresa piloto definida com caminhos reais de teste;
- existe definicao objetiva do que vai para `processados/` e do que vai para `revisar/`;
- existe uma regra objetiva para `CNPJ INCORRETO PARA REPOSITORIO`;
- existe uma regra objetiva para `MODELO NAO SUPORTADO`;
- existe uma regra objetiva para `##CANCELADA##`;
- existe uma regra objetiva para marcar ou nao `##RETIDO##`;
- existe um padrao unico de nome para a V1.

**Como validar**

- conferencia manual da estrutura real de pastas da empresa piloto;
- conferencia manual do lote piloto;
- checklist de regras aprovado pelo responsavel do processo.

### Fase 1 - Implantacao externa, cadastro de empresas e caminhos mensais

**Objetivo**

Garantir que o programa rode fora do repositorio e use apenas configuracao externa.

**Entregas**

- programa compilado para uso externo;
- arquivo `empresas.yaml`;
- configuracao de mes alvo ou caminho mensal;
- caminho para `ledger`;
- comando de execucao em `watch`;
- comando de execucao em `batch`.

**Criterios de aceite**

- o programa roda fora do ambiente de desenvolvimento;
- nenhum PDF operacional precisa ser copiado para dentro do repositorio do codigo;
- os caminhos configurados controlam totalmente onde o sistema le e grava arquivos;
- a pasta mensal correta e resolvida sem percorrer meses antigos;
- adicionar uma nova empresa ao cadastro nao exige alteracao de codigo.

**Como validar**

- instalar o programa em uma pasta separada do projeto;
- apontar a configuracao para a empresa piloto;
- executar e confirmar que toda leitura e gravacao acontece somente na estrutura configurada;
- cadastrar uma segunda empresa ficticia e validar apenas a carga de configuracao.

### Fase 2 - Execucao watch/batch e anti-reprocessamento

**Objetivo**

Executar o processamento por evento ou por lote, evitando retrabalho e sem reler indefinidamente o mesmo PDF.

**Entregas**

- modo `watch` com `WatchService`;
- modo `batch` para passada unica;
- `StableFileGuard`;
- `ProcessingLedger` persistente;
- regra para ignorar arquivo ja processado;
- encerramento limpo quando nao houver novos arquivos.

**Criterios de aceite**

- em `watch`, o sistema so processa quando entrar arquivo novo;
- em `batch`, o sistema percorre apenas uma vez e termina;
- reexecutar o job sem arquivos novos nao duplica processamento;
- reiniciar o programa nao faz ele reprocessar o que ja esta no ledger;
- arquivo travado ou ainda em copia nao derruba o lote; deve ser aguardado, ignorado temporariamente ou reavaliado depois com log.

**Como validar**

- iniciar o modo `watch` e colocar um PDF novo na pasta;
- rodar o modo `batch` com a pasta vazia e conferir encerramento sem acao;
- rodar novamente com o mesmo arquivo ja tratado e confirmar ausencia de retrabalho;
- desligar e religar o programa e confirmar consulta correta ao ledger;
- testar arquivo grande em copia e conferir a espera por estabilidade.

### Fase 3 - Leitura do PDF e identificacao do layout

**Objetivo**

Ler o texto do PDF e classificar com seguranca entre Portal Nacional, ABRASF ou nao suportado.

**Entregas**

- extracao de texto de PDFs com conteudo selecionavel;
- classificador com assinaturas textuais fixadas no plano;
- regra para detectar PDF sem texto util;
- encaminhamento de modelo nao suportado.

**Criterios de aceite**

- todos os PDFs textuais do lote piloto retornam texto nao vazio;
- Portal Nacional e ABRASF sao classificados corretamente no lote piloto;
- arquivo sem texto util vai para `revisar/` com motivo claro;
- arquivo textual fora dos layouts homologados recebe `MODELO NAO SUPORTADO`.

**Como validar**

- executar o lote piloto e comparar layout esperado x layout detectado;
- testar um PDF sem texto extraivel e confirmar envio para `revisar/`;
- testar um PDF textual de layout estranho e confirmar `MODELO NAO SUPORTADO`.

### Fase 4 - Deteccao de uma ou varias notas e separacao

**Objetivo**

Descobrir se o PDF contem uma unica NFS-e ou varias e separar cada nota em um arquivo independente.

**Entregas**

- regra para contar blocos de nota;
- mecanismo de separacao por pagina ou fronteira segura;
- fallback para revisao quando a separacao nao for confiavel.

**Criterios de aceite**

- PDF com uma unica nota gera exatamente um arquivo de saida;
- PDF com varias notas gera um arquivo por nota identificada;
- nenhuma nota e descartada silenciosamente;
- quando houver ambiguidade de corte, o arquivo inteiro vai para `revisar/`.

**Como validar**

- usar `NotasPdf.pdf` como referencia principal de arquivo com varias notas;
- conferir que `NotasPdf.pdf` gera 6 arquivos quando a amostra realmente tiver 6 notas, uma por pagina;
- comparar quantidade de notas esperadas com quantidade de arquivos gerados;
- abrir os PDFs separados e conferir se cada um contem apenas uma nota completa.

### Fase 5 - Extracao de campos, cancelamento, validacao da empresa e retencao

**Objetivo**

Extrair os dados obrigatorios, validar se a nota pertence a empresa correta, detectar cancelamento, detectar retencao e gerar o nome padrao.

**Entregas**

- parser de campos obrigatorios;
- validacao do tomador contra o CNPJ esperado da configuracao;
- detector de cancelamento;
- detector de retencao;
- montagem do nome padrao;
- montagem de nomes de erro para `MODELO NAO SUPORTADO`, `CNPJ INCORRETO PARA REPOSITORIO` e `DADOS OBRIGATORIOS AUSENTES`.

**Criterios de aceite**

- para layouts homologados, todos os campos obrigatorios sao extraidos corretamente no lote piloto;
- notas cujo tomador pertence a empresa configurada seguem no fluxo normal;
- notas cujo tomador nao pertence a empresa configurada sao classificadas como `CNPJ INCORRETO PARA REPOSITORIO`;
- notas com layout desconhecido sao classificadas como `MODELO NAO SUPORTADO`;
- notas canceladas recebem `##CANCELADA##` e nao entram em `processados/`;
- notas com retencao recebem `##RETIDO##`;
- notas sem retencao nao recebem `##RETIDO##`;
- nomes invalidos, muito longos ou duplicados sao tratados sem falha.

**Como validar**

- comparar manualmente os campos extraidos com o PDF original;
- comparar o CNPJ do tomador extraido com o CNPJ esperado da empresa piloto;
- comparar manualmente cancelamento esperado x cancelamento detectado;
- comparar manualmente retencao esperada x retencao detectada;
- testar um PDF propositalmente colocado na pasta errada;
- verificar se o nome do arquivo bate com os dados visiveis da nota.

### Fase 6 - Destino final, revisao manual, canceladas e log

**Objetivo**

Tomar a decisao final de destino do arquivo e garantir rastreabilidade completa.

**Entregas**

- regra de aprovacao automatica;
- regra de envio para `revisar/`;
- regra de envio para `revisar/canceladas/`;
- motivos especificos por status;
- gravacao de log por arquivo;
- gravacao no ledger por arquivo processado.

**Criterios de aceite**

- arquivos completos, confiaveis e da empresa correta terminam em `processados/`;
- arquivos incompletos, ambiguos, cancelados, fora do layout ou da empresa errada terminam no destino de revisao correto;
- cada arquivo processado possui log com status e motivo;
- cada arquivo processado fica registrado no ledger;
- nao existem falhas silenciosas.

**Como validar**

- rodar o lote piloto completo e conferir destino final de cada caso;
- provocar um caso de CNPJ divergente e confirmar envio para `revisar/`;
- provocar um caso de layout desconhecido e confirmar `MODELO NAO SUPORTADO`;
- provocar um caso de cancelamento e confirmar envio para `revisar/canceladas/`;
- conferir se o log e o ledger permitem entender o que aconteceu sem abrir o codigo.

### Fase 7 - Homologacao controlada e entrada em operacao

**Objetivo**

Liberar a V1 apenas depois de validar o comportamento com arquivos reais na estrutura da empresa piloto.

**Entregas**

- checklist de homologacao executado;
- lote piloto processado do inicio ao fim;
- teste de operacao em `watch`;
- teste de operacao em `batch`;
- ajuste fino de regras antes do uso operacional.

**Criterios de aceite**

- 100% dos arquivos homologados do lote piloto sao tratados corretamente;
- 100% dos casos fora do padrao ou da empresa errada sao enviados para revisao;
- 100% das canceladas sao desviadas do fluxo normal;
- 0 perda de original;
- 0 sobrescrita indevida de arquivos processados;
- 0 reprocessamento indevido de arquivo ja registrado no ledger.

**Como validar**

- executar rodada controlada com os PDFs reais separados por categoria;
- testar em caminho real da empresa piloto;
- revisar o resultado com usuario do processo;
- assinar checklist final de liberacao da V1.

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
- o sistema marca `##RETIDO##` quando houver retencao confirmada;
- o sistema gera nome padrao consistente;
- o sistema decide corretamente entre `processados/`, `revisar/` e `revisar/canceladas/`;
- o sistema gera log compreensivel por arquivo;
- os testes do lote piloto passam integralmente.

## 11. Estrategia de validacao posterior

### 11.1 Validacao tecnica

- testes unitarios para configuracao, resolucao de pasta mensal, nomeacao, deteccao de layout, extracao de campos, validacao de CNPJ do tomador, deteccao de retencao e deteccao de cancelamento;
- testes unitarios para prioridade de status;
- testes unitarios para ledger e anti-reprocessamento;
- testes integrados com PDFs reais do lote piloto;
- testes de regressao sempre que um novo layout for adicionado;
- teste de permissao de leitura e escrita nos caminhos externos;
- teste do modo `watch` com inclusao real de arquivo;
- teste do modo `batch` sem novos arquivos.

### 11.2 Validacao funcional

Para cada arquivo do lote piloto, registrar:

- nome original;
- empresa configurada;
- mes resolvido;
- CNPJ esperado do tomador;
- tipo esperado do arquivo;
- quantidade esperada de notas;
- campos obrigatorios esperados;
- status esperado;
- destino esperado;
- retencao esperada;
- nome esperado do arquivo final.

### 11.3 Checklist manual de homologacao

- o programa rodou fora do repositorio de desenvolvimento;
- o PDF original foi preservado na pasta configurada de `originais/`;
- a quantidade de notas geradas esta correta;
- os dados principais batem com o conteudo visual da nota;
- o CNPJ do tomador bate com o CNPJ esperado da empresa nos casos corretos;
- notas na pasta errada foram desviadas com `CNPJ INCORRETO PARA REPOSITORIO`;
- layouts nao suportados foram desviados com `MODELO NAO SUPORTADO`;
- notas canceladas foram desviadas com `##CANCELADA##`;
- notas com retencao receberam `##RETIDO##`;
- o nome final esta no padrao;
- o destino final do arquivo esta correto;
- o ledger registrou o processamento;
- o log explica claramente o resultado.

### 11.4 Regra para evolucao futura

Qualquer nova prefeitura, nova empresa ou nova regra de negocio so entra em producao depois de:

- adicionar PDFs reais daquele novo caso ao lote de regressao;
- cadastrar o novo caminho e o CNPJ esperado da empresa;
- criar regra de identificacao do novo layout;
- validar novamente o fluxo de `processados/`, `revisar/` e `canceladas/`.

## 12. Riscos conhecidos e tratamento

- **PDF sem texto selecionavel suficiente:** nao entra na automacao da V1; vai para `revisar/`.
- **Layout novo nao homologado:** nao deve ser processado automaticamente; vai para `revisar/` com `MODELO NAO SUPORTADO`.
- **CNPJ do tomador ausente ou ilegivel:** nao validar automaticamente; vai para `revisar/`.
- **Nota na pasta da empresa errada:** deve ir para `revisar/` com `CNPJ INCORRETO PARA REPOSITORIO`.
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

Este projeto esta pronto para iniciar implementacao quando houver:

- empresa piloto escolhida;
- caminhos reais da empresa piloto definidos;
- CNPJ esperado do tomador aprovado para validacao;
- estrategia mensal aprovada;
- lote piloto separado e nomeado;
- padrao final de nome aprovado;
- campos obrigatorios aprovados;
- decisao clara do que e sucesso automatico, revisao manual, `CNPJ INCORRETO PARA REPOSITORIO`, `MODELO NAO SUPORTADO` e `##CANCELADA##`;
- aceite deste documento como guia da V1.
