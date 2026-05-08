# Pesquisa de problemas da API do Portal Nacional NFS-e

Pesquisado em: 08/05/2026  
Modulo: `IMPORT API PN`  
Objetivo: registrar relatos recentes de pessoas/comunidades que integraram com NFS-e Nacional/ADN/DANFSe e transformar esses problemas em cuidados de arquitetura para nossa importacao automatica.

## Conclusao pratica

O risco principal do nosso projeto nao e apenas "como chamar a API". O risco e operar em producao sem duplicar nota, sem perder XML, sem salvar erro como PDF e sem depender de um servico de PDF que ja tem historico de instabilidade e tem suspensao oficial marcada para 01/07/2026.

Revisao critica adicionada em 08/05/2026: ver tambem `REVISAO_CRITICA_PLANO_IMPORTACAO.md`. Os problemas de DANFSe foram confirmados por relatos fortes de comunidade e a descontinuacao da API foi confirmada em fonte oficial. Ja os erros de certificado, especialmente `496 SSL Certificate Required`, devem ser classificados com cuidado: muitas vezes indicam configuracao/certificado nao carregado, nao instabilidade do portal.

Decisao recomendada:

1. Usar o ADN como fonte primaria para XML/DF-e.
2. Tratar PDF/DANFSe como obrigatorio para fechamento operacional, preferencialmente gerado a partir do XML.
3. Nao depender da API `danfse` como caminho unico.
4. Implementar ledger por NSU/chave antes de importar em massa.
5. Validar toda resposta HTTP antes de gravar arquivo final.
6. Rodar fila conservadora por empresa, com backoff e circuito aberto.

## Fontes consultadas

Fontes oficiais:

- APIs de Producao Restrita e Producao: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/apis-prod-restrita-e-producao`
- Manual de Contribuintes - APIs do ADN: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/manual-contribuintes-apis-adn-sistema-nacional-nfse.pdf`
- Nota Tecnica SE/CGNFS-e n. 008/2026 - DANFSe: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/rtc/nt-008-se-cgnfse-danfse-20260505.pdf`
- Documentacao Atual de Producao, XSDs e anexos: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual`
- Historico oficial de suspensao/reprocessamento de NSU: `https://www.gov.br/nfse/pt-br/noticias/api-de-distribuicao-nfs-e-ficara-suspensa` e `https://www.gov.br/nfse/pt-br/noticias/rotina-de-atualizacao-dos-nsus-e-concluida`

Relatos de comunidade/mercado:

- Forum Nota Nacional de Servico - erro DANFSe 501/503 e troca de endpoint: `https://forum.nfsebrasil.com.br/t/erro-na-consulta-geracao-do-danfse-pdf-bad-gatway-erro-503-ou-503/61`
- Forum Nota Nacional de Servico - geracao de PDF em producao com 502/503: `https://forum.nfsebrasil.com.br/t/geracao-de-pdf-das-nfs-e-nao-esta-funcionando/741`
- ACBr - erro Forbidden/496/timeout/502/503 em DANFSe: `https://www.projetoacbr.com.br/forum/topic/88941-erro-%E2%80%9Cforbidden-access-is-denied%E2%80%9D-ao-consultar-nfs-e-no-padr%C3%A3o-nacional-usando-openssl-acbr-c/`
- ACBr - erro ao usar `ObterDANFSE`: `https://www.projetoacbr.com.br/forum/topic/89202-mensagem-de-erro-ao-usar-o-metodo-acbrnfsex1obterdanfse/`
- ACBr - arquivo de erro salvo como PDF: `https://www.projetoacbr.com.br/forum/topic/87980-download-de-pdf-danfse/`
- ACBr - alternativa de gerar DANFSe localmente por XML: `https://www.projetoacbr.com.br/forum/topic/89963-acbr-8893-nfsex-mei-nacional-provedor-nacional-falha-de-obterdnfse-fr3/`
- ACBr - implementacao de impressao DANFSe por XML em 02/03/2026: `https://www.projetoacbr.com.br/forum/topic/90726-boas-novas-para-quem-emite-nfs-e-danfse-modelo-padr%C3%A3o-nacional/`
- ACBr - implementacao Fast Report em 06/04/2026: `https://www.projetoacbr.com.br/forum/topic/91581-boas-novas-para-quem-emite-nfs-e-danfse-modelo-padr%C3%A3o-nacional-fast-report/`
- ACBr - noticia da Nota Tecnica 008/2026 e fim da API do DANFSe: `https://www.projetoacbr.com.br/forum/topic/92150-cgnfs-e-publica-nota-t%C3%A9cnica-0082026-com-novas-regras-para-emiss%C3%A3o-do-danfse-padr%C3%A3o-nacional/`
- Nuvem Fiscal - indisponibilidade do servico nacional de PDF: `https://suporte.nuvemfiscal.com.br/t/internal-server-error-nfse/4849`
- Contabeis - relatos operacionais em janeiro/2026: `https://www.contabeis.com.br/forum/legalizacao-de-empresas/411887/emissor-nacional-com-erro/5`
- Reddit/brdev - endpoint descoberto por tentativa e erro, 503 e documentacao confusa: `https://www.reddit.com/r/brdev/comments/1nuecoe/nfse_emissor_nacional_api_de_produ%C3%A7%C3%A3o_danfse_501/`
- PyPI `dfe-nfse` - biblioteca criada para baixar NFS-e do ADN: `https://pypi.org/project/dfe-nfse/`
- GitHub `nfe/poc-nfse-nacional` - prova de conceito publica: `https://github.com/nfe/poc-nfse-nacional`

## Problemas encontrados nos relatos

### 1. API de PDF/DANFSe instavel

Relatos recorrentes mostram erros:

- `502 Bad Gateway`
- `503 Service Unavailable`
- `501 Not Implemented`
- `404 Not Found`
- timeout
- retorno intermitente: funciona por alguns minutos e depois falha

Impacto para nosso importador:

- se dependermos da API de PDF, a fila das 05h/12h/17h pode ficar parada;
- o XML pode existir, mas o PDF nao vir;
- se o PDF nao vier, a nota deve ficar pendente operacional, nao concluida;
- se o sistema insistir demais, aumenta carga no portal e piora a situacao;
- se salvar erro como `.pdf`, a pasta operacional fica contaminada com arquivo falso.

Mitigacao:

- baixar XML primeiro e marcar PDF como pendente;
- validar `Content-Type`, assinatura `%PDF-` e tamanho antes de aceitar PDF;
- nao retentar PDF indefinidamente na mesma rodada;
- gerar DANFSe localmente a partir do XML como solucao definitiva.
- painel deve destacar `PDF_PENDENTE` ate resolver.

### 2. API DANFSe sera suspensa em 01/07/2026

A Nota Tecnica SE/CGNFS-e n. 008/2026 informa que a API de geracao do DANFSe sera suspensa em 01/07/2026. Comunidades como ACBr ja estao orientando usuarios a carregar o XML da NFS-e Nacional e gerar/imprimir o PDF localmente.

Impacto:

- qualquer implementacao que dependa de `GET /danfse/{chave}` nasce com prazo de validade curto;
- se implementarmos somente download de PDF por API, teremos retrabalho obrigatorio em menos de dois meses.

Mitigacao:

- tratar `GET /danfse/{chave}` como opcional/transitorio;
- colocar `danfse_renderer` local como componente obrigatorio do desenho;
- usar o XML oficial como verdade fiscal.

### 3. Endpoint mudou e houve documentacao/Swagger desatualizado

Relatos de setembro/outubro de 2025 mostram mudanca de endpoint de DANFSe de `sefin.../SefinNacional/danfse/{chave}` para `adn.../danfse/{chave}`. Em forum, usuarios comentaram que descobriram por tentativa e erro e perguntaram se houve comunicacao oficial.

Impacto:

- automacao quebraria sem mudanca de codigo/config;
- operador pode achar que certificado ou nota esta errada quando o problema e endpoint;
- Swagger pode nao refletir o comportamento real do servico naquele dia.

Mitigacao:

- endpoints em arquivo de configuracao versionado, nao fixos no codigo;
- healthcheck por ambiente antes da fila real;
- documento operacional com endpoint atual e data de verificacao;
- monitorar paginas oficiais antes de alterar producao.

### 4. Certificado digital e mTLS geram erros confusos

Relatos citam:

- `496 SSL Certificate Required`
- `Forbidden: Access is denied`
- diferenca entre OpenSSL e WinHTTP em C#/ACBr;
- funcionamento em um cliente HTTP e falha em outro;
- necessidade de certificado especifico da filial em alguns cenarios.

Impacto:

- uma empresa pode falhar enquanto outra funciona;
- matriz pode nao enxergar notas da filial dependendo da regra aplicada pelo portal/consulta;
- erro de certificado pode ser confundido com indisponibilidade da API;
- senha/certificado vencido podem travar a fila inteira se nao houver isolamento por empresa.

Mitigacao:

- tratar `496 SSL Certificate Required` primeiro como falha local de certificado/configuracao/TLS;
- validar certificado antes da fila: existencia, senha, validade e CNPJ raiz;
- processar empresa por empresa;
- registrar falha de certificado como bloqueio da empresa, nao da rodada inteira;
- manter alerta de vencimento 30/15/7/1 dias;
- testar oficialmente se o certificado da matriz consulta filiais da planilha antes de prometer isso.

### 5. Resposta de erro pode ser salva como PDF

Relatos de usuarios ACBr indicam caso em que o metodo de obter DANFSe salvou resposta JSON/HTML de erro com extensao `.pdf`, gerando arquivo corrompido.

Impacto:

- nosso sistema poderia achar que o PDF existe e nunca baixar de novo;
- o DMS/REST receberia documento invalido;
- o RENOMEADOR poderia tentar processar um PDF falso;
- a conferencia humana ficaria mais dificil.

Mitigacao:

- todo PDF baixado deve passar por validacao binaria:
  - primeiros bytes `%PDF-`;
  - `Content-Type` compatilvel;
  - tamanho minimo plausivel;
  - leitura basica por biblioteca PDF quando possivel;
  - chave de acesso, se extraivel.
- falha vai para `PDF_PENDENTE` ou `QUARENTENA`, nunca para pasta final.

### 6. Robotizacao em massa pode degradar servico

Relatos no forum Nota Nacional mencionam suspeita de sistemas robotizados buscando DANFSe para guardar, com servico intermitente. Isso e diretamente relacionado ao nosso plano, porque queremos varrer empresas 3 vezes ao dia.

Impacto:

- se fizermos varredura agressiva, podemos contribuir para bloqueios/instabilidade;
- risco de 429, 502, 503 ou indisponibilidade por excesso de chamadas;
- risco reputacional/operacional se a rotina bater demais no portal.

Mitigacao:

- fila sequencial por empresa na V1;
- limite de chamadas por minuto;
- backoff exponencial;
- nao baixar PDF se XML ja esta salvo e PDF pode ser gerado localmente;
- nao consultar chaves ja concluidas no ledger;
- abortar rodada se houver instabilidade ampla.

### 7. Layout do DANFSe local ainda tem ajustes finos

Comunidades que passaram a gerar DANFSe localmente relatam problemas de impressao, como descricao do servico saindo com poucas linhas e informacoes complementares nao aparecendo como esperado.

Impacto:

- gerar PDF local e o caminho correto, mas nao e trivial;
- precisamos validar layout com notas reais de varios casos;
- XML com campos longos, quebras de linha e informacoes complementares precisa de teste.

Mitigacao:

- V1 pode manter PDF pendente se nao houver gerador local homologado;
- V1.1 deve implementar gerador local com suite de XMLs reais;
- validar campos obrigatorios da Nota Tecnica 008/2026;
- comparar PDF local contra modelo do Portal Nacional em homologacao.

### 8. Erros genericos E999/E9999 escondem causas diferentes

Relatos de bibliotecas/comunidades citam `E999` ou `E9999` para cenarios variados:

- erro de servidor;
- XML rejeitado;
- servico fora;
- certificado ausente;
- retorno HTML dentro de JSON;
- falha nao catalogada.

Impacto:

- nao da para tratar `E9999` como uma unica causa;
- retentar cegamente pode piorar a fila;
- erro fiscal definitivo pode ser confundido com erro transitorio.

Mitigacao:

- classificar erro por HTTP status, corpo, etapa e tipo de operacao;
- separar `FALHA_RETENTAVEL` de `FALHA_FINAL`;
- guardar amostra curta do erro em log tecnico, sem dados sensiveis;
- criar painel operacional com causa resumida.

### 9. Ambientes de homologacao e producao nao se comportam igual

Relatos indicam casos em que producao restrita funcionava e producao falhava, ou o contrario. Tambem houve relatos de endpoint oficial retornando erro em um ambiente e outro endpoint funcionando.

Impacto:

- passar em homologacao nao garante producao estavel;
- teste piloto em producao assistida e obrigatorio;
- nao podemos liberar todas as empresas de uma vez.

Mitigacao:

- fase piloto com poucas empresas;
- rodadas assistidas nos tres horarios;
- comparar resultado da API com consulta manual no Portal Nacional;
- ativacao gradual por lote de empresas.

### 10. Filiais, CNPJ raiz e visibilidade fiscal exigem teste real

O manual do ADN informa possibilidade de consulta com certificado cujo CNPJ tenha mesmo CNPJ raiz do contribuinte consultado, especialmente com parametro de CNPJ consulta por NSU. Relatos de usuarios, porem, indicam duvidas praticas sobre matriz/filial e acesso a notas.

Impacto:

- a planilha pode ter empresas que compartilham raiz, mas nao compartilham visibilidade operacional;
- se o sistema assumir visibilidade total, pode deixar de importar notas de filiais;
- certificados precisam ser mapeados explicitamente.

Mitigacao:

- coluna `CNPJ RAIZ CERTIFICADO`;
- teste por empresa real antes de ativar;
- ledger separado por CNPJ consultado, nao apenas por certificado;
- falha de visibilidade deve aparecer no painel.

### 11. XSDs, anexos e layout podem mudar

A documentacao oficial de producao lista XSDs e anexos atuais, e a area RTC mostra atualizacoes ligadas a novos grupos como IBS/CBS. Isso nao significa que a V1 precise emitir ou apurar tributos, mas significa que o importador deve versionar os XSDs usados na validacao e registrar a versao no log.

Impacto:

- XML novo pode trazer campos ainda nao previstos;
- gerador local de PDF pode precisar acompanhar Nota Tecnica do DANFSe;
- parser rigido demais pode rejeitar nota valida.

Mitigacao:

- validar XML de forma conservadora;
- guardar XML bruto sempre que recebido com seguranca;
- versionar XSD/anexo usado;
- criar alerta `SCHEMA_DESATUALIZADO`;
- revisar documentacao oficial antes de producao e mensalmente.

## Riscos especificos para a nossa rotina das 05h/12h/17h

### Risco: usar certificado real em endpoint errado

Regra:

- V1 deve ser somente leitura;
- bloquear emissao, cancelamento e eventos;
- bloquear metodos HTTP de escrita;
- registrar `MODO_SOMENTE_LEITURA` no log;
- fazer `dry-run` antes do primeiro teste real;
- consultar apenas notas ja existentes.

### Risco: perder nota por atualizar NSU cedo demais

Se a rotina consultar NSU, baixar parcialmente e atualizar `ULTIMO NSU` antes de salvar XML/PDF/estado, uma falha no meio pode pular nota.

Regra:

- `ULTIMO NSU CONFIRMADO` so avanca quando o item foi arquivado ou registrado em estado recuperavel.

Complemento: historico oficial mostra que distribuicao por API ja passou por suspensao/reprocessamento de NSU. Por isso, o sistema precisa guardar mais que um unico numero: pendencias, lacunas, chaves, hashes e janelas de reconciliacao.

### Risco: duplicar nota em pastas REST/DMS

Se a comparacao usar somente nome de arquivo, uma mudanca de nome ou de layout pode duplicar documento.

Regra:

- chave de acesso e a chave primaria;
- hash e campos fiscais sao chaves auxiliares;
- destino final so recebe arquivo depois de dedupe.

### Risco: travar fila inteira por uma empresa com certificado ruim

Regra:

- certificado invalido bloqueia apenas aquela empresa;
- a fila segue para a proxima;
- painel mostra `BLOQUEADO_CERTIFICADO`.

### Risco: portal instavel no horario da rodada

Regra:

- falha ampla abre circuito;
- rodada para sem insistir;
- proxima janela tenta de novo;
- operador recebe painel de `API_INSTAVEL`.

### Risco: fallback virar bagunca

Regra:

- fallback deve ser por estado, nao por tentativa solta;
- cada empresa continua com sua fila;
- cada nota continua com sua chave/NSU;
- cada API tem contador de falhas e circuito proprio;
- se uma porta falhou, tenta alternativa segura de leitura;
- se nenhuma alternativa funcionou, registra pendencia e segue para outra empresa;
- nas proximas janelas, volta aos pendentes antes de buscar novidades.

### Risco: baixar PDF do portal em massa sem necessidade

Regra:

- XML e obrigatorio;
- PDF e obrigatorio para concluir a nota;
- PDF por API e opcional/transitorio como meio de obtencao;
- geracao local deve virar padrao.

### Risco: computador desligado no horario planejado

Regra:

- o sistema registra janelas executadas;
- ao iniciar, detecta janelas perdidas;
- se a janela das 05:00 foi perdida, executa assim que o computador ligar;
- se houver muitas janelas perdidas, faz recuperacao controlada e reconciliacao para nao sobrecarregar o portal.

### Risco: logs crescerem sem limite

Regra:

- logs estruturados em JSONL durante o dia;
- compactacao gzip;
- retencao de logs detalhados por 15 dias;
- logs compactados por 12 meses;
- indice mensal pequeno para consulta futura;
- nunca gravar senha/certificado/payload sensivel completo.

## Checklist tecnico obrigatorio antes de implementar

- Confirmar endpoints oficiais no dia da implementacao.
- Testar `GET /DFe/{NSU}` em producao restrita com certificado A1.
- Testar parametro de CNPJ consultado para matriz/filial.
- Definir onde XML final sera guardado.
- Definir se PDF entra direto na REST ou passa pelo RENOMEADOR.
- Implementar ledger antes de qualquer download em massa.
- Implementar validacao de PDF real antes de mover para pasta final.
- Implementar estados recuperaveis para falha no meio.
- Implementar backoff e circuit breaker.
- Planejar gerador local de DANFSe conforme Nota Tecnica 008/2026.

## Orientacao final para arquitetura

A importacao deve ser desenhada como pipeline idempotente:

```text
empresa ativa
  -> certificado valido
  -> consulta ADN por NSU/CNPJ
  -> XML temporario
  -> valida XML/chave/CNPJ/mes
  -> grava XML final ou estado recuperavel
  -> PDF: gerar local ou baixar transitorio
  -> valida PDF real
  -> move final
  -> atualiza ledger
  -> painel da rodada
```

Nunca fazer:

- baixar PDF em massa sem ledger;
- salvar qualquer resposta como `.pdf` sem validacao;
- atualizar NSU antes de registrar estado recuperavel;
- deixar uma empresa quebrada parar todas;
- colocar certificado, senha, logs, ledger ou XML tecnico dentro da REST limpa do cliente;
- depender da API DANFSe depois de 01/07/2026.

## Ajustes apos revisao critica

- PDF local deve ser requisito estrutural, nao apenas melhoria futura.
- PDF deve ser obrigatorio para concluir nota: XML sem PDF e `PENDENTE_OPERACIONAL`.
- XML e PDF devem ter filas independentes.
- O destino fiscal final do XML deve ser definido antes de producao.
- A planilha deve guardar resumo; o estado real deve ficar em ledger tecnico.
- Deve existir modo sombra antes de publicar arquivos nas pastas finais.
- Deve existir reconciliacao diaria/semanal/mensal.
- Deve existir catch-up de janela perdida quando o computador ligar depois do horario.
- Deve existir log compactado com limite de retencao.
- Testes com certificado real devem bloquear qualquer operacao de escrita fiscal.
- Fallback entre caminhos oficiais deve ser controlado por fila e circuit breaker.
- `496` deve virar classe propria: `FALHA_CERTIFICADO_OU_TLS`.
- Resposta HTML/JSON em download de PDF deve virar `PDF_QUARENTENA` ou `PDF_PENDENTE`, nunca arquivo final.
