# IMPORT API PN - Plano de pesquisa e arquitetura

Pesquisado em: 08/05/2026  
Modulo: `IMPORT API PN`  
Objetivo: planejar a importacao automatica de NFS-e do Portal Nacional para todas as empresas cadastradas na `PLANILHA_FISCAL.xlsm`, sem misturar codigo com o modulo `RENOMEADOR/`.

## Resumo executivo

A forma mais correta para importar NFS-e do Portal Nacional e usar as APIs oficiais do Sistema Nacional NFS-e, principalmente o ADN - Ambiente de Dados Nacional, para consultar documentos fiscais por NSU e eventos por chave de acesso.

Revisao critica adicionada em 08/05/2026: ver tambem `REVISAO_CRITICA_PLANO_IMPORTACAO.md`. A meta operacional nao e "a API nunca falhar"; isso nao depende de nos. A meta e que o nosso sistema nunca perca nota, nunca duplique destino, nunca salve erro como PDF/XML valido e sempre retome automaticamente quando a falha for recuperavel.

Revisao operacional adicionada em 08/05/2026: para a empresa, XML e PDF sao obrigatorios. O XML continua sendo a fonte fiscal primaria, mas uma nota so fica `CONCLUIDA` quando tiver XML final validado e PDF final validado. Se o XML existir e o PDF faltar, o estado correto e `PDF_PENDENTE`, com retentativa automatica e sinal no painel.

O sistema deve rodar 3 vezes ao dia, em fila controlada, nos horarios:

- 05:00
- 12:00
- 17:00

Enquanto o sistema nao estiver em servidor ligado 24h, ele deve recuperar horario perdido. Exemplo: se o computador estava desligado as 05:00 e o operador ligar as 08:00, o sistema detecta que a janela das 05:00 nao executou e roda uma execucao `ATRASADA_05H` assim que iniciar.

Em cada rodada, ele deve processar empresa por empresa, nunca todas em paralelo sem limite. Para cada empresa, deve consultar o mes vigente, identificar o que ja existe nas pastas operacionais e baixar apenas o que falta: XML sempre pelo canal oficial e PDF/DANFSe por estrategia segura.

Ponto critico encontrado na pesquisa: a Nota Tecnica SE/CGNFS-e n. 008, de 05/05/2026, informa que a API de geracao do DANFSe (`https://adn.nfse.gov.br/danfse/docs/index.html`) sera suspensa em 01/07/2026. Portanto, o projeto nao deve nascer dependente dessa API como unico caminho para PDF. A decisao recomendada e:

1. XML oficial e a fonte primaria e deve ter destino fiscal final proprio.
2. PDF/DANFSe e obrigatorio para fechamento operacional e deve ser tratado em fila separada.
3. PDF deve ser gerado localmente a partir do XML como caminho estrutural.
4. Download pela API DANFSe e apenas fallback temporario enquanto existir e estiver saudavel.

## Fontes oficiais consultadas

- Documentacao Atual de Producao da NFS-e: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual`
- APIs de Producao Restrita e Producao: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/apis-prod-restrita-e-producao`
- Manual de Contribuintes - APIs do ADN: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/manual-contribuintes-apis-adn-sistema-nacional-nfse.pdf`
- Manual de Contribuintes - Emissor Publico API: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual/manual-contribuintes-emissor-publico-api-sistema-nacional-nfs-e-v1-2-out2025.pdf`
- Nota Tecnica SE/CGNFS-e n. 008 - Especificacoes Tecnicas do DANFSe, 05/05/2026: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/rtc/nt-008-se-cgnfse-danfse-20260505.pdf`
- Portal de Gestao NFS-e - Contribuinte: `https://www.nfse.gov.br/EmissorNacional`
- Documentacao Atual de Producao, com XSDs e anexos atuais: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/documentacao-atual`
- APIs oficiais de producao restrita e producao: `https://www.gov.br/nfse/pt-br/biblioteca/documentacao-tecnica/apis-prod-restrita-e-producao`
- Historico oficial de suspensao/reprocessamento de NSU: `https://www.gov.br/nfse/pt-br/noticias/api-de-distribuicao-nfs-e-ficara-suspensa` e `https://www.gov.br/nfse/pt-br/noticias/rotina-de-atualizacao-dos-nsus-e-concluida`

## APIs oficiais relevantes

### ADN - contribuintes

Uso planejado: consultar documentos fiscais de servico em que a empresa figure como emitente, tomador ou intermediario.

Ambientes oficiais:

- Producao restrita: `https://adn.producaorestrita.nfse.gov.br/contribuintes/docs/index.html`
- Producao: `https://adn.nfse.gov.br/contribuintes/docs/index.html`

Metodos documentados no manual de contribuintes do ADN:

- `GET /DFe/{NSU}`: retorna o documento fiscal de servico associado ao NSU informado.
- `GET /NFSe/{ChaveAcesso}/Eventos`: retorna eventos vinculados a uma NFS-e.

O manual informa tambem que a consulta pode usar certificado cujo CNPJ tenha o mesmo CNPJ raiz do contribuinte consultado. Para consulta por NSU, existe parametro para informar um CNPJ de consulta diferente do CNPJ do certificado, desde que o CNPJ raiz seja validado.

### SEFIN Nacional - emissor publico

Uso planejado: nao e o canal principal para importacao de notas recebidas. Ele e mais relevante para emissao/consulta por chave, DPS e eventos.

Ambientes oficiais:

- Producao restrita: `https://sefin.producaorestrita.nfse.gov.br/API/SefinNacional/docs/index`
- Producao: `https://sefin.nfse.gov.br/SefinNacional/docs/index`

Metodos documentados no manual:

- `POST /nfse`: geracao sincrona de NFS-e a partir de DPS.
- `GET /nfse/{chaveAcesso}`: consulta NFS-e por chave de acesso.
- `GET /dps/{id}` e `HEAD /dps/{id}`: consulta por identificador de DPS.
- `POST /nfse/{chaveAcesso}/eventos`: registro de eventos.
- `GET /nfse/{chaveAcesso}/eventos`: consulta de eventos.

Para testes com certificado real, usar somente operacoes de consulta. `POST /nfse` e `POST /nfse/{chaveAcesso}/eventos` ficam proibidos na V1, porque podem gerar/cancelar/alterar situacao fiscal.

### DANFSe

Ambientes oficiais:

- Producao restrita: `https://adn.producaorestrita.nfse.gov.br/danfse/docs/index.html`
- Producao: `https://adn.nfse.gov.br/danfse/docs/index.html`

Decisao: tratar como recurso transitorio. A Nota Tecnica n. 008 diz que a API de geracao do DANFSe sera suspensa em 01/07/2026. Assim, o sistema deve:

- tentar baixar PDF pela API enquanto ela estiver vigente;
- validar se a resposta e PDF real, nao HTML/JSON de erro salvo com extensao `.pdf`;
- registrar falha sem corromper a pasta operacional;
- manter tarefa tecnica para gerar DANFSe localmente a partir do XML.

## Caminhos de consulta e fallback

Existem mais de um "caminho" oficial possivel, mas eles nao tem o mesmo papel:

- ADN/contribuintes: caminho principal para descobrir e baixar XML/DF-e por NSU.
- SEFIN Nacional `GET /nfse/{chaveAcesso}`: consulta por chave conhecida, util como fallback de leitura.
- API DANFSe: caminho transitorio para PDF, com suspensao oficial em 01/07/2026.
- Gerador local de DANFSe: caminho recomendado para PDF a partir do XML.
- Portal web: nao usar na V1; automacao por navegador e fragil e arriscada.

Estrategia inteligente:

1. tentar o caminho principal da empresa;
2. se falhar por API instavel, abrir circuito daquela API e seguir para a proxima empresa;
3. se falhar por certificado, bloquear apenas aquela empresa e seguir;
4. se XML foi obtido mas PDF falhou, manter `PDF_PENDENTE` e seguir;
5. tentar alternativa segura somente se for consulta/leitura;
6. voltar automaticamente aos pendentes na proxima janela;
7. nunca perder a posicao da empresa nem esquecer nota pendente.

O sistema deve ter fila por empresa e por nota. Uma empresa com problema nao pode travar as demais. Uma nota com PDF pendente nao pode impedir a importacao dos XMLs seguintes.

## Escopo do modulo

O modulo `IMPORT API PN` deve:

- ler a `PLANILHA_FISCAL.xlsm` como cadastro compartilhado;
- selecionar empresas ativas do mes vigente;
- ler CNPJ, nome, caminhos REST/DMS, caminho do certificado digital, validade do certificado e senha/alias por mecanismo seguro;
- consultar o Portal Nacional/ADN em fila nos horarios definidos;
- baixar XMLs ausentes;
- obter ou gerar PDFs ausentes;
- colocar cada arquivo na pasta correta da empresa e do mes;
- manter ledger tecnico fora da REST do cliente;
- nunca duplicar XML/PDF que ja existe;
- registrar falhas por empresa e por nota;
- retentar falhas com backoff, sem exceder o portal.

Fora do escopo inicial:

- emissao de NFS-e;
- cancelamento;
- manifestacao/eventos ativos;
- automacao por navegador no portal web;
- alteracao do `RENOMEADOR/` nesta fase.

## Seguranca em testes com certificado real de cliente

Quando o teste usar certificado digital real de cliente, o modo de teste deve ser estritamente de consulta/leitura. O sistema nao pode chamar nenhum endpoint que emita, substitua, cancele, registre evento fiscal ou gere qualquer obrigacao nova para o cliente.

Regra de seguranca:

- ambiente de teste com certificado real deve iniciar em `MODO_SOMENTE_LEITURA`;
- liberar apenas endpoints `GET`/consulta previamente aprovados;
- bloquear por codigo qualquer `POST`, `PUT`, `PATCH` ou `DELETE` em producao;
- bloquear explicitamente emissao de DPS/NFS-e;
- bloquear cancelamento;
- bloquear eventos;
- bloquear qualquer endpoint da SEFIN Nacional que altere estado fiscal;
- salvar no log o modo `SOMENTE_LEITURA`;
- antes da primeira chamada real, rodar `dry-run` que imprime quais endpoints seriam chamados;
- exigir confirmacao manual para sair de `SOMENTE_LEITURA`, e isso so deve existir em fase futura, se houver decisao formal.

Com certificado de cliente, a V1 deve consultar somente documentos ja existentes. A finalidade e baixar XML/PDF de notas ja emitidas/disponibilizadas, nao criar movimento fiscal.

## Colunas novas sugeridas na planilha

Manter a planilha na raiz. Nao mover para dentro de modulo.

Colunas recomendadas por empresa/aba mensal:

- `IMPORT API PN ATIVO`: SIM/NAO.
- `CNPJ`: CNPJ da empresa.
- `CAMINHO REST`: entrada unica e destino operacional compartilhado com o RENOMEADOR.
- `CAMINHO DMS`: destino DMS, quando aplicavel somente para XML no formato Dominio.
- `CERTIFICADO API PN PASTA`: pasta onde ficam os certificados `.pfx` ou `.p12`.
- `CERTIFICADO API PN ARQUIVO`: nome exato do certificado a usar, porque a mesma pasta pode conter varios certificados.
- `CERTIFICADO API PN ALIAS`: apelido interno usado para buscar senha em cofre local.
- `VALIDADE CERTIFICADO API PN`: data de vencimento.
- `CNPJ RAIZ CERTIFICADO`: para validar se o certificado pode consultar aquele CNPJ.
- `ULTIMO NSU API PN`: ultimo NSU confirmado com sucesso por empresa.
- `MODO API PN`: `SOMBRA`, `PILOTO`, `PRODUCAO_ASSISTIDA` ou `PRODUCAO`.
- `AMBIENTE API PN`: `PRODUCAO_RESTRITA` ou `PRODUCAO`.
- `STATUS API PN`: OK, ATENCAO, BLOQUEADO_CERTIFICADO, FALHA_API etc.

Decisao revisada em 08/05/2026: nao criar `CAMINHO XML` nem `CAMINHO ENTRADA API PN` na V1. O importador entrega XML/PDF na raiz do `CAMINHO REST` da empresa/mes, e o RENOMEADOR separa em `PDF/...` e `XML/...`.

Colunas de compatibilidade ja citadas pelo RENOMEADOR, como `CAMINHO CERTIFICADO DIGITAL`, `VALIDADE CERTIFICADO DIGITAL` e `SENHA CERTIFICADO DIGITAL`, nao devem ser reutilizadas de forma ambigua sem decisao explicita. Para o IMPORT API PN, a recomendacao e usar colunas com prefixo `CERTIFICADO API PN ...`, para separar leitura de API do uso operacional atual.

Senha do certificado nao deve ficar em texto aberto na planilha. Opcoes seguras, em ordem recomendada:

- Windows Credential Manager;
- variavel de ambiente local protegida;
- arquivo de segredo fora do Git e fora da REST, com permissao restrita;
- cofre de senha no futuro;
- planilha apenas como fallback temporario de homologacao, com risco aceito e nunca logando a senha.

Regra pratica para certificado:

```text
certificado = CERTIFICADO API PN PASTA + CERTIFICADO API PN ARQUIVO
senha = buscar por CERTIFICADO API PN ALIAS no cofre local
```

## Estrutura operacional recomendada

Dentro do modulo:

```text
IMPORT API PN/
├── PLANO_IMPORTACAO_NFSE_PORTAL_NACIONAL.md
├── operacao/
│   ├── certificados/
│   │   └── .gitkeep
│   └── config/
│       └── .gitkeep
└── backend/
    ├── ledger/
    ├── logs/
    ├── fila/
    ├── quarentena/
    └── health/
```

Observacao: `operacao/certificados/` pode existir como pasta local, mas os certificados reais devem ficar fora do Git. O `.gitignore` do modulo deve ignorar `*.pfx`, `*.p12`, senhas, logs, ledgers, XML/PDF baixados e temporarios.

Nas pastas REST/DMS do cliente, criar apenas arquivos finais esperados pela operacao. Logs, fila, ledger, temporarios e quarentena ficam no backend tecnico do modulo.

## Fluxo de execucao 3 vezes ao dia

1. Scheduler dispara as 05:00, 12:00 e 17:00.
2. Ao iniciar, verifica se ha janela perdida desde a ultima execucao concluida.
3. Se houver janela perdida, cria uma rodada `ATRASADA_<horario>` e executa imediatamente.
4. Cria um lock global para impedir duas rodadas simultaneas.
5. Le a planilha ou um YAML exportado dela.
6. Filtra empresas ativas no mes vigente.
7. Ordena a fila de empresas de forma estavel.
8. Para cada empresa:
   - valida certificado e validade;
   - valida caminhos REST/DMS;
   - consulta o ultimo NSU confirmado no ledger tecnico;
   - busca novos DF-e pelo ADN;
   - filtra notas do mes vigente;
   - compara com ledger e com arquivos ja existentes;
   - baixa/grava XML ausente em area temporaria;
   - agenda PDF em fila separada;
   - gera PDF localmente pelo XML ou usa API DANFSe apenas como fallback transitorio;
   - valida tamanho, extensao, conteudo e chave;
   - entrega XML/PDF validado na raiz do `CAMINHO REST`, usando arquivo temporario e renomeacao atomica quando possivel;
   - nunca grava direto dentro de `PDF/` ou `XML/`, pois essas pastas sao saida do RENOMEADOR;
   - atualiza ledger somente depois do arquivo entregue estar confirmado.
9. Uma nota so vira `CONCLUIDA` quando XML e PDF estiverem finais, validados e organizados pelo RENOMEADOR.
10. Gera painel de status da rodada.
11. Libera lock.

## Como decidir se a nota ja existe

Nao confiar apenas no nome do arquivo. A chave fiscal principal deve ser a chave de acesso da NFS-e.

Chaves e indices:

- `chave_acesso`: identificador principal.
- `cnpj_prestador`
- `cnpj_tomador`
- `numero_nfse`
- `data_emissao`
- `valor_servico`
- `valor_liquido`
- `tipo_documento`: XML ou PDF.
- `sha256_arquivo`: evita regravar arquivo identico.
- `nsu`: controle de distribuicao no ADN.
- `estado_xml`: estado separado do XML.
- `estado_pdf`: estado separado do PDF.
- `estado_conjunto`: `CONCLUIDO` apenas quando XML e PDF estiverem presentes e validos.

Antes de baixar:

- procurar a chave no ledger tecnico;
- procurar arquivo XML/PDF final por chave de acesso no destino;
- opcionalmente ler XMLs existentes para confirmar chave;
- se houver arquivo com mesmo nome mas chave divergente, mandar para revisao/quarentena.

Depois de baixar:

- validar se XML contem a chave esperada;
- validar se PDF e realmente PDF (`%PDF-`) e nao erro HTML/JSON;
- validar tamanho minimo plausivel;
- gravar em temporario;
- entregar na raiz do `CAMINHO REST` para o RENOMEADOR organizar;
- atualizar ledger.

## Mes vigente e roteamento por pasta

O recorte solicitado e o mes vigente. Em cada rodada, o sistema deve consultar novos documentos e filtrar por data de emissao/competencia dentro do mes atual.

Regra conservadora:

- se a nota pertence ao mes vigente, arquivar na pasta do mes vigente da empresa;
- se aparecer nota de mes anterior por atraso do ADN, registrar como `FORA_DO_MES_VIGENTE` e nao mover automaticamente na V1, salvo decisao posterior;
- se o tomador/prestador indicar outra empresa da planilha, rotear para a empresa correta apenas quando a chave fiscal e CNPJ baterem com seguranca;
- caso contrario, mandar para quarentena tecnica e painel `ATENCAO`.

## REST e DMS

Decisao revisada de integracao com o RENOMEADOR:

- `CAMINHO REST` e a entrada unica: IMPORT API PN deposita XML e PDF na raiz dessa pasta.
- IMPORT API PN nao classifica retencao, cancelamento, tomador incorreto nem nome operacional final.
- RENOMEADOR organiza PDF e XML juntos, usando as mesmas regras fiscais.
- A saida final do RENOMEADOR e separada por tipo:

```text
CAMINHO REST/
├── PDF/
│   ├── processados/
│   ├── RETIDO/
│   ├── canceladas/
│   └── TOMADOR NAO ENCONTRADO/
└── XML/
    ├── processados/
    ├── RETIDO/
    ├── canceladas/
    └── TOMADOR NAO ENCONTRADO/
```

Regras praticas:

- IMPORT API PN grava primeiro em temporario no backend ou na REST com extensao temporaria, valida hash/conteudo e so depois renomeia para `.xml` ou `.pdf`.
- IMPORT API PN nao grava diretamente em `PDF/` nem `XML/`.
- RENOMEADOR decide a pasta final correta por CNPJ do tomador, mes de emissao, cancelamento e retencao.
- DMS nao precisa receber PDF na decisao atual; quando usado, recebe somente XML no formato Dominio, em fluxo separado e documentado antes da producao.
- Nao criar `CAMINHO XML` separado na planilha enquanto a decisao operacional for `CAMINHO REST` unico.

## Certificados digitais

Requisitos:

- usar certificado A1 (`.pfx` ou `.p12`) por empresa ou por grupo de empresas do mesmo CNPJ raiz;
- validar validade antes de chamar API;
- avisar com antecedencia, por exemplo 30, 15, 7 e 1 dias antes do vencimento;
- bloquear consulta se vencido;
- nao versionar certificado;
- nao gravar senha em logs;
- nao expor caminho completo/senha em painel operacional compartilhado.

Validacoes por rodada:

- arquivo existe;
- extensao permitida;
- validade >= data atual;
- CNPJ do certificado ou CNPJ raiz e compativel com a empresa consultada;
- senha carrega o certificado;
- certificado nao esta perto de vencer sem alerta.

## Controle de limite e prudencia com portal

Como a documentacao oficial consultada nao informou uma tabela publica clara de rate limit, a postura deve ser conservadora:

- uma empresa por vez na V1;
- intervalo pequeno entre chamadas, por exemplo 1 a 3 segundos;
- backoff exponencial em 429, 500, 502, 503 e timeout;
- maximo de tentativas por nota/empresa por rodada;
- circuit breaker por ambiente: se o ADN estiver instavel, parar a rodada e reagendar;
- evitar consulta repetida do mesmo NSU/chave quando o ledger ja confirmou sucesso;
- nunca rodar uma segunda fila enquanto a primeira ainda estiver ativa.
- separar fila de XML e fila de PDF para nao deixar PDF instavel atrasar XML fiscal.
- se uma empresa tiver volume alto, limitar por lote e continuar na proxima janela.

Retentativas sugeridas:

- erro de rede/timeout: 3 tentativas com 30s, 2min, 10min;
- 429/limite: parar empresa atual, reagendar para proxima janela;
- 500/502/503: 2 tentativas curtas e depois circuito aberto;
- certificado invalido/vencido: nao retentar ate operador corrigir;
- XML invalido ou chave divergente: quarentena sem retentativa automatica;
- PDF indisponivel: manter XML importado e marcar PDF pendente para geracao/download posterior.
- XML importado com PDF faltante: sinalizar `PDF_PENDENTE`, manter na fila de PDF e continuar tentando nas proximas janelas ate resolver ou exigir acao humana.
- erro `496 SSL Certificate Required`: primeiro tratar como certificado nao carregado/configurado, certificado incompativel, cadeia TLS ou cliente HTTP inadequado; nao classificar automaticamente como instabilidade do portal.

## Falhas e recuperacao

Toda acao deve ser idempotente: rodar de novo nao pode duplicar nem estragar o que ja foi importado.

Estados por item:

- `DESCOBERTO`
- `XML_BAIXADO_TEMP`
- `XML_VALIDADO`
- `XML_ARQUIVADO`
- `PDF_PENDENTE`
- `PDF_BAIXADO_TEMP`
- `PDF_VALIDADO`
- `PDF_ARQUIVADO`
- `CONCLUIDO`
- `CONCLUIDO_COM_XML_E_PDF`
- `FALHA_RETENTAVEL`
- `FALHA_FINAL`
- `QUARENTENA`

Estados revisados devem ser separados para XML e PDF. XML pode estar `XML_ARQUIVADO_FINAL` enquanto PDF permanece `PDF_PENDENTE_GERACAO`. Nesse caso a nota nao esta concluida para a operacao da empresa; ela fica pendente no painel ate o PDF ser obtido/gerado e validado.

Regra de ouro: atualizar `ULTIMO NSU` somente depois que os documentos ate aquele NSU estiverem tratados ou registrados de forma recuperavel. Se atualizar cedo demais, perde nota.

O controle real de NSU deve ficar em ledger tecnico por empresa/CNPJ, nao somente na planilha. A planilha pode exibir resumo, mas o backend deve guardar `ultimo_nsu_confirmado`, pendencias, erros, chaves e hashes.

## Reconciliacao periodica

A rotina incremental das 05:00, 12:00 e 17:00 nao basta sozinha. Devem existir varreduras de reconciliacao:

- diaria: revalidar ultimos 7 dias do mes vigente;
- semanal: revalidar o mes vigente completo;
- fechamento mensal: revalidar o mes anterior antes de encerrar;
- excepcional: permitir revarredura desde NSU 0/1 se houver comunicado oficial de reprocessamento.

Toda reconciliacao deve deduplicar por chave de acesso e hash antes de gravar destino.

## Execucao atrasada quando o computador ligar depois do horario

Enquanto o sistema rodar no computador do operador e nao em servidor 24h, o scheduler deve ser tolerante a desligamento:

- manter `agenda-state.json` com ultimas janelas planejadas e executadas;
- ao iniciar, calcular janelas vencidas ainda nao concluidas;
- executar a janela perdida mais antiga primeiro;
- se ligou as 08:00, executar a janela das 05:00;
- se ligou as 13:00, executar 05:00 e 12:00 em sequencia, respeitando lock e limite;
- se ligou depois de varios dias, nao rodar tudo sem limite: criar uma rodada de recuperacao por dia/mes vigente e priorizar reconciliacao;
- registrar no painel: `RODADA_ATRASADA_EXECUTADA`.

Regra: uma execucao atrasada deve ser idempotente. Ela nao pode duplicar nada, porque a decisao final continua baseada em chave de acesso, hash e ledger.

## Logs compactados e retencao

O sistema deve registrar o suficiente para auditoria e correcao futura sem encher armazenamento.

Arquivos recomendados:

- `backend/logs/rodada-AAAA-MM-DD.jsonl`: eventos estruturados por rodada.
- `backend/logs/api-AAAA-MM-DD.jsonl`: chamadas resumidas, status HTTP, tempo, endpoint, empresa, sem senha/certificado.
- `backend/logs/erros-AAAA-MM-DD.jsonl`: erros e stack traces curtos.
- `backend/logs/auditoria-AAAA-MM.jsonl.gz`: logs diarios compactados no fechamento do dia/mes.

Politica de retencao:

- logs detalhados diarios sem compactar: 15 dias;
- logs compactados `.gz`: 12 meses;
- payload bruto de erro: guardar somente amostra limitada e mascarada, nunca certificado/senha;
- arquivos temporarios antigos: limpar apos 7 dias se estiverem recuperados ou invalidados;
- quarentena: manter ate decisao humana, com relatorio mensal.

Compactacao:

- JSONL compactado com gzip e suficiente, gratuito, simples e pesquisavel depois;
- nao usar formato binario proprietario na V1;
- criar indice mensal pequeno com empresa, chave, NSU, estado final e caminho dos arquivos.

## Politica de retentativa e espera

Retentativas devem ser previsiveis para nao sobrecarregar o Portal Nacional:

- falha de rede/timeout: tentar 3 vezes na mesma rodada, com 30s, 2min e 10min;
- `429` ou suspeita de limite: parar empresa atual, abrir circuito por 60min e tentar na proxima janela;
- `500/502/503`: 2 tentativas curtas; se continuar, abrir circuito por 30min;
- `401/403/496`: classificar como certificado/TLS/autorizacao e nao insistir em loop;
- PDF pendente: tentar em toda janela, mas com limite por nota por dia;
- apos 3 janelas com PDF pendente, destacar em `alertas.tsv`;
- apos 3 dias com PDF pendente, exigir revisao humana, mas continuar reconciliando XML.

Painel:

- `OK`: XML e PDF concluidos;
- `PDF_PENDENTE`: XML existe, PDF ainda nao;
- `API_INSTAVEL_AGUARDANDO`: circuito aberto por falha externa;
- `CERTIFICADO_BLOQUEADO`: corrigir certificado/senha;
- `QUARENTENA`: divergencia ou arquivo invalido.

## Escala com muitas empresas

Como a rotina vai executar 3 vezes ao dia para varias empresas, a V1 nao pode ser um loop simples sobre a planilha. Ela deve ser uma fila persistente, com estado por empresa, por NSU, por chave, por XML e por PDF.

Regras obrigatorias:

- uma empresa com problema nao trava as demais;
- uma empresa grande nao consome toda a janela;
- cada chamada de API tem timeout;
- cada escrita em pasta de rede tem timeout e verificacao;
- toda etapa grava estado antes de passar para a proxima;
- rodar a mesma janela de novo nao duplica;
- pendencias antigas sao retomadas antes de buscar novidades;
- PDF pendente nao bloqueia XML novo, mas impede conclusao daquela nota;
- logs sao compactados automaticamente;
- se disco estiver cheio ou quase cheio, parar antes de gravar arquivo incompleto.

Limites iniciais sugeridos para homologacao:

- processar 1 empresa por vez;
- no maximo 50 notas novas por empresa por rodada, ajustavel;
- no maximo 3 tentativas HTTP por chamada;
- no maximo 10 PDFs pendentes por empresa por janela, ajustavel;
- pausa de 1 a 3 segundos entre chamadas;
- circuit breaker de 30 a 60 minutos para API instavel.

Esses numeros devem virar configuracao, nao ficar fixos no codigo.

## O que ainda nao e automatico

Algumas falhas nao devem ser corrigidas automaticamente, porque a correcao cega pode causar erro fiscal ou bagunca operacional:

- certificado vencido;
- senha de certificado errada;
- CNPJ sem autorizacao;
- XML com CNPJ/chave divergente;
- PDF que nao confere com XML;
- pasta REST/DMS sem permissao;
- mudanca oficial de API/schema/layout;
- disco sem espaco.

Nesses casos, o sistema deve isolar, sinalizar no painel e seguir com outras empresas.

## Tecnicas de performance

Performance aqui significa processar bem sem agredir o Portal Nacional e sem travar o computador do operador.

Tecnicas obrigatorias:

- cache local de certificado carregado por rodada, sem reabrir o `.pfx` para cada nota;
- conexao HTTP com timeout curto e pool pequeno;
- fila sequencial na V1, com paralelismo so para tarefas locais seguras no futuro;
- evitar download repetido: consultar ledger antes de chamar API;
- validar primeiro metadados leves e so depois processar PDF;
- gerar PDF em fila separada para nao bloquear XML;
- limitar tamanho e tempo de geracao de PDF;
- usar streaming para XML/PDF, sem carregar arquivos grandes inteiros em memoria;
- compactar logs fora do horario de pico;
- manter indice mensal pequeno para busca rapida;
- pausar automaticamente se CPU, memoria, disco ou rede estiverem em estado ruim;
- medir tempo por empresa, tempo por nota, chamadas por API, PDFs pendentes e tamanho de backlog.

KPIs iniciais:

- tempo medio por empresa;
- tempo medio por nota XML;
- tempo medio por PDF;
- taxa de sucesso XML;
- taxa de sucesso PDF;
- quantidade de `PDF_PENDENTE`;
- quantidade de empresas bloqueadas por certificado;
- chamadas HTTP por rodada;
- tamanho da fila pendente;
- uso de disco do backend.

## Fases de implementacao

### Fase 0 - Fechamento tecnico do desenho

Objetivo: congelar escopo da V1 para consulta/importacao somente leitura.

Checklist de aceite:

- endpoints oficiais revisados no dia da implementacao;
- `MODO_SOMENTE_LEITURA` definido como padrao;
- decisao registrada: `CAMINHO REST` e entrada unica, com saida final em `PDF/...` e `XML/...`;
- decisao registrada: DMS recebe somente XML Dominio quando aplicavel;
- contrato `IMPORT API PN -> RENOMEADOR` aprovado para entrega de XML/PDF na raiz do `CAMINHO REST`;
- colunas `CERTIFICADO API PN PASTA`, `CERTIFICADO API PN ARQUIVO` e `CERTIFICADO API PN ALIAS` aprovadas na planilha;
- decisao registrada sobre cofre local de certificados;
- limites iniciais definidos em configuracao.

Testes/validacao:

- revisao manual dos documentos;
- checklist assinado antes de usar certificado real;
- dry-run de endpoints sem chamada externa real.

### Fase 1 - Cadastro, configuracao e certificados

Objetivo: ler planilha/YAML, validar empresas e validar certificados sem chamar API fiscal.

Checklist de aceite:

- empresas ativas carregadas;
- CNPJs normalizados;
- `CAMINHO REST` validado como pasta de entrada e saida operacional;
- `CAMINHO DMS` validado somente quando a empresa exigir XML Dominio;
- certificado resolvido por pasta + arquivo;
- senha resolvida por alias/cofre local;
- certificado A1 abre com senha;
- validade e CNPJ raiz conferidos;
- certificado vencido bloqueia apenas a empresa;
- senha nao aparece em log.

Testes/validacao:

- certificado valido;
- certificado vencido;
- senha errada;
- CNPJ incompatível;
- caminho inexistente;
- planilha com coluna ausente.

### Fase 2 - Scheduler, catch-up e fila persistente

Objetivo: executar 05:00, 12:00 e 17:00, recuperar janela perdida e persistir estado.

Checklist de aceite:

- lock global impede duas rodadas simultaneas;
- se ligar 08:00, executa `ATRASADA_05H`;
- se ligar 13:00, executa 05:00 e 12:00 em ordem;
- fila sobrevive a queda do processo;
- cada empresa tem estado proprio;
- cada nota tem estado por NSU/chave.

Testes/validacao:

- simular computador desligado;
- simular processo morto no meio;
- reexecutar mesma janela 10 vezes;
- confirmar zero duplicidade.

### Fase 3 - Cliente ADN somente leitura e XML

Objetivo: consultar DF-e pelo ADN, baixar XML e validar documento fiscal.

Checklist de aceite:

- somente `GET`/consulta permitido;
- `POST/PUT/PATCH/DELETE` bloqueados por codigo;
- XML salvo primeiro em temporario;
- XML validado por estrutura, chave, CNPJ, data e hash;
- XSD oficial versionado/configurado;
- ledger atualizado somente apos estado recuperavel;
- NSU nao avanca cedo demais.

Testes/validacao:

- XML valido;
- XML malformado;
- XML com CNPJ divergente;
- XML duplicado;
- API timeout;
- API 500/502/503;
- reprocessamento/reconciliacao de NSU.

### Fase 4 - PDF obrigatorio

Objetivo: garantir que toda nota com XML tenha PDF validado.

Checklist de aceite:

- nota com XML e sem PDF fica `PENDENTE_OPERACIONAL`;
- gerador local de PDF recebe XML validado;
- API DANFSe e fallback temporario;
- HTML/JSON nunca vira PDF final;
- PDF abre em leitor PDF;
- PDF confere com XML por chave ou campos fiscais;
- PDF pendente aparece no painel.

Testes/validacao:

- PDF gerado localmente;
- API DANFSe fora;
- PDF retornando HTML/JSON;
- PDF corrompido;
- PDF com CNPJ/numero divergente;
- XML concluido com PDF pendente.

### Fase 5 - Publicacao REST/DMS e deduplicacao final

Objetivo: entregar XML/PDF validados ao RENOMEADOR pela raiz do `CAMINHO REST` e deixar o RENOMEADOR mover os arquivos finais para destinos corretos sem duplicar e sem corromper.

Checklist de aceite:

- XML/PDF gerados primeiro em temporario;
- arquivos validados sao depositados na raiz do `CAMINHO REST`;
- RENOMEADOR reconhece XML/PDF soltos e organiza os dois em `PDF/...` e `XML/...`;
- IMPORT API PN nao duplica regras de retencao/cancelamento do RENOMEADOR;
- publicacao em duas fases: temporario, validacao, entrega ao RENOMEADOR;
- hash conferido no destino;
- arquivo existente com mesmo hash nao sobrescreve;
- nome igual com chave diferente vai para quarentena;
- pasta sem permissao bloqueia apenas a empresa;
- REST nao recebe logs/ledger nem arquivos dentro de `PDF/` ou `XML/` gravados pelo importador.

Testes/validacao:

- destino normal;
- arquivo ja existente igual;
- arquivo ja existente diferente;
- pasta sem permissao;
- rede cai durante copia;
- disco cheio.
- XML entregue sem PDF correspondente;
- PDF entregue sem XML correspondente;
- ledger do importador divergente dos arquivos entregues.

### Fase 6 - Logs, painel e retencao

Objetivo: dar visibilidade operacional e manter auditoria sem encher armazenamento.

Checklist de aceite:

- `painel-rodada.tsv`;
- `painel-notas.tsv`;
- `alertas.tsv`;
- `health.json`;
- logs JSONL diarios;
- compactacao `.gz`;
- retencao configurada;
- indice mensal pesquisavel;
- senha/certificado nunca aparecem em log.

Testes/validacao:

- rodada OK;
- API instavel;
- certificado bloqueado;
- PDF pendente;
- compactacao;
- limpeza por idade;
- busca por chave no indice mensal.

### Fase 7 - Modo sombra e escala

Objetivo: provar comportamento com varias empresas sem publicar em producao final.

Checklist de aceite:

- 7 dias em modo sombra;
- 3 janelas diarias simuladas/reais;
- nenhuma duplicidade;
- pendencias retomadas;
- empresas bloqueadas nao travam fila;
- relatorio diario revisado.

Testes/validacao:

- muitas empresas;
- empresa com muito volume;
- API intermitente;
- computador ligado atrasado;
- reexecucao manual;
- soak de 1 semana.

### Fase 8 - Producao assistida

Objetivo: ativar com poucas empresas e conferencia humana.

Checklist de aceite:

- piloto com 1 empresa;
- depois lote pequeno;
- XML e PDF conferidos manualmente por amostra;
- painel revisado todo dia;
- plano de rollback operacional;
- backup do ledger.

Testes/validacao:

- comparar com Portal Nacional manualmente;
- comparar quantidade XML/PDF por empresa;
- conferir DMS/REST;
- simular falha e recuperacao real.

### Fase 9 - Producao normal

Objetivo: operar 3 vezes ao dia com monitoramento.

Checklist de aceite:

- backlog controlado;
- `PDF_PENDENTE` dentro do limite aceito;
- certificados com alerta antecipado;
- logs compactados;
- reconciliacao diaria/semanal/mensal ativa;
- plano de atualizacao de XSD/API.

Testes/validacao:

- revisao semanal de painel;
- reconciliacao mensal;
- teste periodico de restore do ledger;
- auditoria de amostra XML/PDF.

## Arquivos e nomes

IMPORT API PN nao define o nome final operacional. Ele deve usar nomes temporarios controlados por chave/NSU/hash apenas ate entregar o arquivo validado na raiz do `CAMINHO REST`.

O nome final e responsabilidade do RENOMEADOR:

```text
PDF/NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valor>.pdf
XML/NFSE_<numero>_<prestador>_<dataDD.MM.AAAA>_<valor>.xml
```

Compatibilidade: IMPORT API PN nunca deve entregar PDF/XML diretamente nas subpastas finais `PDF/` ou `XML/`, para nao duplicar criterios de retencao, cancelamento, tomador incorreto e nomenclatura.

## Arquitetura futura sugerida

Componentes:

- `config`: leitura da planilha/YAML e validacao de empresas.
- `certificados`: carregamento seguro de A1, validade e CNPJ raiz.
- `scheduler`: agenda 05:00/12:00/17:00 e lock de instancia.
- `catchup`: detecta e executa janelas perdidas quando o computador estava desligado.
- `fila`: processamento empresa por empresa.
- `adn_client`: cliente HTTP mTLS/certificado para ADN.
- `danfse_client`: cliente transitorio para API de PDF.
- `danfse_renderer`: gerador local de PDF a partir do XML, necessario antes/depois de 01/07/2026.
- `reconciliador`: revarredura controlada de janelas recentes e fechamento mensal.
- `cofre`: resolucao segura de senha/certificado por alias.
- `validador_pdf`: impede HTML/JSON/erro salvo como PDF.
- `validador_xml`: schema, chave, CNPJ, mes, evento e hash.
- `dedupe`: ledger por chave de acesso, NSU, hash e destino final.
- `storage`: escrita temporaria, validacao e movimento seguro.
- `painel`: TSV/CSV/JSON com status operacional.
- `log_retention`: compactacao gzip, limpeza por idade e indice mensal pesquisavel.

## Plano de homologacao antes de producao

1. Confirmar credenciais/certificados A1 de uma empresa piloto.
2. Testar em producao restrita.
3. Baixar XML por NSU.
4. Baixar ou gerar PDF para uma chave conhecida.
5. Validar que a nota nao duplica em segunda execucao.
6. Simular falha de rede e confirmar retentativa.
7. Simular certificado vencido e confirmar bloqueio.
8. Simular PDF com erro HTML/JSON e confirmar que nao salva como PDF valido.
9. Rodar uma fila piloto com 2 ou 3 empresas.
10. Conferir REST/DMS manualmente.
11. Rodar 7 dias em modo sombra, sem publicar em REST/DMS final.
12. Simular queda no meio de download e confirmar retomada.
13. Simular reexecucao da mesma rodada e confirmar zero duplicidade.
14. Rodar reconciliacao dos ultimos 7 dias.
15. So depois ativar producao assistida.
16. Rodar teste de escala com muitas empresas em modo sombra.
17. Rodar teste de reexecucao da mesma janela 10 vezes e confirmar zero duplicidade.
18. Rodar teste de queda simulada entre XML, PDF e ledger.

## Decisoes pendentes

- Qual coluna/cadastro oficial indicara empresa ativa para `IMPORT API PN`?
- A senha do certificado ficara em qual cofre local?
- O DMS exige nomenclatura propria para XML Dominio?
- Devemos gerar DANFSe local desde a primeira versao, considerando a suspensao oficial em 01/07/2026?
- Qual sera o nome temporario usado pelo importador antes de entregar XML/PDF na raiz do `CAMINHO REST`?

## Recomendacao de primeira versao

V1 deve ser pequena e segura:

- ler cadastro;
- validar certificados;
- consultar ADN por empresa;
- baixar XML do mes vigente;
- manter ledger;
- nao duplicar;
- separar fila XML e fila PDF;
- gerar PDF localmente quando possivel;
- usar API DANFSe apenas como fallback temporario ate 01/07/2026;
- deixar PDF pendente quando falhar, sem perder XML, mas sem marcar a nota como concluida;
- executar janelas atrasadas quando o computador ligar depois do horario;
- manter logs estruturados compactados e com retencao;
- gerar painel operacional;
- entregar XML/PDF na raiz do `CAMINHO REST` e deixar o RENOMEADOR organizar.

V1.1 deve completar/homologar visualmente a geracao local do DANFSe a partir do XML, porque depender da API DANFSe nao e sustentavel apos 01/07/2026.
