# SITUACAO ATUAL

Atualizado em 06/05/2026.

## 1. Onde estamos

**Fase:** V1.1 de preparacao operacional implementada e validada em testes. O lote real inicial em pasta REST errada rodou sem erros tecnicos; o proximo passo e validar no Windows/Excel oficial da operacao e fechar o pacote P1.

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
- [x] Preservacao de original com colisao.
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
- [x] Importacao especifica do `Dashboard Fiscal`: `CLIENTE`, `CNPJ` do tomador, `CAMINHO REST` na linha 2.
- [x] Comando para preparar copia profissional da planilha: `config preparar-planilha`.
- [x] `PLANILHA_FISCAL_MODELO.xlsm` gerada, preservando o projeto VBA.
- [x] Planilha modelo preparada com visual PROTONS, cabecalho congelado, filtro, coluna `CIDADE`, coluna REST destacada, CNPJs invalidos em amarelo e 30 linhas extras para novos clientes.
- [x] Importacao da planilha preparada validada: 88 empresas importadas para YAML temporario.
- [x] `config check` aprovado sobre o YAML temporario importado da planilha preparada.
- [x] Validacao tecnica de `empresas.yaml` via CLI.
- [x] Watcher com varredura inicial, `ENTRY_CREATE`, `ENTRY_MODIFY` e revarredura em `OVERFLOW`.
- [x] Watcher tenta novamente quando o PDF ainda esta sendo copiado e nao estabilizou.
- [x] Ledger reconhece arquivo ja processado por SHA-256 mesmo quando o caminho muda.
- [x] Duplicidade fiscal Portal Nacional x ABRASF detectada por campos fiscais, com preferencia pelo Portal Nacional.
- [x] Remocao de duplicata operacional travada para nao apagar arquivo fora da pasta da empresa.
- [x] Nota encontrada na pasta REST errada pode ser processada pelo cadastro correto e enviada para as subpastas da REST correta pelo CNPJ do tomador.
- [x] Nota roteada da REST errada para a REST correta agora registra log operacional tambem no cliente correto e grava ledger do cliente correto.
- [x] Diagnostico tecnico da planilha modelo confirma pacote `.xlsm` macro-habilitado com `vbaProject.bin`, `Worksheet_BeforeDoubleClick` e `FileDialogFolderPicker`; se o duplo clique nao funcionar no Windows, verificar bloqueio de macros/confianca do Excel.
- [x] Log operacional registra `duracaoMs` por arquivo.
- [x] Scripts Windows em `scripts/windows/`, incluindo `compilar.bat` e guarda quando o JAR ainda nao existe.
- [x] Coluna `SOMENTE ORIGEM` usada para diferenciar pasta de entrada generica de CNPJ invalido digitado errado.
- [x] Validacao bloqueia CNPJ de tomador duplicado entre empresas de destino ativas.
- [x] `batch` e `watch` usam trava de instancia por `empresas.yaml`, evitando execucao simultanea no mesmo cadastro.
- [x] CLI exibe erros operacionais como `ERRO: ...`, sem stack trace Java para uso normal.
- [x] JDK 17 portatil instalado em `C:/Users/thiago.faria/tools/jdk-17.0.18+8`.
- [x] Maven 3.9.9 portatil instalado em `C:/Users/thiago.faria/tools/apache-maven-3.9.9`.
- [x] `mvn test` aprovado em 06/05/2026: 97 testes, 0 falhas.
- [x] `mvn verify -Pintegration` aprovado em 06/05/2026: 97 testes unitarios + 1 teste de integracao, 0 falhas.
- [x] `mvn package` aprovado.
- [x] Jar validado com `--help`.
- [x] Homologacao controlada com PDFs modelo em pasta temporaria.
- [x] Reexecucao validada: 10 arquivos ignorados por ledger, 0 erros.
- [x] Homologacao real inicial em pasta REST errada: 17 paginas/saidas, 13 OK, 3 revisao, 1 cancelada, 0 erros.

## 3. Ainda falta para fechar a V1

- [ ] Importar `PLANILHA_FISCAL_MODELO.xlsm` para `empresas.yaml` definitivo e validar caminhos.
- [ ] Executar teste no Windows/Excel oficial da operacao, incluindo macro de duplo clique e scripts `.bat`.
- [ ] Conferir logs e relatorio operacional com o responsavel.
- [ ] Definir rotina final: `batch --homologacao`, `batch` real agendado ou `watch` continuo, sem rodar `batch` e `watch` simultaneamente com o mesmo `empresas.yaml`.

## 4. Proximo passo recomendado

Validar o pacote P1 no Windows/Excel oficial da operacao:

```text
config import-excel -> config check -> batch --homologacao -> conferir logs/relatorio -> escolher batch agendado OU watch continuo -> teste .bat/watch -> liberar P1
```

A homologacao controlada e a homologacao real inicial ja foram feitas fora do repositorio, preservando os PDFs de entrada quando necessario. O proximo teste deve ser no Windows/Excel oficial da operacao.

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
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config preparar-planilha --entrada C:\caminho\PLANILHA_FISCAL_ORIGINAL.xlsm --saida C:\caminho\PLANILHA_FISCAL_MODELO.xlsm
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config import-excel --planilha C:\caminho\PLANILHA_FISCAL_MODELO.xlsm --saida C:\caminho\empresas.yaml --sobrescrever
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar config check --config C:\caminho\empresas.yaml
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config empresas.yaml --homologacao
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --config empresas.yaml
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
