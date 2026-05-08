# AUDITORIA DE RISCOS DO RENOMEADOR

Atualizado em 07/05/2026.

Este documento registra somente riscos que merecem melhoria real no modulo
`RENOMEADOR/` para uma operacao seria, profissional e sustentavel.

A auditoria anterior estava correta em varios pontos, mas misturava:

- falhas corrigiveis do software;
- limitacoes assumidas do desenho atual;
- dependencias inevitaveis do ambiente Windows/Excel/rede.

Aqui ficam priorizados os riscos que devem virar melhoria tecnica ou operacional
controlada. O que for premissa inevitavel fica separado no fim e nao entra como
defeito do projeto.

## Parecer resumido

Nao permanecem falhas tecnicas principais abertas na lista desta auditoria.
Os pontos abaixo viraram protecoes implementadas, testes ou rotina operacional
documentada.

Foram retirados da lista critica porque ja receberam correcao:

- recuperacao de `TOMADOR NAO ENCONTRADO` agora respeita o mes de emissao;
- movimentacao sem `ATOMIC_MOVE` seguro agora usa copia verificada antes de apagar a origem;
- falhas, revisoes e PDFs invalidos agora aparecem em `backend/painel-operacional.tsv` e no health do watch como `ATENCAO`.
- YAML com campo desconhecido agora falha com erro claro antes da execucao;
- `backendRoot` agora fixa logs, indices, painel, healthcheck e lock em local tecnico explicito.
- PDFs grandes ou com paginas demais agora caem em revisao antes da extracao textual pesada;
- batch/watch usam executor limitado para timeout, sem criacao livre de threads;
- logs TSV, ledger e indice escapam campos e preservam linhas ruins em `.corrompidas`;
- manutencao tecnica limpa `split-work/`, compacta logs e gera relatorio de `revisar/`;
- release Windows registra dependencias, roda integracao, gera JAR e testa `java -jar --help`;
- caminhos relativos inseguros e `backendRoot` dentro da REST sao recusados;
- suite de regressao exige lote minimo de PDFs reais/extremos.

Eu reduziria a gravidade de alguns pontos:

- "nao usar banco" nao e falha imediata; e uma evolucao para quando houver volume, concorrencia ou necessidade maior de auditoria transacional;
- "scripts e macros podem ser bloqueados" e premissa de ambiente, nao bug do RENOMEADOR, mas pede diagnostico oficial;
- "PDF escaneado nao processa" e limite assumido, nao falha, desde que caia em revisao claramente;
- "watch depende de alerta externo" e normal para processo continuo; o ponto corrigivel e instalar reinicio/monitoramento.

## Riscos corrigidos nesta rodada

### 1. PDF pesado, timeout e concorrencia

Arquivos principais:

- `RENOMEADOR/src/main/java/br/com/nfse/renomeador/pdf/PdfTextExtractor.java`
- `RENOMEADOR/src/main/java/br/com/nfse/renomeador/app/BatchModeRunner.java`
- `RENOMEADOR/src/main/java/br/com/nfse/renomeador/app/WatchFolderProcessor.java`

Correcao aplicada:

- limite de PDF reduzido para 50MB;
- limite de paginas definido em 80;
- PDF acima desses limites vai para revisao antes da extracao textual completa;
- batch e watch usam executor limitado para timeout, evitando crescimento livre de threads;
- timeout continua enviando a nota para revisao e painel `ATENCAO`.

### 2. Logs, ledger e indice tolerantes a linha ruim

Arquivos principais:

- `RENOMEADOR/src/main/java/br/com/nfse/renomeador/pipeline/ProcessingLogger.java`
- `RENOMEADOR/src/main/java/br/com/nfse/renomeador/ledger/ProcessingLedger.java`
- `RENOMEADOR/src/main/java/br/com/nfse/renomeador/ledger/DuplicateInvoiceIndex.java`

Correcao aplicada:

- escapar tab, `\r` e `\n` no TSV;
- ao carregar ledger/indice, mover linhas invalidas para `.corrompidas`;
- continuar lendo linhas validas;
- testar round-trip de campos com tab/quebra de linha.

### 3. Retencao e manutencao tecnica

Correcao aplicada:

- limpar `split-work/` antigo;
- gerar relatorio de `revisar/` pendente;
- compactar/limitar logs antigos conforme politica;
- criar comando de manutencao:

```bash
renomeador-nfse manutencao limpar-tecnicos --backend operacao/backend
```

### 4. Suite de PDFs reais/extremos

Correcao aplicada:

- teste garante suite minima de PDFs em `src/test/resources/nfse-modelos`;
- a suite precisa conter lote multipagina e layout nao suportado;
- regressao de PDF grande e PDF com paginas demais foi adicionada.

### 5. Dependencias e JAR final

Arquivo principal:

- `RENOMEADOR/pom.xml`
- `RENOMEADOR/scripts/windows/verificar-release.bat`

Correcao aplicada:

- registrar `mvn dependency:tree` em release;
- rodar `mvn verify -Pintegration`;
- rodar `mvn package`;
- testar o JAR sombreado final com `java -jar ... --help`;
- manter script Windows versionado.

### 6. Seguranca de caminhos

Correcao aplicada:

- subpastas tecnicas devem ser relativas;
- caminho absoluto ou com `..` que escape da pasta da empresa e recusado;
- `backendRoot` dentro da REST de cliente e recusado.

## Melhorias quando volume crescer

### 6. Avaliar SQLite para auditoria transacional

Nao usar banco nao impede a V1 de operar, mas limita auditoria e recuperacao em
anos de uso.

Migrar para SQLite passa a valer a pena quando houver:

- muitos meses de historico;
- necessidade de consulta rapida por CNPJ, nota, valor e hash;
- falhas frequentes de energia/rede;
- multiplas execucoes concorrentes;
- exigencia de trilha transacional.

Melhoria esperada quando esse ponto virar prioridade:

- tabelas para processamento, duplicidade e eventos;
- transacao para registrar fases do processamento;
- recuperacao de processamento incompleto;
- exportacao simples para auditoria.

### 7. Instalar watch como servico monitorado

Para uso continuo, o processo precisa ser supervisionado fora do Java.

Melhoria esperada:

- Agendador de Tarefas ou servico Windows;
- reinicio automatico;
- alerta quando `watch-status.json` ficar velho;
- soak de 1h antes de homologacao e 8h antes de producao continua.

## Premissas que nao devem virar "falha a corrigir"

Estes pontos devem ser documentados e validados, mas nao sao defeitos do
RENOMEADOR:

- o ambiente final e Windows/Excel;
- macro e VBS dependem da politica de seguranca da empresa;
- a unidade de rede precisa estar disponivel;
- a planilha fiscal e a fonte oficial de clientes e caminhos;
- PDF escaneado/imagem vai para revisao porque a V1 nao tem OCR;
- layout nao homologado vai para revisao;
- a homologacao final precisa acontecer no ambiente real;
- operador precisa conferir primeiro lote antes de producao real.

O projeto deve se proteger contra falhas previsiveis dessas premissas, mas nao
deve tentar eliminar a propria premissa.

## Itens removidos da lista critica

Estes pontos nao devem aparecer como risco principal:

- "recuperacao de pendencias sem mes": corrigida para usar data de emissao e manter pendente quando o mes nao tem REST ativa;
- "movimentacao em rede sem verificacao": corrigida para usar copia verificada antes de apagar origem quando movimento atomico nao e seguro;
- "falta de painel simples": corrigida com `backend/painel-operacional.tsv` e `watch-status.json` com `ATENCAO`;
- "YAML aceita campo desconhecido": corrigido com validacao estrita de campos na raiz, empresa e pastas;
- "backend tecnico calculado automaticamente": corrigido com `backendRoot` explicito e lock no backend tecnico;
- "timeout/concorrencia usam threads sem isolamento forte": corrigido com pre-filtro de PDF suspeito e executor limitado;
- "limites de PDF grande ainda sao altos": corrigido com limite de 50MB e 80 paginas;
- "logs TSV e ledger texto sao frageis": corrigido com escape TSV e isolamento de linhas corrompidas;
- "suite de PDFs reais extremos ainda e pequena": corrigido com teste de cobertura minima e regressao de extremos;
- "retencao/limpeza tecnica incompleta": corrigido com manutencao de logs, `split-work/` e relatorio de `revisar/`;
- "dependencias e JAR final sem rotina": corrigido com `scripts/windows/verificar-release.bat`;
- "regras de seguranca de caminhos implicitas": corrigido com validacao explicita;
- "usar Windows/Excel" como falha: e decisao operacional;
- "macro pode ser bloqueada" como bug: deve virar diagnostico/checklist;
- "sem OCR" como defeito: e limite assumido da V1;
- "sem banco" como urgencia imediata: manter como evolucao por volume;
- "watch precisa alerta externo" como defeito: todo servico continuo precisa;
- "Maven/JDK precisam existir" como falha do sistema: deve ser checado por script.

## Conclusao

O RENOMEADOR esta bem estruturado para V1.3 e os riscos tecnicos desta rodada
foram tratados. Depois disso, banco SQLite e servico Windows monitorado deixam
de ser urgencia e viram evolucoes naturais quando o volume justificar.
