# Renomeador NFS-e

Projeto inicial para extrair dados de PDFs textuais de NFS-e, classificar layouts homologados e preparar a decisao de renomeacao/revisao.

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
- jar Maven empacotado com dependencias.
- processamento `batch` com scanner de entrada, destino, ledger e logs;
- CLI operacional com Picocli;
- modo `watch` com `WatchService`;
- modo de homologacao para preservar PDFs de entrada.

Validado nesta maquina em 30/04/2026:

- JDK 17 portatil em `C:/Users/thiago.faria/tools/jdk-17.0.18+8`;
- Maven 3.9.9 portatil em `C:/Users/thiago.faria/tools/apache-maven-3.9.9`;
- `mvn test`: 51 testes, 0 falhas;
- `mvn verify -Pintegration`: sucesso;
- `mvn package`: sucesso e jar gerado em `target/renomeador-nfse-0.1.0-SNAPSHOT.jar`;
- `java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar --help`: sucesso;
- homologacao controlada em pasta temporaria com os PDFs modelo: 16 notas/saidas processadas, 7 OK, 1 cancelada, 8 revisao, 0 erros;
- reexecucao do mesmo lote: 0 processados, 10 ignorados por ledger, 0 erros.

Ainda pendente para liberar operacao:

- homologacao manual em pasta real da empresa;
- `.bat` opcional para Windows apos validar o jar.

## Verificacao

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

Quando o ambiente nao permitir escrita em `~/.m2`, use um repositorio Maven local temporario:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration
mvn -Dmaven.repo.local=/tmp/m2-nfse package
```

## Configuracao

Use `empresas.example.yaml` como base para o arquivo externo de empresas. O codigo nao deve depender de PDFs ou pastas operacionais dentro do repositorio.

Para a homologacao inicial, o `batch` deve permitir apontar para uma pasta ja existente. Quando a origem for a pasta de PDFs modelo do projeto (`NF MODELO ABRASP E PORTAL NACIONAL/`), a execucao deve preservar a entrada e gravar resultados em uma pasta de saida separada, para nao mover nem apagar os PDFs usados como regressao.

## Uso operacional

Ajuda:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar --help
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --help
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar watch --help
```

Execucao `batch`:

```bash
java -jar target/renomeador-nfse-0.1.0-SNAPSHOT.jar batch --config C:/caminho/empresas.yaml
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

Destinos por status:

- `OK`: `processados/`;
- cancelada: `revisar/canceladas/`;
- CNPJ divergente, sem texto, modelo nao suportado, dados ausentes, conflito de retencao ou erro tecnico: `revisar/`.

## Observacao de auditoria do plano

O plano fala em validar `NotasPdf.pdf` gerando 6 notas se a amostra realmente tiver 6. A amostra atual tem 7 paginas/notas, conforme `pdfinfo`, e os testes foram escritos para 7.
