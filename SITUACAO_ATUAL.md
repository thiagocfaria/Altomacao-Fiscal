# SITUACAO ATUAL

Atualizado em 07/05/2026.

## 1. Onde estamos

**Fase:** V1.3 de limpeza operacional e controle de armazenamento implementada. A pasta REST do cliente fica focada em saidas operacionais, enquanto logs compactaveis, ledger e indices ficam no backend do sistema sem duplicar PDFs originais.

- Repositorio Git inicializado e preparado para publicar no GitHub.
- Projeto Java 17/Maven criado em `src/main/java`.
- Lote piloto disponivel em `NF MODELO ABRASP E PORTAL NACIONAL/`.
- `NotasPdf.pdf` tem 7 paginas/notas na amostra atual.
- MCP `codebase-memory-mcp` indexado para este projeto.
- LSP Java documentado via `.claude/settings.json`.

## 2. O que ja foi implementado

- [x] Extracao de texto PDF com PDFBox.
- [x] Detector de layout: Portal Nacional, ABRASF/ISSNet, sem texto e nao suportado.
- [x] Parsers iniciais de Portal Nacional e ABRASF/ISSNet.
- [x] Separacao logica e fisica de PDF agrupado por pagina segura.
- [x] Validacao de CNPJ do tomador.
- [x] Deteccao de cancelamento.
- [x] Deteccao conservadora de retencao e conflito de retencao.
- [x] Nomeacao no padrao `NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valor>.pdf`.
- [x] Nome operacional limitado a 150 caracteres para reduzir risco em caminhos Windows profundos.
- [x] Configuracao externa `empresas.yaml` com Jackson YAML.
- [x] Resolucao de pasta por estrategia `atual`, `informado`, `lista` e `direto`.
- [x] Ledger persistente basico.
- [x] Hash SHA-256.
- [x] Guarda de estabilidade de arquivo.
- [x] Processamento sem sobrescrever PDF original; o arquivo vai para destino operacional ou revisao, sem copia tecnica duplicada no backend.
- [x] Logback configurado.
- [x] Jar Maven empacotado com dependencias.
- [x] `InputScanner`.
- [x] `DestinationService`.
- [x] `BatchModeRunner`.
- [x] CLI `batch` com Picocli.
- [x] Modo de homologacao/preservacao de entrada.
- [x] Log/relatorio operacional por execucao.
- [x] Reexecucao sem duplicar processamento via ledger.
- [x] `WatchModeRunner`.
- [x] Importacao de planilha Excel `.xlsx`/`.xlsm` para `empresas.yaml`.
- [x] Importacao especifica da aba `CADASTRO`; se `--aba` nao for informado, o sistema usa a aba mensal do mes de execucao ou de `--mes`, como `CADASTRO MAIO`; planilha legada `Dashboard Fiscal` continua compativel com `CLIENTE`, `CNPJ` do tomador e `CAMINHO REST` na linha 2.
- [x] Comando para preparar copia profissional da planilha: `config preparar-planilha`, gerando `DASHBOARD`, `CADASTRO` e `CONFIG`.
- [x] `PLANILHA_FISCAL.xlsm` trazida do deploy/GitHub, preservando o projeto VBA.
- [x] Planilha modelo preparada com visual PROTONS futurista, dashboard contabil inicial, cadastro operacional com cabecalho congelado/filtro, coluna `CIDADE`, `CAMINHO REST`, `CAMINHO DMS`, `CAMINHO ENTRADAS`, `CAMINHO SAIDAS`, `CAMINHO CERTIFICADO DIGITAL`, `VALIDADE CERTIFICADO DIGITAL`, `SENHA CERTIFICADO DIGITAL` opcional, `SOMENTE ORIGEM`, CNPJs invalidos em amarelo e 30 linhas extras para novos clientes.
- [x] Importacao da planilha preparada validada: 88 empresas importadas para YAML temporario.
- [x] `config check` aprovado sobre o YAML temporario importado da planilha preparada.
- [x] Validacao tecnica de `empresas.yaml` via CLI.
- [x] Watcher com varredura inicial, `ENTRY_CREATE`, `ENTRY_MODIFY` e revarredura em `OVERFLOW`.
- [x] Watcher recarrega o cadastro quando o `empresas.yaml` muda e passa a observar novos caminhos ativos.
- [x] Watcher pode receber `--planilha`; quando a planilha e salva, ele reimporta o Excel, recarrega caminhos e processa pendencias.
- [x] Watcher tenta novamente quando o PDF ainda esta sendo copiado e nao estabilizou.
- [x] Ledger reconhece arquivo ja processado por SHA-256 mesmo quando o caminho muda.
- [x] Duplicidade fiscal Portal Nacional x ABRASF detectada por campos fiscais, com preferencia pelo Portal Nacional.
- [x] Duplicidade fiscal no mesmo layout tambem descartada quando a chave fiscal completa bate.
- [x] Remocao de duplicata operacional travada para nao apagar arquivo fora da pasta da empresa.
- [x] Nota encontrada na pasta REST errada pode ser processada pelo cadastro correto e enviada para as subpastas da REST correta pelo CNPJ do tomador.
- [x] Pasta marcada como `SOMENTE ORIGEM` tambem roteia a nota pelo CNPJ do tomador, sem virar destino operacional.
- [x] Nota em pasta REST errada sem caminho ativo no Excel fica em `TOMADOR NAO ENCONTRADO/`, com nome contendo tomador, CNPJ e valor.
- [x] Nota pendente em `TOMADOR NAO ENCONTRADO/` e recuperada quando o tomador ganha caminho REST ativo; a pasta pendente e apagada se ficar vazia.
- [x] Copia pendente em `TOMADOR NAO ENCONTRADO/` e descartada quando o mesmo PDF ja consta processado no destino correto.
- [x] Nota roteada da REST errada para a REST correta agora registra log operacional e ledger no backend do cadastro correto.
- [x] Logs, ledger, indice de duplicidade e `split-work` movidos para `backend/empresas/<empresa_id>/`, sem pasta `originais` duplicando PDFs.
- [x] Logs operacionais mensais com compactacao automatica de meses fechados e retencao limitada a 12 meses/100 MB por empresa.
- [x] Notas retidas agora vao para `RETIDO/`; canceladas vao para `canceladas/`.
- [x] Diagnostico tecnico da planilha modelo confirma pacote `.xlsm` macro-habilitado com `vbaProject.bin`, `Worksheet_BeforeDoubleClick` e `FileDialogFolderPicker`; se o duplo clique nao funcionar no Windows, verificar bloqueio de macros/confianca do Excel.
- [x] `PLANILHA_FISCAL.xlsm` agora usa abas `DASHBOARD`, `CADASTRO ABRIL`, `CADASTRO MAIO`, `CADASTRO JUNHO`, `CADASTRO JULHO`, `CADASTRO AGOSTO`, `CADASTRO SETEMBRO`, `CADASTRO OUTUBRO`, `CADASTRO NOVEMBRO`, `CADASTRO DEZEMBRO` e `CONFIG`; `CADASTRO ABRIL` preserva os dados/caminhos atuais e as abas futuras copiam clientes/dados fixos sem copiar caminhos mensais.
- [x] Corrigido problema visual da planilha moderna: linhas herdadas como ocultas agora sao reexibidas pelo preparador, sem filtro ativo escondendo clientes.
- [x] Dashboard inicial ajustado para operacao contabil: total de clientes, NF hoje, XML hoje, certificados em alerta, pendencias de cadastro e os 3 certificados digitais mais proximos do vencimento.
- [x] Correcao visual do dashboard apos print: titulo com acentos, certificados como "mais proximos de vencer", painel reduzido para `A:L`, alertas em cards e entradas/saidas removidas das pendencias atuais.
- [x] Dashboard visual refeito no estilo painel de sistema: canvas expandido para `A:S`, topo com identidade `NFSE`, cards grandes para clientes/NF/XML/certificados, painel de certificados e saude operacional em cards, preservando `CADASTRO` como fonte real e o projeto VBA do `.xlsm`.
- [x] Dashboard revisado visualmente: selos `CLI`, `NF`, `XML` e `CERT` ampliados, area externa do painel preenchida em cinza escuro ate `AH60`, formulas marcadas para recalculo no Excel e `PLANILHA_FISCAL.xlsm` regenerada preservando VBA.
- [x] Card `CERT. ALERTA` corrigido: fica verde sem vencimentos proximos, amarelo quando houver certificados vencendo em ate 30 dias e vermelho quando houver certificados com menos de 15 dias; o numero exibido prioriza a faixa mais critica.
- [x] Cadastro mensal implementado na planilha: `CAMINHO ENTRADA/SAIDA` substitui as duas colunas antigas separadas, caminhos DMS/REST/ENTRADA-SAIDA ficam em branco nas abas futuras e certificado digital permanece copiado para todos os meses.
- [x] `batch`, `watch` e `config import-excel` usam a aba mensal correta quando recebem `--mes`; sem `--mes`, usam o mes atual da execucao.
- [x] Roteamento automatico por data de emissao: quando `--mes` e omitido e `--planilha` e fornecido, o sistema importa TODAS as abas `CADASTRO MES` de uma vez, gravando o campo `mes` em cada entrada do `empresas.yaml`; ao processar um PDF, a data de emissao extraida do proprio PDF determina qual caminho REST usar — PDF de abril vai para o caminho de abril, PDF de maio vai para o caminho de maio; se o mes correto nao tiver caminho cadastrado, o arquivo vai para `TOMADOR NAO ENCONTRADO/` com nome correto; planilhas sem abas CADASTRO continuam funcionando no modo aba unica (compatibilidade).
- [x] Importador ignora linha mensal marcada como `SOMENTE ORIGEM` quando ainda nao existe caminho do mes preenchido, evitando bloqueio nas abas futuras clonadas.
- [x] `CADASTRO MAIO` da planilha real importado em YAML temporario em 07/05/2026: 124 empresas importadas e `config check` aprovado.
- [x] Log operacional registra `duracaoMs` por arquivo.
- [x] Scripts Windows em `scripts/windows/`, incluindo `compilar.bat` e guarda quando o JAR ainda nao existe.
- [x] Coluna `SOMENTE ORIGEM` usada para diferenciar pasta de entrada generica de CNPJ invalido digitado errado.
- [x] Validacao bloqueia CNPJ de tomador duplicado entre empresas de destino ativas.
- [x] `batch` e `watch` usam trava de instancia por `empresas.yaml`, evitando execucao simultanea no mesmo cadastro.
- [x] CLI exibe erros operacionais como `ERRO: ...`, sem stack trace Java para uso normal.
- [x] JDK 17 portatil instalado em `C:/Users/thiago.faria/tools/jdk-17.0.18+8`.
- [x] Maven 3.9.9 portatil instalado em `C:/Users/thiago.faria/tools/apache-maven-3.9.9`.
- [x] `mvn test` aprovado em 07/05/2026 (segunda vez, pos roteamento por data): 113 testes, 0 falhas.
- [x] `mvn verify -Pintegration` aprovado em 07/05/2026: 113 testes unitarios + 1 teste de integracao, 0 falhas.
- [x] `mvn package` aprovado em 07/05/2026 (segunda vez): 113 testes, 0 falhas.
- [x] Jar validado com `--help`.
- [x] Homologacao controlada com PDFs modelo em pasta temporaria.
- [x] Reexecucao validada: 10 arquivos ignorados por ledger, 0 erros.
- [x] Homologacao real inicial em pasta REST errada: 17 paginas/saidas, 13 OK, 3 revisao, 1 cancelada, 0 erros.

## 3. Ainda falta para fechar a V1

- [ ] Importar `PLANILHA_FISCAL.xlsm` para `empresas.yaml` definitivo e validar caminhos.
- [ ] Executar teste no Windows/Excel oficial da operacao, incluindo macro de duplo clique e scripts `.bat`.
- [ ] Conferir logs e relatorio operacional com o responsavel.
- [ ] Definir rotina final: `batch --homologacao`, `batch` real agendado ou `watch` continuo, sem rodar `batch` e `watch` simultaneamente com o mesmo `empresas.yaml`.

## 4. Proximo passo recomendado

Validar o novo fluxo multi-mes no ambiente operacional:

```text
# Importar TODOS os meses da planilha de uma so vez (novo padrao):
config import-excel --planilha PLANILHA_FISCAL.xlsm --saida empresas.yaml --sobrescrever
config check --config empresas.yaml

# Processar PDFs — o sistema usa a data do PDF para escolher o mes certo automaticamente:
batch --config empresas.yaml --homologacao
# conferir: PDF de abril -> caminho ABRIL, PDF de maio -> caminho MAIO
# PDF sem caminho para o mes -> TOMADOR NAO ENCONTRADO/ com nome correto

# Modo batch automatico via script (renomeador --real importa todos os meses automaticamente):
# renomeador.ps1 agora chama config import-excel sem --mes, importando todos os meses
```

Para atualizar o script `renomeador.ps1` na area de trabalho: a linha `config import-excel --planilha $planilha --saida $yaml --sobrescrever` ja funciona sem `--mes` — importara todos os meses automaticamente.

## 5. Comandos de verificacao

Use o repositorio Maven local em `/tmp` neste ambiente:

```bash
$env:JAVA_HOME="$env:USERPROFILE/tools/jdk-17.0.18+8"
$env:MAVEN_HOME="$env:USERPROFILE/tools/apache-maven-3.9.9"
$env:Path="$env:JAVA_HOME/bin;$env:MAVEN_HOME/bin;$env:Path"
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration
mvn -Dmaven.repo.local=/tmp/m2-nfse package
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config preparar-planilha --entrada C:\caminho\PLANILHA_FISCAL_ORIGINAL.xlsm --saida C:\caminho\PLANILHA_FISCAL.xlsm
# importar todos os meses (novo padrao sem --mes):
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config import-excel --planilha C:\caminho\PLANILHA_FISCAL.xlsm --saida C:\caminho\empresas.yaml --sobrescrever
# importar so um mes especifico:
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config import-excel --planilha C:\caminho\PLANILHA_FISCAL.xlsm --saida C:\caminho\empresas.yaml --sobrescrever --mes 2026-05
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config check --config C:\caminho\empresas.yaml
# processar sem --mes (data do PDF decide o caminho):
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config empresas.yaml --homologacao
# processar com --mes (comportamento antigo):
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config empresas.yaml --homologacao --mes 2026-05
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --config empresas.yaml --planilha PLANILHA_FISCAL.xlsm
```

## 6. Resultado da homologacao controlada

Executada em 30/04/2026 com copia dos PDFs de `NF MODELO ABRASP E PORTAL NACIONAL/` para pasta temporaria:

```text
Processados=16 OK=7 Canceladas=1 Ignorados=0 Erros=0
Entrada preservada: 10 PDFs
Processados/: 7 PDFs
Revisar/: 8 PDFs
Revisar/canceladas/: 1 PDF
Originais/: 10 PDFs
Ledger: gerado
Log operacional: gerado
Reexecucao: Processados=0 OK=0 Canceladas=0 Ignorados=10 Erros=0
```

Esse resultado e historico da homologacao anterior. Na regra atual, `ledger`, log e revisoes tecnicas ficam em `backend/empresas/<empresa_id>/`, mas PDFs originais nao sao duplicados em `backend/originais`; a REST do cliente fica limitada a `processados/`, `RETIDO/`, `canceladas/` e `TOMADOR NAO ENCONTRADO/` quando necessario.
