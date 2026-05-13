# Melhoria do VERIFICAR TUDO Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

## Estado revisado em 12/05/2026

Implementado: `VERIFICAR TUDO` consulta o Portal real em modo somente leitura e usa
o mesmo motor de reconciliacao do `LIGAR SISTEMA` em dry-run. Nao existe mais a ideia
de criar `VerificacaoPortalOlheiro` com uma segunda logica paralela: a decisao por
documento fica em `PlanejadorDocumentoDfe`, e o loop compartilhado fica em
`ReconciliadorPortalDestino`.

O painel chama:

1. `IMPORT API PN verificar-tudo --mes --ambiente --nsu --max-lotes`
2. `RENOMEADOR config import-excel` para todos os meses
3. `RENOMEADOR config preflight --config --mes`

`TUDO OK` so aparece quando todos retornam `OK`. `max-lotes` atingido antes de parada
natural vira `ATENCAO`. `ERRO_EXTERNO` usa codigo 4.

**Goal:** transformar o botao `VERIFICAR TUDO` no teste mais fiel do sistema antes de ligar, provando mes, empresas, certificados, Portal, caminhos da planilha, REST, DMS, RENOMEADOR e decisao "vai importar ou nao vai importar".

**Architecture:** criar um novo comando de pre-voo no `IMPORT API PN` que reaproveita os validadores, scanner de destino e cliente ADN existentes, mas roda em modo somente leitura. O painel passa a chamar esse pre-voo e so mostra `TUDO OK` quando as regras criticas passarem. Nada deve publicar XML/PDF, baixar DANFSe, mover arquivo ou usar ledger como autoridade.

**Regra arquitetural critica:** o pre-voo nao pode virar uma segunda logica paralela de importacao. Ele deve usar a mesma logica de reconciliacao em modo seco (`dry-run`), trocando publicadores reais por simuladores que apenas contam e explicam o que aconteceria. Assim evitamos codigo morto e evitamos o erro de o `VERIFICAR TUDO` aprovar uma coisa diferente do que o `LIGAR SISTEMA` executa.

**Tech Stack:** Python/Tkinter (`painel.py`), Java Maven (`IMPORT API PN`), Picocli, Apache POI, cliente ADN existente, testes JUnit, testes Python `pytest`.

---

## Aviso de uso deste documento

Este arquivo agora e historico de evolucao do `VERIFICAR TUDO`, nao a fonte unica do
estado operacional. O estado vivo fica em `SITUACAO_ATUAL.md`.

Nao execute literalmente os chunks antigos abaixo sem antes comparar com o codigo atual.
Varias partes ja foram implementadas com nomes diferentes:

- o dry-run fiel existe em `SimuladorReconciliacaoDryRun`;
- a decisao por documento fica em `PlanejadorDocumentoDfe`;
- a varredura compartilhada fica em `ReconciliadorPortalDestino`;
- o painel ja chama `verificar-tudo` e depois `RENOMEADOR config preflight`.

Reproducao real em 12/05/2026:

```text
java -jar "IMPORT API PN/target/importador-api-pn-0.1.0-SNAPSHOT.jar" verificar-tudo \
  --planilha PLANILHA_FISCAL.xlsm \
  --backend "IMPORT API PN/backend" \
  --ambiente PRODUCAO \
  --nsu 1 \
  --max-lotes 500 \
  --tentativas-portal 5 \
  --retry-portal-ms 2000 \
  --mes 2026-05
```

Resultado observado:

- exit code `4`;
- nivel final `ERRO_EXTERNO`;
- validacoes locais, caminhos e certificados passaram;
- Portal real e dry-run foram executados;
- 28 lotes consultados, 1262 documentos retornados;
- 97 documentos de maio faltantes no destino;
- 8 documentos com DMS faltante e sem rota DMS;
- duas consultas ao ADN retornaram `HTTP 404 application/json`.

Hipoteses pendentes de investigacao antes de mudar codigo:

1. Se `HTTP 404` no `GET /DFe/{NSU}` representar ausencia/fim natural para aquele NSU,
   o reconciliador nao deve classificar isso como `ERRO_EXTERNO`.
2. Se a regra correta de DMS for por CNPJ do tomador, a planilha precisa ganhar as rotas
   DMS desses tomadores ou o pre-voo deve classificar como pendencia operacional clara.
3. Se a regra correta de DMS for por empresa consultada/prestador, `RoteadorDmsPorEmissao`
   e seus testes precisam mudar explicitamente.

## Estado atual do VERIFICAR TUDO

Historico do plano inicial: antes da melhoria, o painel rodava 6 passos:

1. `validar-cadastro`
2. `validar-certificados`
3. `planejar-consulta-adn`
4. `renomeador config import-excel`
5. `renomeador config check`
6. `simular-janelas`

Esse fluxo antigo conferia partes importantes, mas ainda nao provava a logica completa
do sistema. Ele podia dizer `TUDO OK` sem provar:

- se o mes de atuacao e o mes certo;
- se os caminhos da planilha existem e sao gravaveis;
- se o certificado pertence ao mesmo CNPJ da empresa;
- se o Portal aceita o certificado de cada empresa;
- se o Portal retorna notas para aquela empresa;
- se as notas retornadas pertencem ao mes escolhido;
- se REST e DMS seguirao caminhos corretos;
- se o sistema tentaria importar algo ao ligar;
- se, quando tudo ja estiver completo, ele nao tentaria importar de novo;
- se ha processos antigos/locks/entrada REST suja confundindo o teste.
- se o JAR do IMPORT API PN e o JAR do RENOMEADOR existem e sao executaveis;
- se as subpastas operacionais REST (`XML/processados`, `PDF/processados`, `RETIDO`,
  `canceladas`) podem ser criadas/usadas;
- se o pre-voo esta usando a mesma logica do reconciliar real, em modo seco.

## Regra de produto

`VERIFICAR TUDO` deve responder, em linguagem humana:

```text
Posso ligar o sistema?
Qual mes sera trabalhado?
Quais empresas serao trabalhadas?
O Portal respondeu para cada empresa?
Os certificados pertencem ao CNPJ correto?
Os caminhos da planilha existem e aceitam escrita?
O sistema vai tentar importar algo ao ligar?
Se sim, o que falta?
Se nao, esta tudo completo?
```

## Nivel de confianca esperado

O objetivo e dar a maior certeza pratica possivel antes de ligar. Nao existe certeza
absoluta de 100%, porque alguns fatores podem mudar depois do teste:

- Portal Nacional pode cair depois do `VERIFICAR TUDO`;
- rede/internet pode oscilar;
- permissao de pasta pode mudar depois da verificacao;
- alguem pode apagar/mover pasta enquanto o sistema roda;
- API DANFSe de PDF pode falhar mesmo apos o Portal ADN responder OK.

Entao a promessa correta do botao deve ser:

```text
No momento da verificacao, tudo que esta sob nosso controle foi conferido.
Se o painel disser TUDO OK, o sistema tem condicao real de funcionar.
Se algo externo falhar depois, o painel deve mostrar o erro na rodada/loop.
```

## Resultado na tela do painel

O painel nao pode mostrar so texto bruto tecnico. Ele precisa mostrar erro corrigivel:

```text
PODE LIGAR? NAO

Problema:
CAMINHO REST da POWER nao existe.

Onde corrigir:
PLANILHA_FISCAL.xlsm -> aba CADASTRO MAIO -> linha 123 -> CAMINHO REST

Depois de corrigir:
Clique VERIFICAR TUDO novamente.
```

Niveis de resultado:

| Nivel | Significado | Pode ligar? |
|---|---|---|
| OK | Tudo passou. Se ligar agora, o sistema deve funcionar. | Sim |
| ATENCAO | Algo nao bloqueia, mas precisa ser visto. Ex: entrada REST tem fila pendente. | Sim, com aviso |
| BLOQUEADO | Algo impede funcionamento correto. Ex: caminho nao existe, certificado errado, lock ocupado. | Nao |
| ERRO_EXTERNO | Portal/rede/certificado rejeitado na consulta real. | Nao |

## O que reaproveitar

Nao criar outro fluxo paralelo quando ja existe codigo pronto.

| Necessidade nova | Reaproveitar |
|---|---|
| Ler empresas do mes | `LeitorPlanilhaFiscal.ler(planilha, mes)` |
| Ler todos os meses para DMS | `LeitorPlanilhaFiscal.lerTodasAbas(planilha)` |
| Validar CNPJ/caminhos basicos | `ValidadorCadastro` |
| Abrir certificado | `ResolvedorSenhaCertificado`, `ValidadorCertificado`, `ContextoSslCertificado` |
| Planejar URL do Portal | `PlanejadorConsultaAdn`, `ClienteAdn` |
| Consultar Portal real | `ExecutorConsultaAdn` + `ClienteAdn.consultarDfePorNsu` |
| Extrair XML/lote do Portal | classes existentes em `portal/`, principalmente `ExtratorLoteDfeJson` e `DocumentoDfeExtraido` |
| Ver destino real | `ChavesPresentesNoDestino.escanearEstado` |
| Entender REST completo | `EstadoDestinoNotas` |
| Entender DMS por emissao | `RoteadorDmsPorEmissao` |
| Simular importacao sem gravar | mesma orquestracao do `reconciliar`, com publicadores dry-run |
| Publicar REST/DMS | **nao usar no pre-voo**, apenas conferir caminhos |
| Ledger | **nao usar para decisao**, apenas pode ser citado como auditoria |
| RENOMEADOR config | `config import-excel` e `config check` existentes |

## O que nao fazer

- Nao chamar `reconciliar` real dentro do `VERIFICAR TUDO`, porque ele publica arquivos.
- Nao baixar DANFSe no pre-voo.
- Nao gravar XML/PDF/DMS real.
- Nao mover arquivo.
- Nao decidir por ledger.
- Nao criar segundo scanner de destino se `ChavesPresentesNoDestino` ja atende.
- Nao criar segundo leitor de planilha.
- Nao criar uma segunda regra de "o que falta importar". A regra deve ser compartilhada
  com o reconciliar real, apenas com saida dry-run.
- Nao criar codigo morto para comandos antigos; se algo ficar substituido, marcar como legado ou remover em tarefa propria com teste.

## Resultado esperado no painel

Exemplo quando pode ligar e vai importar algo:

```text
RESULTADO FINAL

Mes de atuacao: 2026-05
Empresas verificadas: 2
Portal real testado: SIM
Certificados batem com CNPJ: SIM
Caminhos REST/DMS gravaveis: SIM
Entrada REST limpa: SIM
Notas faltantes encontradas: 3

Pode ligar? SIM
Ao ligar, o sistema vai tentar importar 3 nota(s).
```

Exemplo quando pode ligar, mas nao ha nada para importar:

```text
RESULTADO FINAL

Mes de atuacao: 2026-05
Empresas verificadas: 2
Notas faltantes encontradas: 0

Pode ligar? SIM
Tudo ja esta completo. Ao ligar, o sistema nao deve importar nada.
```

Exemplo quando nao pode ligar:

```text
RESULTADO FINAL

Pode ligar? NAO
Motivo: certificado da POWER nao pertence ao CNPJ da planilha.
Onde corrigir: PLANILHA_FISCAL.xlsm -> POWER -> certificado/API PN
Acao: corrigir e clicar VERIFICAR TUDO novamente.
```

---

## Divisao em partes de implementacao

Este plano deve ser implementado por fases. Cada fase precisa compilar, passar testes e
deixar o sistema utilizavel. Nao juntar tudo em uma mudanca gigante.

| Fase | Entrega | Valida o que? | Performance esperada |
|---|---|---|---|
| 1 | Pre-voo local rapido | JARs, mes, planilha, caminhos, escrita, entrada REST, RENOMEADOR config | Sem Portal: deve rodar em segundos |
| 2 | Certificado forte | Certificado abre, vence no futuro, CNPJ do certificado bate com CNPJ da planilha | Sem Portal: poucos segundos por empresa |
| 3 | Portal olheiro | Portal aceita certificado e retorna resposta real por empresa | Rede limitada: 1 empresa por vez, poucos lotes |
| 4 | Dry-run fiel do reconciliar | Mesma logica do `LIGAR SISTEMA`, mas sem gravar nada | Usa `max-lotes` configurado, com saida resumida |
| 5 | Integracao no painel | Tela mostra OK/ATENCAO/BLOQUEADO e acao de correcao | Log claro sem travar a UI |
| 6 | Limpeza controlada | Remove/aposenta fluxo morto criado pela evolucao | Sem apagar comando util sem teste e decisao |

## Estado implementado em 12/05/2026

### Fase 1 concluida

Arquivos principais:

- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudo.java`
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/VerificacaoCaminhos.java`
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/AppImportadorPn.java`
- `painel.py`

Entregue:

- comando `verificar-tudo`;
- validacao do mes de atuacao;
- validacao da planilha/cadastro;
- validacao de caminhos REST, DMS, entrada REST e backend;
- teste de escrita com arquivos/diretorios temporarios;
- contagem de arquivos soltos na entrada REST;
- verificacao de subpastas REST sem criar as pastas finais;
- painel destacando `OK`, `ATENCAO`, `BLOQUEADO` e `PODE LIGAR? NAO`.

### Fase 2 concluida

Arquivos principais:

- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/certificado/ExtratorCnpjCertificado.java`
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/certificado/ValidadorCertificado.java`
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/VerificacaoCertificadosPrevoo.java`

Entregue:

- extracao de CNPJ pelo OID ICP-Brasil `2.16.76.1.3.3`;
- bloqueio quando o certificado nao confirma CNPJ;
- bloqueio quando o CNPJ do certificado diverge da planilha;
- cache em memoria por rodada para nao abrir o mesmo certificado repetidamente;
- limpeza do `char[]` de senha apos uso;
- comando legado `validar-certificados` endurecido para conferir CNPJ;
- mensagens mais claras com CNPJ esperado e CNPJs encontrados.

### Validacoes executadas

```bash
python3 -m pytest test_painel.py -q
python3 -m py_compile painel.py test_painel.py
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" test
mvn -Dmaven.repo.local=/tmp/m2-nfse package -DskipTests
```

Observacao operacional:

- No ambiente sandbox do Codex, caminhos externos em `/home/u/Documentos/...` podem
  aparecer como sem permissao de escrita. Esse bloqueio e do sandbox, nao prova falha
  no painel real.
- Em teste real do `verificar-tudo`, o POWER confirmou CNPJ do certificado; a DGA foi
  bloqueada porque o CNPJ nao foi confirmado dentro do certificado.

## Fase 1: primeira implementacao recomendada

Status: implementada e revisada em 12/05/2026.

Comecar pela Fase 1 porque ela da muita seguranca com maxima performance e sem depender
do Portal. Essa fase deve ser quase instantanea comparada ao reconciliar real.

### O que entra na Fase 1

1. Criar o comando `verificar-tudo` no `IMPORT API PN`.
2. Validar que os JARs existem antes de rodar o painel:
   - `IMPORT API PN/target/importador-api-pn-0.1.0-SNAPSHOT.jar`
   - `RENOMEADOR/target/renomeador-nfse-0.1.0-SNAPSHOT.jar`
3. Validar o mes escolhido:
   - existe aba mensal correspondente;
   - existem empresas ativas naquele mes.
4. Validar planilha com `ValidadorCadastro`.
5. Validar caminhos reais da planilha:
   - `CAMINHO REST` existe;
   - `CAMINHO DMS` existe;
   - entrada REST tecnica existe;
   - todos aceitam escrita.
6. Validar subpastas REST:
   - se ja existem, ok;
   - se nao existem, verificar se podem ser criadas pelo sistema;
   - nao criar as subpastas finais durante o `VERIFICAR TUDO`.
7. Validar entrada REST:
   - existe;
   - e gravavel;
   - mostrar quantos arquivos soltos existem.
8. Validar backend do IMPORT API PN:
   - existe;
   - e gravavel.
9. Atualizar e validar `empresas.yaml` do RENOMEADOR usando comandos ja existentes.
10. Mostrar resultado humano no painel com:
   - `OK`;
   - `ATENCAO`;
   - `BLOQUEADO`;
   - onde corrigir.

### O que NAO entra na Fase 1

- Nao consulta Portal.
- Nao abre conexao de rede.
- Nao baixa PDF.
- Nao publica XML.
- Nao move arquivo.
- Nao varre NSU.
- Nao mexe em ledger.

### Performance da Fase 1

Meta:

```text
Rodar em poucos segundos para dezenas/centenas de empresas,
porque e so leitura de planilha + filesystem + validacao local.
```

Regras de performance:

- Ler a planilha uma vez por mes de atuacao.
- Ler todas as abas somente se for necessario para validar RENOMEADOR/DMS.
- Nao escanear XML/PDF profundo ainda.
- Para testar escrita, criar um unico arquivo temporario por pasta e apagar.
- Para subpastas REST faltantes, criar apenas diretorio temporario de prova e apagar,
  sem criar `XML/processados`, `PDF/processados` ou equivalentes no `VERIFICAR TUDO`.
- Nao listar arvores enormes de destino nessa fase.
- Nao chamar Portal.

### Validacao da Fase 1

Automatica:

```bash
python3 -m pytest test_painel.py -q
python3 -m py_compile painel.py test_painel.py
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" -Dtest='*Prevoo*,*VerificacaoCaminhos*,AppImportadorPnCliTest' test
```

Manual:

```text
1. Apontar uma empresa para CAMINHO REST inexistente.
2. Clicar VERIFICAR TUDO.
3. Confirmar BLOQUEADO na tela.
4. Corrigir caminho na planilha.
5. Clicar VERIFICAR TUDO novamente.
6. Confirmar que o erro sumiu.
```

### Criterio de pronto da Fase 1

Fase 1 so termina quando:

- painel nao diz `TUDO OK` se algum caminho da planilha nao existe;
- painel nao diz `TUDO OK` se alguma pasta nao aceita escrita;
- painel mostra onde corrigir na planilha;
- painel permite verificar novamente depois da correcao;
- nenhum arquivo fiscal real foi criado/movido;
- testes automatizados passam.

## Fases seguintes

### Fase 2: certificado forte

Status: implementada em 12/05/2026.

Adicionar validacao de CNPJ dentro do certificado. So depois disso o painel pode dizer
que o certificado pertence realmente a empresa da planilha.

Performance:

```text
Abrir cada certificado uma vez.
Nao chamar Portal ainda.
Cache em memoria apenas dentro da rodada do VERIFICAR TUDO.
```

Contrato implementado:

- CNPJ do certificado e extraido preferencialmente do `otherName` ICP-Brasil
  `2.16.76.1.3.3`.
- Certificado sem CNPJ confirmado bloqueia o `VERIFICAR TUDO`.
- Certificado com CNPJ diferente do CNPJ da planilha bloqueia o `VERIFICAR TUDO`.
- Senha ausente, arquivo invalido ou certificado vencido continuam bloqueando.
- O comando legado `validar-certificados` tambem so mostra OK quando o CNPJ confere.

### Fase 3: Portal olheiro

Status: substituida pelo dry-run fiel.

A ideia de um olheiro separado nao deve virar segunda logica paralela. O pre-voo atual
entra no Portal pelo mesmo caminho do reconciliador e resume a simulacao real.

Performance:

```text
Limitar lotes do olheiro.
Uma empresa por vez para log claro.
Timeout curto e mensagem humana se Portal demorar.
```

### Fase 4: dry-run fiel do reconciliar

Status: implementada em 12/05/2026 com pendencias de classificacao.

Reaproveitar o mesmo motor do `reconciliar`, trocando publicadores reais por dry-run.
Esta e a fase que prova se vai importar ou se ja esta completo.

Performance:

```text
Usar os mesmos parametros do LIGAR SISTEMA.
Nao gravar nada.
Nao baixar PDF.
Nao alterar ledger.
Emitir resumo por empresa, nao despejar XML/log gigante.
```

Pendencias atuais:

- decidir semantica de `HTTP 404` no ADN;
- decidir regra de DMS sem rota para tomador ausente na planilha.

### Fase 5: painel

Status: implementada em 12/05/2026.

Trocar o `VERIFICAR TUDO` atual pelo pre-voo novo, mostrando resultado por nivel e erro
corrigivel.

Performance:

```text
Rodar em thread como hoje.
Nao travar a UI.
Mostrar progresso por empresa.
Evitar log repetido de milhares de linhas.
```

### Fase 6: limpeza

Depois que o novo `VERIFICAR TUDO` estiver validado:

- revisar comandos legados `capturar-adn`, `varrer-nsus`, `publicar-rest-simulado`;
- decidir se ficam documentados como ferramenta tecnica ou se serao removidos;
- remover codigo duplicado criado no caminho;
- atualizar docs e desenhos;
- rodar suite completa.

Nada deve ser apagado so por parecer antigo. Apagar apenas quando:

```text
1. nao e chamado pelo painel;
2. nao e usado por teste;
3. nao e caminho de emergencia/documentacao;
4. existe substituto testado;
5. a remocao passa na suite Maven.
```

---

## Arquivos previstos

### Criar

- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudo.java`
  - orquestra as verificacoes somente leitura.
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/ResultadoPrevoo.java`
  - resumo final para CLI/painel.
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/ResultadoEmpresaPrevoo.java`
  - resultado por empresa.
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/ProblemaPrevoo.java`
  - erro/aviso com nivel, empresa, caminho, onde corrigir e mensagem humana.
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/VerificacaoCaminhos.java`
  - confere existencia e escrita segura dos caminhos da planilha.
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/VerificacaoPortalOlheiro.java`
  - consulta Portal em modo somente leitura e coleta ate N notas por empresa.
- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PublicadoresDryRun.java`
  - substitui publicadores reais no pre-voo e registra o que seria publicado.
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudoTest.java`
  - testes do pre-voo sem rede real.
- `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/VerificacaoCaminhosTest.java`
  - testes de caminhos existentes/gravaveis.

### Modificar

- `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/AppImportadorPn.java`
  - adicionar comando CLI `verificar-tudo`.
  - manter comandos atuais, mas o painel deve preferir o novo comando.
- `painel.py`
  - trocar a sequencia manual 1/6 por uma chamada principal a `verificar-tudo`.
  - manter `renomeador config import-excel` e `config check` se o pre-voo ainda nao cobrir o RENOMEADOR.
- `test_painel.py`
  - testar que o painel passa `--mes` escolhido para `verificar-tudo`.
  - testar que painel exibe `BLOQUEADO`/`ATENCAO` sem esconder erro.
- `SITUACAO_ATUAL.md`
  - atualizar papel do `VERIFICAR TUDO`.
- `docs/operacao/MAPA_LOGICO_SISTEMA.md`
  - atualizar desenho depois que a implementacao estiver pronta.
- `docs/operacao/mapa-verificar-tudo.svg`
  - atualizar desenho visual.

---

## Chunk 1: Contrato do comando `verificar-tudo`

### Task 1: adicionar comando CLI sem comportamento ainda

**Files:**
- Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/AppImportadorPn.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/AppImportadorPnCliTest.java`

- [ ] **Step 1: escrever teste falhando**

Adicionar teste que exige o subcomando:

```java
@Test
void registraComandoVerificarTudo() {
    CommandLine commandLine = new CommandLine(new AppImportadorPn());

    assertThat(commandLine.getSubcommands()).containsKey("verificar-tudo");
}
```

- [ ] **Step 2: rodar o teste e ver falhar**

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" -Dtest=AppImportadorPnCliTest test
```

Esperado: falha porque `verificar-tudo` ainda nao existe.

- [ ] **Step 3: implementar comando minimo**

Adicionar em `@Command(subcommands = ...)` o comando `VerificarTudoCommand`.

Opcoes obrigatorias:

```text
--planilha
--backend
--mes
--ambiente
--nsu
--amostras-por-empresa
--max-lotes-olheiro
```

Valores padrao:

```text
--ambiente PRODUCAO
--nsu 1
--amostras-por-empresa 2
--max-lotes-olheiro 10
```

- [ ] **Step 4: rodar teste e ver passar**

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" -Dtest=AppImportadorPnCliTest test
```

- [ ] **Step 5: commit**

```bash
git add "IMPORT API PN/src/main/java/br/com/nfse/importadorpn/AppImportadorPn.java" \
        "IMPORT API PN/src/test/java/br/com/nfse/importadorpn/AppImportadorPnCliTest.java"
git commit -m "feat: registra comando verificar-tudo"
```

---

## Chunk 2: Verificacao de mes, planilha e caminhos

### Task 2: criar verificador de caminhos da planilha

**Files:**
- Create: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/VerificacaoCaminhos.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/VerificacaoCaminhosTest.java`

- [ ] **Step 1: escrever teste falhando para caminho inexistente**

Teste deve criar empresa com `CAMINHO REST` inexistente e esperar `BLOQUEADO`.

- [ ] **Step 2: escrever teste falhando para caminho existente e gravavel**

Teste deve criar temp dirs para REST, DMS e entrada REST e esperar OK.

- [ ] **Step 3: implementar o minimo**

Regra:

```text
CAMINHO REST deve existir e ser diretorio.
CAMINHO DMS deve existir e ser diretorio.
Entrada REST deve existir e ser diretorio.
Cada diretorio deve aceitar escrita.
Teste de escrita deve criar arquivo temporario pequeno e apagar em seguida.
Subpastas REST esperadas devem existir ou poder ser criadas:
- XML/processados
- XML/RETIDO
- XML/canceladas
- PDF/processados
- PDF/RETIDO
- PDF/canceladas
```

- [ ] **Step 4: rodar testes**

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" -Dtest=VerificacaoCaminhosTest test
```

- [ ] **Step 5: reaproveitar `ValidadorCadastro`**

Nao duplicar regra de CNPJ/campos obrigatorios. `VerificacaoCaminhos` deve complementar com escrita, nao substituir `ValidadorCadastro`.

### Task 3: garantir mes de atuacao no pre-voo

**Files:**
- Create/Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudo.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudoTest.java`

- [ ] **Step 1: teste falhando para mes sem empresas**

Quando `LeitorPlanilhaFiscal.ler(planilha, mes)` retornar cadastro sem empresas, resultado deve ser `NAO_PODE_LIGAR`.

- [ ] **Step 2: teste falhando para mes com empresas**

Quando cadastro tem empresas e caminhos OK, resultado deve avancar para proximas verificacoes.

- [ ] **Step 3: implementar resultado por mes**

Saida humana deve incluir:

```text
Mes de atuacao: AAAA-MM
Aba usada: CADASTRO <MES>
Empresas ativas: N
```

Se a aba do mes nao existir, ou se nao houver empresa ativa, o resultado deve ser
`BLOQUEADO`, com orientacao para corrigir a planilha ou escolher outro mes no painel.

---

## Chunk 3: Certificado correto para o CNPJ

### Task 4: validar certificado e vinculo com CNPJ

**Files:**
- Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/certificado/ValidadorCertificado.java`
- Create/Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudo.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/certificado/ValidadorCertificadoTest.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudoTest.java`

- [ ] **Step 1: investigar o certificado**

Verificar se `ValidadorCertificado` hoje extrai Subject/SAN/CNPJ. Se nao extrai, adicionar campo no resultado sem quebrar chamadas existentes.

- [ ] **Step 2: teste falhando para CNPJ divergente**

Criar fixture ou mock de resultado indicando certificado com CNPJ diferente do CNPJ da planilha.

- [ ] **Step 3: implementar comparacao**

Regra:

```text
CNPJ da planilha deve aparecer no certificado.
Se nao conseguir ler CNPJ do certificado, marcar ATENCAO critica.
Se ler e for diferente, NAO PODE LIGAR.
```

- [ ] **Step 4: saida humana**

```text
Empresa: DGA
CNPJ planilha: 25014360000173
CNPJ certificado: 25014360000173
Resultado: OK
```

---

## Chunk 4: Portal como olheiro, sem importar

### Task 5: criar verificador olheiro do Portal

**Files:**
- Create: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/VerificacaoPortalOlheiro.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/VerificacaoPortalOlheiroTest.java`

- [ ] **Step 1: teste falhando para Portal respondendo HTTP 200**

Usar executor/cliente fake ou resposta fixture. Nao usar rede real em teste unitario.

- [ ] **Step 2: teste falhando para Portal sem nota**

Resultado deve ser `ATENCAO`, nao necessariamente bloqueio, porque pode haver mes sem nota.

- [ ] **Step 3: teste falhando para duas notas de amostra**

Quando o lote tiver varias notas, coletar no maximo `--amostras-por-empresa 2`.

- [ ] **Step 4: implementar consulta somente leitura**

Reaproveitar:

```text
ClienteAdn.consultarDfePorNsu
ExecutorConsultaAdn
ContextoSslCertificado
ExtratorLoteDfeJson
```

Regra:

```text
Consultar Portal por empresa, uma empresa por vez.
Ler ate N notas.
Nao publicar nada.
Nao baixar PDF.
Nao chamar PublicadorRestEntrada.
Nao chamar PublicadorDmsDireto.
```

- [ ] **Step 5: saida humana**

```text
Empresa 1/2: DGA
Portal: OK HTTP 200
Amostras lidas: 2
Nota 1: emissao 2026-05, chave ...
Nota 2: emissao 2026-05, chave ...
```

---

## Chunk 5: Simulacao Portal x destino sem publicar

### Task 6: transformar reconciliacao em motor reaproveitavel com dry-run

**Files:**
- Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/AppImportadorPn.java`
- Create/Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PublicadoresDryRun.java`
- Modify/Create: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudo.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudoTest.java`

- [ ] **Step 0: refatorar sem mudar comportamento**

Extrair a logica de `reconciliarCadastro(...)` para um servico reaproveitavel, mantendo
o comando `reconciliar` real com o mesmo comportamento atual.

Objetivo:

```text
reconciliar real = mesmo motor + publicadores reais
verificar-tudo = mesmo motor + publicadores dry-run
```

- [ ] **Step 1: teste falhando para destino completo**

Cenario:

```text
Portal tem nota X.
ChavesPresentesNoDestino diz que nota X esta completa.
Resultado: faltantes=0, nao tentaria importar.
```

- [ ] **Step 2: teste falhando para destino incompleto**

Cenario:

```text
Portal tem nota X.
Destino nao tem X completa.
Resultado: faltantes=1, tentaria importar.
```

- [ ] **Step 3: implementar usando `ChavesPresentesNoDestino.escanearEstado`**

Nao duplicar scanner de destino.

- [ ] **Step 4: garantir que dry-run nao escreve nada**

Teste deve provar:

```text
Nao criou arquivo na entrada REST.
Nao criou arquivo no CAMINHO DMS.
Nao baixou PDF.
Nao alterou ledger.
```

- [ ] **Step 5: separar REST e DMS no resumo**

Mostrar:

```text
XML REST encontrados: N
PDF REST encontrados: N
XML DMS encontrados: N
Chaves completas: N
Faltantes simuladas: N
```

- [ ] **Step 6: explicar loop**

Saida:

```text
Se ligar agora:
- enquanto houver faltantes, o sistema tentara de novo nas proximas rodadas;
- quando tudo estiver completo nas pastas finais, nao deve importar de novo.
```

### Task 6.1: diferenciar amostra visual de simulacao fiel

**Files:**
- Modify: `PrevooVerificarTudo.java`
- Test: `PrevooVerificarTudoTest.java`

- [ ] **Step 1: documentar no resultado**

Amostra de 2 notas serve para mostrar ao operador que o Portal esta retornando notas
da empresa certa. Ela NAO pode ser usada como prova de completude.

- [ ] **Step 2: simulacao fiel usa a varredura configurada**

Para dizer `vai importar 3` ou `nao vai importar nada`, o pre-voo deve rodar a mesma
varredura do reconciliar, respeitando `--nsu` e `--max-lotes` usados pelo painel.

Saida obrigatoria:

```text
Amostra visual: ate 2 notas por empresa.
Simulacao fiel: usou max-lotes=500, igual ao LIGAR SISTEMA.
```

---

## Chunk 6: Entrada REST, locks e processos antigos

### Task 7: verificar entrada REST tecnica

**Files:**
- Modify: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudo.java`
- Test: `IMPORT API PN/src/test/java/br/com/nfse/importadorpn/prevoo/PrevooVerificarTudoTest.java`

- [ ] **Step 1: teste falhando para entrada vazia**

Resultado OK.

- [ ] **Step 2: teste falhando para entrada com arquivos soltos**

Resultado ATENCAO:

```text
Entrada REST contem arquivos pendentes.
Nao bloqueia sempre, mas precisa aparecer.
```

### Task 8: verificar lock do importador

**Files:**
- Reuse: `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/execucao/BloqueioExecucao.java`
- Modify: `PrevooVerificarTudo.java`
- Test: `PrevooVerificarTudoTest.java`

- [ ] **Step 1: teste falhando para lock ocupado**

Se lock ocupado, resultado deve ser `NAO_PODE_LIGAR`.

- [ ] **Step 2: implementar sem matar processo**

Pre-voo so detecta e informa. Nao deve executar `pkill`, nao deve apagar lock sem regra segura.

### Task 8.1: verificar artefatos executaveis

**Files:**
- Modify: `painel.py`
- Test: `test_painel.py`

- [ ] **Step 1: testar JAR ausente**

Se `IMPORT API PN/target/importador-api-pn-0.1.0-SNAPSHOT.jar` ou
`RENOMEADOR/target/renomeador-nfse-0.1.0-SNAPSHOT.jar` nao existir, o painel deve
mostrar `BLOQUEADO` com acao:

```text
Rode o build Maven antes de abrir o painel.
```

- [ ] **Step 2: nao tentar verificar com JAR ausente**

O painel nao deve seguir para o pre-voo se o JAR necessario nao existe.

---

## Chunk 7: Integrar painel

### Task 9: painel chama `verificar-tudo`

**Files:**
- Modify: `painel.py`
- Test: `test_painel.py`

- [ ] **Step 1: teste falhando para comando com mes escolhido**

Testar helper que monta comando:

```text
java -jar IMPORT_API_PN.jar verificar-tudo --planilha ... --backend ... --mes 2026-04 ...
```

- [ ] **Step 2: extrair builder de comando em `painel.py`**

Evitar duplicacao entre `VERIFICAR TUDO`, `TESTAR AGORA` e `LIGAR`.

- [ ] **Step 3: substituir fluxo 1/6**

Novo fluxo:

```text
1. Pre-voo completo do IMPORT API PN
2. Atualizar RENOMEADOR com todos os meses
3. Validar RENOMEADOR
4. Resultado final humano
```

Ou, se o comando Java ja cobrir RENOMEADOR no futuro, reduzir para:

```text
1. verificar-tudo
```

- [ ] **Step 4: resultado do painel**

O painel so mostra `TUDO OK - sistema pronto para ligar` se o comando retornar 0.

Codigos:

```text
0 = pode ligar
2 = nao pode ligar por regra/cadastro/caminho/certificado
3 = lock/processo ocupado
4 = Portal indisponivel ou certificado rejeitado
```

Importante: o `VERIFICAR TUDO` deve passar para o comando Java os mesmos parametros que
o `LIGAR SISTEMA` usara depois:

```text
--mes
--ambiente
--nsu
--max-lotes
```

Se estes parametros forem diferentes, o pre-voo nao e fiel.

### Task 10: painel mostra erro corrigivel

**Files:**
- Modify: `painel.py`
- Test: `test_painel.py`

- [ ] **Step 1: teste falhando para erro bloqueante**

Simular saida do comando:

```text
NIVEL: BLOQUEADO
Problema: CAMINHO REST da POWER nao existe
Onde corrigir: PLANILHA_FISCAL.xlsm -> CADASTRO MAIO -> linha 123
Acao: corrija e clique VERIFICAR TUDO novamente
```

O teste deve provar que o painel:

```text
1. colore como erro;
2. nao mostra TUDO OK;
3. mostra a acao de correcao;
4. reabilita o botao VERIFICAR TUDO para nova tentativa.
```

- [ ] **Step 2: teste falhando para aviso**

Simular:

```text
NIVEL: ATENCAO
Problema: Entrada REST contem 2 arquivos pendentes
```

O painel deve mostrar aviso, mas permitir ligar se nao houver bloqueio.

- [ ] **Step 3: implementar parser simples de niveis**

Nao precisa criar UI complexa agora. Basta reconhecer linhas que comecem com:

```text
NIVEL: OK
NIVEL: ATENCAO
NIVEL: BLOQUEADO
NIVEL: ERRO_EXTERNO
```

e aplicar cor/mensagem correta.

---

## Chunk 8: Documentacao e desenho

### Task 11: atualizar mapa visual

**Files:**
- Modify: `docs/operacao/MAPA_LOGICO_SISTEMA.md`
- Modify: `docs/operacao/mapa-verificar-tudo.svg`
- Modify: `SITUACAO_ATUAL.md`

- [ ] **Step 1: atualizar SVG**

O desenho deve mostrar:

```text
Abrir painel
Escolher mes
VERIFICAR TUDO
Pre-voo completo
Pode ligar? SIM/NAO
```

- [ ] **Step 2: atualizar doc**

Explicar em linguagem simples:

```text
VERIFICAR TUDO agora e o ensaio geral.
Ele olha Portal e pastas, mas nao importa.
```

---

## Checklist final de validacao

Rodar:

```bash
python3 -m pytest test_painel.py -q
python3 -m py_compile painel.py test_painel.py
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" test
mvn -Dmaven.repo.local=/tmp/m2-nfse test
```

Teste manual assistido:

```text
1. Abrir painel.
2. Escolher mes.
3. Clicar VERIFICAR TUDO.
4. Confirmar que ele mostra empresa por empresa.
5. Confirmar que ele testa Portal como olheiro.
6. Confirmar que ele mostra caminhos REST/DMS.
7. Confirmar que ele diz se vai importar algo ao ligar.
8. Sem clicar LIGAR, confirmar que nenhuma nota foi importada.
```

## Criterio de pronto

`VERIFICAR TUDO` so pode dizer `TUDO OK` quando:

- mes selecionado existe na planilha;
- ha empresa ativa no mes;
- cada empresa foi verificada separadamente;
- certificados abrem e batem com CNPJ;
- caminhos da planilha existem e sao gravaveis;
- subpastas REST necessarias existem ou podem ser criadas;
- JAR do importador e JAR do renomeador existem;
- entrada REST tecnica existe;
- Portal foi consultado em modo olheiro;
- ate 2 notas por empresa foram lidas quando existirem;
- notas de amostra foram conferidas contra mes/CNPJ;
- destino real foi escaneado;
- simulacao fiel usa a mesma logica do reconciliar real em modo dry-run;
- simulacao fiel usa os mesmos parametros que o `LIGAR SISTEMA`;
- simulacao diz claramente se vai importar ou se ja esta tudo completo;
- dry-run provou que nao gravou XML/PDF/DMS, nao baixou PDF e nao alterou ledger;
- nenhum lock/processo antigo bloqueia execucao;
- nenhuma decisao usou ledger como fonte de verdade.

Mesmo com tudo isso, o texto final deve evitar promessa falsa de certeza absoluta.
Mensagem correta:

```text
TUDO OK - no momento da verificacao, o sistema esta pronto para ligar.
```

Mensagem proibida:

```text
Garantia absoluta de que nunca dara erro.
```
