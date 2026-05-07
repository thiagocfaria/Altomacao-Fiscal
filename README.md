# Renomeador NFS-e

Automacao Java para receber PDFs textuais de NFS-e, separar notas agrupadas, validar o CNPJ do tomador, identificar retencao/cancelamento, renomear arquivos e organizar cada nota nas pastas operacionais da empresa.

O sistema foi desenhado para rodar fora do repositorio, apontando para as pastas reais cadastradas na planilha fiscal. Casos seguros vao para `processados/`, `RETIDO/` ou `canceladas/`; casos tecnicos duvidosos ficam no `backend/` do sistema.

## Sumario

- [Estado atual](#estado-atual)
- [Instalacao e dependencias](#instalacao-e-dependencias)
- [Configuracao pela planilha](#configuracao-pela-planilha)
- [Uso operacional](#uso-operacional)
- [Compatibilidade com caminhos grandes](#compatibilidade-com-caminhos-grandes)
- [Validacao tecnica](#validacao-tecnica)

## Estado atual

Implementado:

- projeto Java 17 com Maven;
- extracao de texto por PDFBox;
- detector de layout para:
  - Portal Nacional / DANFSe v1.0;
  - ABRASF municipal / ISSNet;
  - PDF sem texto suficiente;
  - modelo nao suportado;
- parser de campos principais para Portal Nacional e ABRASF/ISSNet;
- deteccao de cancelamento;
- deteccao conservadora de retencao;
- envio para revisao quando houver evidencia conflitante de retencao;
- validacao do CNPJ do tomador contra o CNPJ esperado;
- montagem do nome operacional;
- separacao logica e fisica de PDF agrupado por paginas quando o documento inteiro pertence a layout homologado;
- carga e validacao inicial de `empresas.yaml`;
- selecao de empresas habilitadas;
- resolucao de pastas mensais por estrategia `atual`, `informado`, `lista` e `direto`;
- calculo de SHA-256;
- ledger persistente basico;
- guarda de estabilidade basica para arquivo legivel;
- preservacao do original com tratamento de colisao;
- jar Maven empacotado com dependencias;
- processamento `batch` com scanner de entrada, destino, ledger e logs;
- CLI operacional com Picocli;
- modo `watch` com `WatchService`, varredura inicial e revarredura em overflow;
- recarga do `watch` quando `empresas.yaml` muda ou quando a planilha informada em `--planilha` e salva;
- retry no `watch` quando o PDF ainda esta sendo copiado e nao estabilizou;
- trava de instancia por `empresas.yaml`, impedindo `batch` e `watch` simultaneos no mesmo cadastro;
- modo de homologacao para preservar PDFs de entrada;
- importacao de cadastro por planilha Excel `.xlsx`/`.xlsm`;
- planilha `PLANILHA_FISCAL.xlsm` para preencher `CAMINHO REST`;
- validacao tecnica de `empresas.yaml`;
- bloqueio de CNPJ de tomador duplicado entre empresas de destino ativas;
- roteamento de nota encontrada na pasta REST errada para a pasta REST correta quando o CNPJ do tomador da nota for conhecido;
- preferencia por Portal Nacional quando existir ABRASF duplicada com os mesmos dados fiscais;
- descarte de duplicidade fiscal tambem no mesmo layout quando a chave fiscal completa bate;
- trava para nao remover duplicata operacional quando o indice apontar para arquivo fora da pasta da empresa;
- dados tecnicos de execucao em `backend/`, fora da REST do cliente;
- destino operacional `RETIDO/` para notas com imposto retido;
- destino operacional `TOMADOR NAO ENCONTRADO/` para nota em pasta errada sem caminho REST ativo no Excel;
- recuperacao automatica de `TOMADOR NAO ENCONTRADO/` quando o CNPJ do tomador passa a ter caminho REST ativo;
- descarte da copia pendente quando o mesmo PDF ja foi processado no destino correto;
- erros operacionais da CLI exibidos como `ERRO: ...`, sem stack trace Java para uso normal;
- log operacional com `duracaoMs` por arquivo.

Historico de validacao operacional:

- Ambiente Windows de referencia: JDK 17 portatil em `C:/Users/thiago.faria/tools/jdk-17.0.18+8` e Maven 3.9.9 portatil em `C:/Users/thiago.faria/tools/apache-maven-3.9.9`;
- Validacao local de desenvolvimento em 07/05/2026: `mvn -Dmaven.repo.local=/tmp/m2-nfse test`: 110 testes, 0 falhas;
- Validacao local de integracao em 07/05/2026: `mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration`: 110 testes unitarios + 1 teste de integracao, 0 falhas;
- `mvn package`: sucesso e jar gerado em `target/renomeador-nfse-0.1.0-SNAPSHOT.jar`;
- `java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar --help`: sucesso;
- `PLANILHA_FISCAL.xlsm` disponivel no projeto para operacao/teste;
- homologacao controlada em pasta temporaria com os PDFs modelo: 16 notas/saidas processadas, 7 OK, 1 cancelada, 8 revisao, 0 erros;
- reexecucao do mesmo lote: 0 processados, 10 ignorados por ledger, 0 erros.
- homologacao real inicial em pasta REST errada: 17 paginas/saidas, 13 OK, 3 revisao, 1 cancelada, 0 erros.

Ainda pendente para liberar operacao:

- importar a planilha modelo para o `empresas.yaml` definitivo;
- testar o pacote no Windows/Excel oficial da operacao, incluindo macro de duplo clique e scripts `.bat`;
- definir rotina operacional: `batch --homologacao` de conferencia, `batch` real agendado ou `watch` continuo. Nao rode `batch` e `watch` ao mesmo tempo com o mesmo `empresas.yaml`;
- conferir logs e arquivos gerados com o responsavel fiscal antes de deixar em producao.

## Instalacao e dependencias

### Requisitos da maquina

- Windows 10/11 ou Windows Server com permissao de leitura/escrita nas pastas REST.
- Java 17 instalado ou portatil.
- Maven 3.9+ apenas para compilar/testar o projeto. Para rodar o `.jar` pronto, Maven nao e necessario.
- Acesso de escrita nas subpastas operacionais que o sistema cria ou usa: `processados/`, `RETIDO/`, `canceladas/` e, quando necessario, `TOMADOR NAO ENCONTRADO/`.
- Acesso de escrita na pasta do `empresas.yaml`, onde o sistema cria `backend/` com logs operacionais, ledger e indices tecnicos. O sistema nao duplica PDFs originais no backend.
- PDFs textuais. PDF escaneado/imagem sem texto selecionavel vai para revisao; a V1 nao usa OCR.

### Dependencias Java do projeto

As dependencias ficam declaradas no `pom.xml` e entram no `.jar` final pelo Maven Shade:

| Dependencia | Uso |
|---|---|
| Java 17 | execucao da aplicacao |
| Apache PDFBox | leitura e separacao de PDFs |
| Apache POI | leitura/preparacao de planilhas `.xlsx`/`.xlsm` |
| Jackson YAML | leitura e geracao de `empresas.yaml` |
| Picocli | comandos `batch`, `watch` e `config` |
| SLF4J/Logback | logs tecnicos |
| JUnit 5 e AssertJ | testes automatizados |

### Instalacao recomendada no Windows

1. Coloque o projeto em uma pasta tecnica, por exemplo:

```text
C:\NFSE\renomeador-nfse
```

2. Garanta o Java 17:

```powershell
java -version
```

3. Se precisar compilar na maquina:

```powershell
mvn test
mvn package
```

4. Confirme o `.jar`:

```powershell
java -jar target\renomeador-nfse-0.1.0-SNAPSHOT.jar --help
```

5. Use os scripts de apoio quando estiver no Windows:

```bat
scripts\windows\compilar.bat
scripts\windows\preparar-planilha.bat C:\entrada\PLANILHA_ORIGINAL.xlsm C:\saida\PLANILHA_FISCAL.xlsm
scripts\windows\importar-planilha.bat C:\saida\PLANILHA_FISCAL.xlsm C:\NFSE\empresas.yaml
scripts\windows\validar-config.bat C:\NFSE\empresas.yaml
scripts\windows\rodar-batch-homologacao.bat C:\NFSE\empresas.yaml C:\saida\PLANILHA_FISCAL.xlsm
scripts\windows\rodar-watch.bat C:\NFSE\empresas.yaml C:\saida\PLANILHA_FISCAL.xlsm
```

## Validacao tecnica

Instale ou aponte `JAVA_HOME` para JDK 17 e garanta `mvn` no PATH. Nesta maquina, foi usada instalacao portatil:

```powershell
$env:JAVA_HOME="$env:USERPROFILE/tools/jdk-17.0.18+8"
$env:MAVEN_HOME="$env:USERPROFILE/tools/apache-maven-3.9.9"
$env:Path="$env:JAVA_HOME/bin;$env:MAVEN_HOME/bin;$env:Path"
```

Depois rode:

```bash
mvn test
mvn verify -Pintegration
mvn package
```

O profile `integration` tambem roda testes `*IT.java` com PDF real. A homologacao operacional continua sendo a rodada com PDFs e pastas reais da empresa.

Quando o ambiente nao permitir escrita em `~/.m2`, use um repositorio Maven local temporario:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration
mvn -Dmaven.repo.local=/tmp/m2-nfse package
```

## Referencias visuais e artefatos auxiliares

Arquivos usados somente como inspiracao ou conferencia visual ficam em `docs/referencias/planilha/`. Eles nao participam do runtime Java, nao sao fonte de importacao do cadastro e nao substituem a planilha operacional `PLANILHA_FISCAL.xlsm`.

## Configuracao pela planilha

Use `empresas.example.yaml` como base para o arquivo externo de empresas. O codigo nao deve depender de PDFs ou pastas operacionais dentro do repositorio.

Para a homologacao inicial, o `batch` deve permitir apontar para uma pasta ja existente. Quando a origem for a pasta de PDFs modelo do projeto (`NF MODELO ABRASP E PORTAL NACIONAL/`), a execucao deve preservar a entrada e gravar resultados em uma pasta de saida separada, para nao mover nem apagar os PDFs usados como regressao.

A planilha de trabalho do projeto e `PLANILHA_FISCAL.xlsm`. Ela preserva o VBA da planilha original e agora e preparada em tres abas: `DASHBOARD`, com painel contabil inicial; `CADASTRO`, com os dados que alimentam o sistema; e `CONFIG`, com listas, paleta e contadores operacionais que o futuro importador podera atualizar. O `CADASTRO` mantem filtros, cabecalho congelado, `CNPJ` e caminhos como texto, `CAMINHO REST` destacado, `CAMINHO ENTRADAS`, `CAMINHO SAIDAS`, `CAMINHO CERTIFICADO DIGITAL`, `VALIDADE CERTIFICADO DIGITAL`, `SENHA CERTIFICADO DIGITAL` opcional e `SOMENTE ORIGEM` apenas para casos excepcionais de pasta generica com CNPJ invalido.

O campo de senha do certificado fica fora do dashboard. Excel nao e cofre de senha; se esse campo for usado em producao, proteja o arquivo e limite o acesso a pasta da planilha.

Mantenha apenas essa planilha modelo como planilha operacional local do projeto. A planilha bruta/original serve somente como entrada para recriar o modelo quando chegar uma versao nova; planilhas fiscais com dados de clientes nao devem ser publicadas no GitHub.

O sistema importa a aba `CADASTRO` para `empresas.yaml`; em planilhas antigas, continua aceitando a aba `Dashboard Fiscal`. O `batch` e o `watch` rodam sobre esse YAML validado. Se `batch` ou `watch` forem chamados com `--planilha`, eles atualizam o `empresas.yaml` a partir da planilha antes de processar. Se `--planilha` nao for informado, mas existir `PLANILHA_FISCAL.xlsm` na mesma pasta do `empresas.yaml`, essa planilha padrao tambem sera importada automaticamente. No `watch`, salvar a planilha informada, ou a planilha padrao ao lado do config, faz o cadastro ser reimportado e os novos caminhos REST passam a ser observados.

Cabecalhos obrigatorios:

- `empresa`
- `cnpj`
- `caminho`

Na aba `CADASTRO`, ou na planilha legada `Dashboard Fiscal`, o sistema tambem entende o cabecalho da linha 2 e usa:

- `CLIENTE` como nome da empresa;
- `CIDADE` como cidade da empresa;
- `CNPJ` como CNPJ esperado do tomador;
- `CAMINHO REST` como pasta direta monitorada.

O `DASHBOARD` nao e fonte de importacao: ele mostra a tela inicial da operacao contabil, com total de clientes, notas importadas hoje, XMLs importados hoje, certificados em alerta, pendencias de cadastro e os 3 certificados digitais mais proximos do vencimento. Certificado com menos de 10 dias fica em vermelho, ate 15 dias em amarelo e acima disso em verde. Ao atualizar a validade do certificado na linha do cliente e salvar a planilha, o painel recalcula e remove o alerta quando ele nao estiver mais proximo do vencimento.

O campo `CNPJ` da planilha sempre representa o CNPJ esperado do tomador da NFS-e. O CNPJ do prestador tambem aparece dentro da nota, mas ele nao decide qual pasta REST deve receber o PDF.

O campo de caminho pode vir como texto na celula ou como hyperlink de arquivo. Cada caminho REST preenchido e tratado como pasta direta monitorada daquela empresa; o sistema nao renomeia pastas reais, apenas organiza PDFs nas subpastas operacionais `processados/`, `RETIDO/`, `canceladas/` e, quando necessario, `TOMADOR NAO ENCONTRADO/`. Linhas com CNPJ de tomador valido e REST vazio entram no cadastro como clientes conhecidos, mas desabilitados para monitoramento. Quando um novo `CAMINHO REST` for preenchido na planilha, a proxima execucao com a planilha importada passa a processar esse caminho automaticamente, sem alterar codigo.

A coluna `SOMENTE ORIGEM` nao deve ser usada para clientes normais. Para CNPJ valido com `CAMINHO REST` preenchido, o importador ignora qualquer `SIM` nessa coluna e cadastra a linha como destino ativo. Ela existe apenas para pasta generica ou pasta errada de teste com CNPJ invalido; nesse caso a linha sera monitorada como origem, mas nunca sera usada como destino por CNPJ. Se houver CNPJ invalido com `CAMINHO REST` preenchido e `SOMENTE ORIGEM` vazio, a importacao falha para evitar esconder erro de digitacao.

O mesmo CNPJ de tomador nao pode aparecer em duas empresas de destino ativas. Essa validacao evita que uma nota em pasta errada seja roteada para a REST errada quando houver cadastro duplicado na planilha.

### Macro de duplo clique da planilha

A `PLANILHA_FISCAL.xlsm` deve ser aberta no Excel para Windows com macros habilitadas. O arquivo preserva o projeto VBA da planilha original e contem evento `Worksheet_BeforeDoubleClick` com seletor de pasta (`FileDialogFolderPicker`) para preencher o caminho.

Se o duplo clique nao abrir o Explorador de pastas:

- confirme que o arquivo esta como `.xlsm`, nao `.xlsx`;
- clique em `Habilitar Conteudo` quando o Excel pedir;
- se o arquivo veio de download, abra `Propriedades` no Windows e marque `Desbloquear`;
- se estiver em pasta de rede, adicione a pasta como `Local Confiavel` no Excel;
- no Excel, confira se macros nao estao bloqueadas em `Arquivo > Opcoes > Central de Confiabilidade > Configuracoes de Macro`.

Mesmo sem macro, o sistema continua funcionando se o caminho for colado manualmente na coluna `CAMINHO REST`.

Se receber uma nova planilha bruta de fora, gere novamente a copia de trabalho:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config preparar-planilha --entrada C:/caminho/PLANILHA_FISCAL_ORIGINAL.xlsm --saida PLANILHA_FISCAL.xlsm
```

Importar a planilha de trabalho:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config import-excel --planilha PLANILHA_FISCAL.xlsm --saida C:/caminho/empresas.yaml
```

Validar configuracao:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config check --config C:/caminho/empresas.yaml
```

## Uso operacional

Ajuda:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar --help
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --help
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --help
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config --help
```

Execucao `batch`:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config C:/caminho/empresas.yaml
```

Execucao `batch` atualizando a partir da planilha antes da passada:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config C:/caminho/empresas.yaml --planilha C:/caminho/PLANILHA_FISCAL.xlsm
```

Limitar a uma empresa e informar mes:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config C:/caminho/empresas.yaml --empresa empresa_piloto --mes 2026-04
```

Homologacao preservando os PDFs da pasta de entrada:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config C:/caminho/empresas.yaml --homologacao
```

Modo continuo:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --config C:/caminho/empresas.yaml
```

Modo continuo acompanhando tambem alteracoes salvas na planilha:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --config C:/caminho/empresas.yaml --planilha C:/caminho/PLANILHA_FISCAL.xlsm
```

Os comandos `batch` e `watch` usam uma trava por arquivo de configuracao. Se outro processo ja estiver rodando com o mesmo `empresas.yaml`, a segunda execucao falha com mensagem de instancia em uso. Para servidor, escolha uma rotina principal: `watch` continuo ou `batch` pelo Agendador do Windows; nao mantenha os dois concorrendo no mesmo cadastro.

No modo `watch`, o Java fica aberto aguardando evento da pasta. Ele trabalha na varredura inicial, quando entra arquivo novo, quando um PDF termina de copiar/alterar, quando o sistema operacional avisa que precisa revarrer a pasta, e quando o cadastro muda. Com `--planilha`, salvar a planilha faz o sistema reimportar o Excel, registrar novos caminhos REST e reprocessar pendencias de `TOMADOR NAO ENCONTRADO/`.

Destinos por status:

- `OK`: `processados/`;
- `OK` com retencao: `RETIDO/`;
- cancelada: `canceladas/`;
- duplicada ABRASF com Portal Nacional equivalente: nao gera PDF operacional duplicado; Portal Nacional fica como principal;
- duplicada no mesmo layout: nao gera PDF operacional duplicado;
- nota em pasta errada com CNPJ do tomador encontrado e REST preenchido: processada pelo cadastro correto e enviada para as subpastas do caminho REST correto;
- nota em pasta errada sem caminho REST ativo no Excel: fica na pasta atual em `TOMADOR NAO ENCONTRADO/`, com nome contendo tomador, CNPJ e valor;
- nota que ja estava em `TOMADOR NAO ENCONTRADO/`: quando o CNPJ do tomador ganhar caminho REST ativo, vai para a REST correta com nome operacional normal; se a pasta pendente ficar vazia, ela e apagada;
- se essa mesma nota ja existir no destino correto pelo ledger/hash, a copia em `TOMADOR NAO ENCONTRADO/` e apagada para nao manter duplicidade operacional;
- sem texto, modelo nao suportado, dados ausentes, conflito de retencao ou erro tecnico: `backend/empresas/<empresa_id>/revisar/`.

Os arquivos tecnicos ficam no backend ao lado do `empresas.yaml` usado na execucao:

```text
backend/
  empresas/
    <empresa_id>/
      execucao-AAAA-MM.tsv
      execucao-AAAA-MM.tsv.gz
      processados.idx
      duplicadas.idx
      revisar/
      split-work/
```

O mes atual fica em TSV para consulta direta. Meses fechados sao compactados automaticamente em `.gz`. O sistema mantem ate 12 meses de logs operacionais por empresa e tambem aplica limite de 100 MB por empresa, apagando os logs mais antigos quando passar desse teto. `processados.idx` e `duplicadas.idx` nao sao copias de PDF; sao indices pequenos usados para impedir reprocessamento e duplicidade fiscal.

### Duplicidade Portal Nacional x ABRASF

Se a mesma NFS-e chegar em dois modelos, Portal Nacional e ABRASF/ISSNet, o sistema usa o Portal Nacional como versao principal.

A ABRASF so e descartada automaticamente quando todos estes campos extraidos forem iguais:

- numero da nota;
- CNPJ do prestador;
- nome do prestador;
- CNPJ do tomador;
- data de emissao;
- valor do servico;
- valor liquido.

O horario de emissao nao entra nessa comparacao, porque pode variar entre modelos. Se algum campo fiscal for diferente, os dois PDFs sao mantidos para conferencia. O sistema nao cria copia tecnica do PDF original em `backend/originais`; o descarte remove somente duplicidade operacional em pasta controlada e registra a decisao no log operacional.

## Compatibilidade com caminhos grandes

O codigo usa `java.nio.file.Path`, entao nao existe um limite artificial criado pela aplicacao para o tamanho do caminho. Mesmo assim, no Windows o limite real depende da configuracao do sistema operacional, do compartilhamento de rede e do tamanho final do caminho completo:

```text
pasta REST + subpasta de destino + nome final do PDF
```

Para reduzir risco em pastas profundas, o sistema limita o nome operacional gerado para ate 150 caracteres, preservando as partes mais importantes: numero da nota, data, valor e marcadores como `##IR_RETIDO##` e `##CANCELADA##`.

Recomendacoes para implantacao:

- preferir uma raiz curta, por exemplo `D:\REST\Cliente` ou `C:\NFSE\REST\Cliente`;
- evitar caminhos com muitas subpastas antes da pasta REST;
- manter nomes de compartilhamento de rede curtos;
- habilitar suporte a caminhos longos no Windows quando a empresa usa estruturas muito profundas;
- validar com `config check` e fazer um `batch --homologacao` antes de deixar o `watch` ligado.

Se a empresa usa caminho extremamente grande, o risco principal nao e a regra fiscal do sistema, mas o Windows negar criacao/movimentacao do arquivo por limite do proprio sistema ou da rede. Nesse caso, o caminho deve ser encurtado ou o Windows deve estar configurado para aceitar caminhos longos.
