# Revisao critica do plano de importacao NFS-e Nacional

Pesquisado e revisado em: 08/05/2026  
Modulo: `IMPORT API PN`

## Objetivo desta revisao

Rever se os problemas levantados sao reais, corrigir pontos fracos dos documentos existentes e definir como construir uma importacao robusta de XML e PDF/DANFSe, separada e inteligente, com recuperacao automatica sempre que tecnicamente possivel.

Importante: em integracao com API externa, "sem nenhuma falha" nao significa que o portal nunca ficara fora, nem que toda resposta vira perfeita. Significa que o nosso sistema deve:

- nunca perder nota;
- nunca duplicar arquivo final;
- nunca considerar uma nota concluida sem XML e PDF;
- nunca salvar erro como documento valido;
- nunca misturar nota de uma empresa na pasta errada;
- nunca avancar controle de NSU antes de ter estado recuperavel;
- sempre conseguir retomar apos queda, travamento, internet instavel ou API fora;
- separar claramente falha temporaria, falha de certificado, falha fiscal, falha de PDF e falha operacional.

## Validacao dos problemas pesquisados

### Confirmados por fonte oficial

1. **A API atual de geracao do DANFSe sera descontinuada em 01/07/2026.**
   - Confirmacao: noticia oficial da SE/CGNFS-e publicada em 05/05/2026 informa que a API atual de geracao do DANFSe sera descontinuada a partir de 01/07/2026.
   - Impacto: nosso plano nao pode depender de download de PDF por API como solucao principal.

2. **Existem endpoints oficiais separados para ADN, DANFSe, Parametrizacao e SEFIN Nacional.**
   - Confirmacao: pagina oficial "APIs - Prod. Restrita e Producao" lista endpoints de producao restrita e producao.
   - Impacto: endpoints devem ser configuraveis e validados por healthcheck, nao fixos escondidos no codigo.

3. **O ADN permite consulta de DF-e por NSU e consulta de eventos por chave.**
   - Confirmacao: manual oficial de contribuintes das APIs do ADN.
   - Impacto: XML/DF-e deve ser fonte primaria da importacao.

4. **A distribuicao por API ja teve historico oficial de suspensao/reprocessamento de NSU.**
   - Confirmacao: noticia oficial de 04/05/2023 informou suspensao temporaria e necessidade de reiniciar busca a partir do NSU 1 em determinado contexto.
   - Impacto: nosso ledger nao pode ser apenas "ultimo NSU"; precisa guardar historico, lacunas e capacidade de revarredura controlada.

### Confirmados por relatos fortes de comunidade

1. **DANFSe com 502/503 e arquivo de 1 KB/erro no lugar do PDF.**
   - ACBr documentou multiplos relatos em 05/12/2025 com retorno `E9999` contendo HTML de `503` ou `502`.
   - Impacto: validacao binaria de PDF e obrigatoria.

2. **Robotizacao em massa afetando estabilidade do endpoint DANFSe.**
   - ACBr registrou que justificativa encontrada no forum da NFS-e apontava muitos robos baixando DANFSe em massa.
   - Impacto: nosso sistema deve ser educado: fila, limite, cache, nao repetir download inutil.

3. **Erro 496 SSL Certificate Required e Forbidden em cenarios de certificado/mTLS.**
   - Relatos existem, mas a interpretacao precisa ser cuidadosa.
   - Correcao do nosso documento anterior: `496` nem sempre significa instabilidade do portal. Pode ser certificado nao carregado, cadeia TLS incorreta, biblioteca HTTP errada ou configuracao incompleta.

4. **Comunidades ja estao migrando para geracao local de DANFSe por XML.**
   - ACBr implementou/relatou geracao local em Fortes/Fast Report em marco/abril de 2026.
   - Impacto: geracao local nao e luxo; e a rota correta para continuidade.

## Falhas encontradas no nosso plano anterior

### Falha 1: V1 deixava geracao local de PDF para V1.1

Isso ficou fraco. Como a API DANFSe sera descontinuada em 01/07/2026, a arquitetura deve nascer considerando:

- `XML_IMPORTADO` como sucesso fiscal principal;
- `PDF_GERADO_LOCALMENTE` como meta de producao;
- `PDF_BAIXADO_API` apenas como fallback temporario ate 01/07/2026;
- depois de 01/07/2026, nao depender da API DANFSe.

Decisao revisada: implementar pelo menos o esqueleto do gerador local desde a primeira fase, mesmo que a homologacao visual completa fique em etapa seguinte. O sistema nao deve bloquear importacao de XML por falta de PDF.

### Falha 2: controle por "ultimo NSU" estava simples demais

So guardar `ULTIMO NSU API PN` na planilha e arriscado. Se houver falha, reprocessamento oficial, buraco de NSU ou queda no meio, podemos perder nota.

Decisao revisada:

- manter na planilha apenas um resumo operacional;
- manter o estado real no backend tecnico por empresa/CNPJ;
- guardar `ultimo_nsu_confirmado`, `nsus_pendentes`, `nsus_com_erro`, `janelas_reconciliacao`, `data_ultima_consulta`, `hash_xml`, `chave_acesso`;
- so avancar NSU quando o documento estiver arquivado ou em estado recuperavel.

### Falha 3: faltava reconciliacao periodica

Consultar so "o que veio desde o ultimo NSU" pode falhar se o portal reprocessar, atrasar compartilhamento ou se nosso sistema tiver bug.

Decisao revisada:

- alem das 3 rodadas diarias incrementais, rodar uma reconciliacao leve:
  - diaria: revalidar ultimos 7 dias do mes vigente;
  - semanal: revalidar mes vigente completo;
  - fechamento mensal: revalidar mes anterior antes de encerrar;
- reconciliacao deve ser por chave/NSU e nunca duplicar.

### Falha 4: destino de XML estava indefinido

Para importacao fiscal, XML nao pode ser tratado como temporario. O XML e o documento fiscal eletronico; o PDF e auxiliar.

Decisao revisada:

- XML deve ter destino fiscal oficial proprio dentro do `CAMINHO REST`, em `XML/processados`, `XML/RETIDO`, `XML/canceladas` ou `XML/TOMADOR NAO ENCONTRADO`;
- a REST deixa de ser somente PDF e passa a ser organizada por tipo: `PDF/...` e `XML/...`;
- backend tecnico pode manter cache/ledger, mas nao deve ser o unico lugar do XML fiscal final.

### Falha 5: faltava contrato claro entre IMPORT API PN e RENOMEADOR

Se o IMPORT API PN jogar PDF final direto na REST, ele precisa classificar retencao/cancelamento e nomear igual ao RENOMEADOR. Se nao fizer isso, cria dois padroes.

Decisao revisada:

- IMPORT API PN entrega XML/PDF validados na raiz do `CAMINHO REST`;
- RENOMEADOR organiza PDF e XML juntos;
- RENOMEADOR continua dono das regras de retencao, cancelamento, tomador incorreto, nome final e duplicidade operacional;
- XML organizado segue `CAMINHO REST/XML/...`;
- PDF organizado segue `CAMINHO REST/PDF/...`;
- IMPORT API PN nao deve publicar direto nas subpastas finais `PDF/` ou `XML/`.

Contrato revisado de entrega:

```text
CAMINHO REST/
  <arquivo-validado>.xml
  <arquivo-validado>.pdf
```

O RENOMEADOR le cada arquivo pela propria estrutura fiscal. A relacao XML/PDF deve ficar no ledger do IMPORT API PN por chave/NSU/hash, sem exigir pacote na REST.

### Falha 6: faltava modelo de observabilidade

Sem painel bom, a rotina "automatizada" vira caixa preta.

Decisao revisada: criar quatro saidas operacionais:

- `painel-rodada.tsv`: status por empresa na rodada;
- `painel-notas.tsv`: status por chave/NSU;
- `health.json`: ultima execucao, proxima execucao, API saudavel, certificados vencendo;
- `alertas.tsv`: somente o que precisa de acao humana.

## Arquitetura revisada recomendada

### Principio central

Separar descoberta, download, validacao, arquivamento e publicacao operacional.

```text
PLANILHA/YAML
  -> fila por empresa
  -> certificado validado
  -> descoberta ADN por NSU/CNPJ
  -> XML bruto temporario
  -> validacao fiscal do XML
  -> arquivo XML fiscal final
  -> ledger confirmado
  -> geracao/download de PDF independente
  -> validacao PDF
  -> publicacao para DMS/REST/RENOMEADOR
  -> painel e alertas
```

### Separacao XML x PDF

XML:

- prioridade maxima;
- fonte fiscal;
- nao depende de PDF;
- deve ser salvo mesmo se PDF falhar;
- deve ter deduplicacao por chave de acesso e hash;
- deve entrar em destino fiscal final.

PDF/DANFSe:

- documento obrigatorio para fechamento operacional da empresa;
- pode ser gerado localmente pelo XML;
- download via API e apenas fallback temporario;
- se falhar, fica `PDF_PENDENTE` sem impedir XML, mas a nota nao vira `CONCLUIDA`;
- deve passar por validacao binaria e, quando possivel, leitura da chave.

Regra de fechamento:

```text
XML_ARQUIVADO_FINAL + PDF_ARQUIVADO_FINAL = CONCLUIDO
XML_ARQUIVADO_FINAL + PDF_PENDENTE = PENDENTE_OPERACIONAL
PDF_ARQUIVADO_FINAL sem XML valido = QUARENTENA
```

## Estados revisados por documento

Estados XML:

- `DESCOBERTO_NSU`
- `XML_BAIXANDO`
- `XML_BAIXADO_TEMP`
- `XML_DECODIFICADO`
- `XML_VALIDADO_SCHEMA`
- `XML_VALIDADO_CHAVE`
- `XML_VALIDADO_CNPJ`
- `XML_FORA_DO_MES`
- `XML_ARQUIVADO_FINAL`
- `XML_DUPLICADO_CONFIRMADO`
- `XML_QUARENTENA`

Estados PDF:

- `PDF_NAO_SOLICITADO`
- `PDF_PENDENTE_GERACAO`
- `PDF_GERANDO_LOCAL`
- `PDF_GERADO_TEMP`
- `PDF_BAIXANDO_API_TRANSITORIA`
- `PDF_BAIXADO_TEMP`
- `PDF_VALIDADO_BINARIO`
- `PDF_VALIDADO_FISCAL`
- `PDF_ARQUIVADO_FINAL`
- `PDF_PENDENTE_API_INSTAVEL`
- `PDF_QUARENTENA`

Estado conjunto:

- `PENDENTE_XML`
- `PENDENTE_PDF`
- `PENDENTE_OPERACIONAL`
- `CONCLUIDO`
- `QUARENTENA`

Estados empresa:

- `EMPRESA_OK`
- `SEM_CERTIFICADO`
- `CERTIFICADO_VENCIDO`
- `CERTIFICADO_VENCE_EM_BREVE`
- `CERTIFICADO_CNPJ_INCOMPATIVEL`
- `CAMINHO_REST_INVALIDO`
- `CAMINHO_DMS_INVALIDO`
- `API_INSTAVEL`
- `SEM_MOVIMENTO`
- `PROCESSADA_COM_PENDENCIAS`

## Autocorrecao e recuperacao automatica

### Teste com certificado real de cliente

Risco: usar certificado real em endpoint errado e gerar emissao, cancelamento, evento fiscal ou qualquer obrigacao indevida para o cliente.

Como evitar:

- V1 deve operar em `MODO_SOMENTE_LEITURA`;
- permitir somente endpoints de consulta (`GET`/`HEAD`) aprovados;
- bloquear `POST`, `PUT`, `PATCH` e `DELETE` em producao;
- bloquear `POST /nfse`;
- bloquear registro de eventos;
- bloquear cancelamento;
- rodar `dry-run` antes da primeira chamada real;
- registrar no painel e logs que a execucao esta em modo leitura;
- separar certificado real de testes de producao restrita quando possivel;
- nunca testar emissao/cancelamento com certificado de cliente sem autorizacao formal especifica.

Regra de arquitetura: o cliente HTTP deve ter uma camada `ReadOnlyHttpClient` na V1. Mesmo que alguem configure endpoint de escrita por engano, o codigo recusa a chamada antes de sair para a internet.

### Queda de energia ou processo morto

Como recuperar:

- todos os downloads primeiro vao para `*.part`;
- ao iniciar, procurar `*.part` antigos;
- se houver hash/estado recuperavel, retomar;
- se nao houver, apagar temporario e baixar de novo;
- nunca mexer em arquivo final sem confirmacao de hash.

### Internet/API fora

Como recuperar:

- backoff por empresa;
- circuit breaker por endpoint;
- nao marcar empresa como erro definitivo;
- reagendar na proxima janela;
- preservar fila pendente.

### Fallback entre caminhos oficiais sem virar bagunca

Como recuperar:

- cada empresa tem estado proprio na fila;
- cada nota tem estado proprio por chave/NSU;
- cada endpoint tem circuit breaker proprio;
- falha no ADN de uma empresa nao trava as outras;
- falha de PDF nao bloqueia XML;
- fallback so pode usar caminho de leitura;
- depois de tentar alternativa segura, o item volta para a fila pendente com horario da proxima tentativa;
- a proxima janela retoma pendencias antes de procurar novidades, sem duplicar.

Ordem recomendada:

1. ADN/contribuintes para XML por NSU.
2. Consulta por chave conhecida, quando a chave ja foi descoberta.
3. Geracao local de PDF pelo XML.
4. API DANFSe apenas como fallback temporario enquanto existir.
5. Reagendar pendencia com alerta, sem automacao por navegador na V1.

### Certificado vencido ou senha errada

Como recuperar:

- bloquear somente a empresa afetada;
- nao retentar ate mudar certificado/senha;
- alerta claro no painel;
- demais empresas continuam.

### PDF indisponivel

Como recuperar:

- XML fica salvo e confirmado;
- PDF fica em fila separada;
- primeira tentativa: gerar localmente;
- fallback temporario ate 01/07/2026: API DANFSe;
- se ambos falharem, `PDF_PENDENTE` sem duplicar XML;
- a nota aparece no painel como pendencia operacional ate o PDF existir.

### Computador desligado no horario da agenda

Como recuperar:

- manter estado de agenda em `backend/health/agenda-state.json`;
- ao iniciar, comparar horario atual com janelas planejadas;
- se 05:00 nao rodou e o computador ligou 08:00, executar `ATRASADA_05H`;
- se 05:00 e 12:00 nao rodaram e ligou 13:00, executar as duas em ordem;
- se ficou varios dias desligado, rodar uma recuperacao controlada do mes vigente, nao uma explosao de chamadas;
- registrar no painel que a rodada foi atrasada, nao perdida.

### XML invalido, CNPJ divergente ou chave inesperada

Como recuperar:

- enviar para quarentena;
- nao atualizar NSU como concluido simples;
- registrar como estado recuperavel/manual;
- nunca mover para REST/DMS final.

### Reprocessamento ou reinicio de NSU pelo portal

Como recuperar:

- detectar se NSU menor que confirmado apareceu com chave nova;
- nao apagar ledger antigo;
- abrir modo `RECONCILIACAO`;
- permitir revarredura desde NSU 0/1 em ambiente controlado, deduplicando por chave;
- registrar evento no painel.

### Arquivo final ja existe

Como recuperar:

- se chave e hash batem: marcar duplicado confirmado e nao sobrescrever;
- se chave bate e hash difere: manter ambos em revisao ou versionar com sufixo tecnico ate confirmar evento/substituicao/cancelamento;
- se nome bate e chave diverge: quarentena obrigatoria.

## Validacoes obrigatorias

### Antes de chamar API

- empresa ativa;
- CNPJ normalizado;
- certificado existe;
- certificado abre com senha;
- certificado nao vencido;
- CNPJ raiz compativel;
- caminhos REST/DMS existem ou sao criaveis conforme regra;
- lock global livre;
- endpoint oficial configurado.

### Ao receber XML

- resposta HTTP esperada;
- corpo nao vazio;
- decodificacao GZip/Base64 quando aplicavel;
- XML bem formado;
- schema XSD quando disponivel;
- chave de acesso extraida;
- CNPJ prestador/tomador/intermediario confere com empresa ou regra de visibilidade;
- data de emissao/competencia;
- status/eventos relevantes;
- hash SHA-256.

### Ao receber/gerar PDF

- nao aceitar HTML/JSON como PDF;
- verificar `%PDF-`;
- tamanho minimo plausivel;
- abrir com leitor PDF;
- extrair texto quando possivel;
- conferir chave ou pelo menos numero/CNPJ/data/valor;
- bloquear se PDF tiver erro embutido.

### Antes de atualizar ledger

- arquivo final existe;
- tamanho final igual ao temporario validado;
- hash final igual ao validado;
- destino correto;
- estado recuperavel gravado antes do avanco de NSU.

## Estrategia gratuita e consistente

Ferramentas preferidas:

- Java, seguindo o ecossistema Maven ja usado no projeto, ou outro stack somente se houver decisao explicita;
- APIs oficiais do governo, sem fornecedor pago;
- certificado A1 local;
- ledger em arquivos TSV/JSONL ou SQLite local;
- geracao local de PDF por biblioteca open source;
- Windows Task Scheduler para agenda no computador de operacao;
- logs locais fora da REST.

Evitar:

- servico pago de terceiro como dependencia obrigatoria;
- automacao de navegador para baixar documentos;
- download massivo de PDF se XML permite gerar local;
- guardar senha em planilha;
- depender de comportamento nao documentado.

## Ideias inovadoras e praticas

### 1. Cofre local simples por apelido

Na planilha fica apenas `CERTIFICADO_ALIAS`. O caminho/senha real ficam em cofre local protegido. Isso reduz vazamento e facilita troca de certificado.

### 2. Modo sombra

Antes de mover qualquer arquivo para REST/DMS, rodar 7 dias em modo sombra:

- consulta API;
- baixa XML;
- simula destino;
- gera painel;
- nao publica arquivo operacional.

Serve para descobrir empresas sem certificado, CNPJ raiz errado e volume real.

### 3. Reconciliador mensal

No dia 1 a 5 de cada mes, revalidar o mes anterior antes de fechar:

- procurar XML faltante;
- procurar PDF pendente;
- conferir cancelamentos/eventos;
- emitir relatorio de pendencias.

### 4. Pontuacao de confianca por nota

Cada nota recebe score:

- XML oficial validado: +50
- chave confere: +20
- CNPJ confere: +10
- PDF validado e confere: +10
- destino confirmado: +10

Somente nota com score suficiente vai para final. Baixa pontuacao vai para quarentena.

### 5. Fila separada para PDF

Nao misturar fila de XML com fila de PDF. XML e obrigatorio e leve; PDF pode ser pesado/instavel.

```text
fila_xml: descobre e salva documentos fiscais
fila_pdf: gera/baixa auxiliares pendentes
fila_publicacao: move para DMS/REST apos validacao
```

Mesmo separadas, as filas precisam se encontrar no estado final: uma chave so fica concluida quando as duas filas terminaram com sucesso.

### 6. Janela inteligente por volume

Se uma empresa tem muitas notas, limitar por lote:

- maximo de documentos por rodada;
- continuar na proxima janela;
- nao deixar uma empresa grande atrasar todas.

### 7. Painel de decisao humana minima

O operador nao deve ler log tecnico. Deve ver apenas:

- certificado vencendo;
- empresa sem caminho;
- API fora;
- XML em quarentena;
- PDF pendente;
- notas importadas hoje.

### 8. Logs compactados com indice mensal

Gravar logs em JSONL durante o dia e compactar em `.gz` no fechamento. Criar um indice mensal pequeno para busca posterior sem abrir todos os logs:

- empresa;
- CNPJ;
- chave de acesso;
- NSU;
- status XML;
- status PDF;
- horario da ultima tentativa;
- caminho final do XML;
- caminho final do PDF.

Retencao sugerida:

- logs detalhados: 15 dias;
- logs compactados: 12 meses;
- indice mensal: 5 anos ou conforme politica fiscal;
- quarentena: ate decisao humana.

### 9. Politica de espera inteligente

Quando a API falhar, nao martelar o portal. O sistema deve:

- tentar poucas vezes;
- abrir circuito;
- sinalizar painel;
- continuar outras empresas quando seguro;
- tentar novamente na proxima janela.

Estados de espera:

- `AGUARDANDO_BACKOFF`
- `CIRCUITO_API_ABERTO`
- `TENTAR_PROXIMA_JANELA`
- `PDF_PENDENTE_COM_ALERTA`

## Checklist revisado para dizer "pronto para producao"

Nao declarar producao pronta antes de cumprir:

- teste real em producao restrita;
- teste piloto em producao com 1 empresa;
- 7 dias de modo sombra;
- 3 rodadas diarias sem duplicidade;
- queda simulada no meio de download;
- certificado vencido simulado;
- PDF retornando HTML/JSON simulado;
- reexecucao da mesma rodada sem duplicar;
- reconciliacao dos ultimos 7 dias;
- validacao manual de amostra de XML/PDF;
- backup do ledger;
- documentacao de troca de certificado;
- decisao formal sobre destino XML e PDF.
- validacao de execucao atrasada ao ligar o computador depois do horario.
- validacao da compactacao e limpeza de logs.

## Atualizacao que deve ser aplicada ao plano

O plano principal deve ser lido com estas correcoes:

- A planilha nao deve ganhar `CAMINHO XML` nem `CAMINHO ENTRADA API PN` na V1; `CAMINHO REST` e a entrada unica para XML/PDF.
- Certificado deve ser identificado por pasta + nome exato do arquivo + alias.
- Senha deve ser resolvida por cofre/alias sempre que possivel, nao por texto aberto na planilha.
- IMPORT API PN deve depositar XML/PDF validados na raiz do `CAMINHO REST`, sem gravar dentro de `PDF/` ou `XML/`.
- RENOMEADOR organiza XML e PDF juntos em `PDF/...` e `XML/...`, sem duplicar regra no IMPORT API PN.
- Testes com certificado real devem ser `SOMENTE_LEITURA`.
- Endpoints de escrita fiscal devem ser bloqueados por codigo na V1.
- Fallback entre APIs deve ser controlado por fila, estado e circuit breaker.
- PDF local nao e opcional futuro; e requisito estrutural.
- PDF e obrigatorio para concluir nota, mesmo sendo gerado em fila separada.
- API DANFSe e fallback temporario ate 01/07/2026.
- XML tem destino final claro dentro do `CAMINHO REST`: `XML/processados`, `XML/RETIDO`, `XML/canceladas` ou `XML/TOMADOR NAO ENCONTRADO`.
- Ledger deve ser mais forte que `ULTIMO NSU`.
- Deve existir reconciliacao periodica.
- Deve existir execucao atrasada quando o computador ligar depois do horario.
- Deve existir log compactado com retencao e indice pesquisavel.
- `496 SSL Certificate Required` deve ser tratado primeiro como problema de certificado/configuracao, nao como instabilidade.
- Nenhuma nota vai para final sem validacao de chave, CNPJ e hash.

## Revisao de larga escala

O plano e viavel para muitas empresas, mas so se a implementacao seguir arquitetura de fila persistente e limites. Se for apenas um loop simples que percorre a planilha 3 vezes ao dia, ha risco real de travar, repetir processamento, esquecer pendencias ou sobrecarregar a API.

### Onde pode travar

- uma empresa com certificado ruim travar a rodada inteira;
- uma empresa com muitas notas consumir toda a janela;
- API lenta manter conexoes presas;
- PDF pendente ficar em loop eterno;
- log crescer demais;
- duas execucoes rodarem ao mesmo tempo;
- computador desligar no meio de uma rodada;
- rede cair depois de baixar XML mas antes de gravar ledger;
- `ULTIMO NSU` avancar antes da nota estar recuperavel;
- reprocessamento oficial de NSU trazer documentos antigos;
- PDF local falhar para um layout/campo inesperado;
- antivirus, rede Windows ou pasta DMS bloquear escrita.

### Correcoes obrigatorias para escala

1. **Fila persistente**
   - Nada pode existir so em memoria.
   - Cada empresa, NSU, chave, XML e PDF deve ter estado gravado em disco/SQLite/JSONL antes e depois de cada etapa.
   - Se o processo cair, a proxima execucao retoma do ultimo estado seguro.

2. **Lock global e lock por empresa**
   - Lock global impede duas rodadas simultaneas.
   - Lock por empresa impede que uma pendencia antiga e uma rodada nova mexam na mesma empresa ao mesmo tempo.

3. **Limite por empresa e por rodada**
   - Definir maximo de empresas por bloco, maximo de notas por empresa e maximo de chamadas por minuto.
   - Empresa grande continua na proxima janela sem travar as demais.

4. **Timeout em todas as operacoes**
   - Timeout HTTP.
   - Timeout de geracao de PDF.
   - Timeout de escrita/movimentacao em pasta de rede.
   - Timeout de leitura da planilha.

5. **Circuit breaker por endpoint**
   - Se ADN falhar repetidamente, parar ADN por um tempo.
   - Se DANFSe falhar, nao insistir.
   - Se so uma empresa falhar por certificado, bloquear so essa empresa.

6. **Prioridade de pendencias**
   - Rodada nova deve olhar primeiro pendencias recuperaveis antigas.
   - Depois buscar documentos novos.
   - PDF pendente deve ter fila propria, com limite diario.

7. **Idempotencia forte**
   - Rodar a mesma janela 10 vezes deve resultar no mesmo conjunto final de arquivos.
   - Chave de acesso + hash + estado final mandam mais que nome de arquivo.

8. **Publicacao em duas fases**
   - Baixar/gerar em temporario.
   - Validar.
   - Mover para final.
   - Conferir hash no destino.
   - So depois atualizar estado final.

9. **Backpressure**
   - Se API estiver lenta, reduzir ritmo automaticamente.
   - Se fila acumulou demais, processar em lotes.
   - Se disco estiver cheio, parar antes de corromper destino.

10. **Painel de saude**
    - Mostrar se a rodada esta atrasada, rodando, parada, em backoff ou bloqueada.
    - Mostrar numero de empresas restantes, notas XML pendentes, PDFs pendentes, certificados bloqueados e erros por API.

### Automacao maxima possivel

O sistema deve corrigir automaticamente:

- queda no meio de download;
- computador desligado no horario;
- API temporariamente fora;
- PDF pendente;
- arquivo temporario antigo;
- duplicidade exata;
- reexecucao da mesma janela;
- empresa com falha isolada;
- logs antigos sem compactacao;
- pasta final com arquivo ja existente e hash igual.

O sistema deve pedir acao humana quando:

- certificado venceu ou senha esta errada;
- CNPJ do certificado nao autoriza a empresa;
- XML vem com CNPJ/chave divergente;
- PDF gerado/baixado nao confere com XML;
- pasta DMS/REST nao existe ou esta sem permissao;
- disco esta quase cheio;
- houve mudanca oficial de API/schema/layout;
- a nota esta em quarentena por conflito fiscal.

### Resposta curta sobre "ja cobrimos tudo?"

Nao existe como prever qualquer bug futuro, principalmente com API externa do governo, certificado digital, pasta de rede e PDF. O que da para fazer e projetar para que bugs e falhas nao causem perda, duplicidade ou corrupcao. O plano agora cobre os principais riscos, mas antes de codigo precisamos transformar isso em requisitos testaveis:

- testes de idempotencia;
- testes de queda no meio;
- testes de API 429/500/502/503;
- testes de certificado ruim;
- testes de PDF falso;
- testes de reexecucao de janela;
- testes com muitas empresas;
- teste de soak de 1 semana em modo sombra.

Sem esses testes, o plano fica bom no papel, mas nao comprovado em escala.
