# Plano de testes, homologacao e liberacao para producao

Modulo: `IMPORT API PN`  
Data: 08/05/2026

## Objetivo

Definir como testar o importador de XML e PDF/DANFSe ate liberar producao, evitando perda de nota, duplicidade, cobranca indevida, arquivo falso, travamento de fila e crescimento descontrolado de logs.

## Principios

- Teste com certificado real sempre em `MODO_SOMENTE_LEITURA`.
- Nenhum `POST`, `PUT`, `PATCH` ou `DELETE` permitido na V1.
- XML e PDF sao obrigatorios para conclusao operacional.
- Falha externa nao pode virar duplicidade ou perda.
- Toda falha recuperavel precisa ser retomada automaticamente.
- Toda falha nao recuperavel precisa ser isolada e aparecer no painel.

## Ambientes de teste

### Ambiente local sem API real

Uso:

- validar parser de planilha/config;
- validar certificados com mocks;
- validar fila;
- validar ledger;
- validar deduplicacao;
- validar logs;
- validar PDF falso/corrompido.

Aceite:

- todos os testes automatizados passam;
- nenhuma chamada externa real acontece.

### Producao restrita

Uso:

- validar endpoints oficiais;
- validar TLS/certificado quando possivel;
- validar comportamento de API sem risco fiscal real.

Aceite:

- somente leitura;
- logs sem senha/certificado;
- falhas classificadas corretamente.

### Modo sombra em producao real

Uso:

- usar certificado real para consultar;
- baixar/validar/simular destino;
- nao publicar em REST/DMS final.

Aceite:

- 7 dias de execucao;
- 3 janelas por dia ou catch-up quando computador ligar depois;
- zero duplicidade;
- pendencias retomadas;
- painel revisado.

### Producao assistida

Uso:

- publicar para 1 empresa piloto;
- conferir manualmente XML e PDF;
- depois ativar lote pequeno.

Aceite:

- XML/PDF batem com Portal Nacional;
- DMS/REST recebem arquivos corretos;
- nenhum arquivo falso;
- rollback operacional documentado.

## Matriz de testes obrigatorios

| Grupo | Teste | Resultado esperado |
|---|---|---|
| Seguranca | Tentar configurar `POST /nfse` | Bloqueado antes da chamada |
| Seguranca | Tentar evento/cancelamento | Bloqueado antes da chamada |
| Certificado | Certificado valido | Empresa liberada |
| Certificado | Certificado vencido | Empresa bloqueada, fila segue |
| Certificado | Senha errada | Empresa bloqueada, senha nao logada |
| Certificado | CNPJ raiz incompatível | Empresa bloqueada |
| Certificado | Pasta com varios certificados | Usa somente `CERTIFICADO API PN ARQUIVO` |
| Certificado | Alias sem senha no cofre | Empresa bloqueada, fila segue |
| Scheduler | Computador liga 08:00 | Executa `ATRASADA_05H` |
| Scheduler | Computador liga 13:00 | Executa 05:00 e 12:00 em ordem |
| Fila | Processo cai no meio | Retoma sem duplicar |
| Fila | Duas execucoes simultaneas | Lock bloqueia a segunda |
| XML | XML valido | `XML_ARQUIVADO_FINAL` |
| XML | XML malformado | `XML_QUARENTENA` |
| XML | CNPJ divergente | `XML_QUARENTENA` |
| XML | Duplicado exato | Nao sobrescreve, marca duplicado |
| PDF | PDF gerado localmente | `PDF_ARQUIVADO_FINAL` |
| PDF | API DANFSe retorna HTML | Nao salva como PDF final |
| PDF | PDF corrompido | `PDF_QUARENTENA` |
| PDF | PDF faltante | `PENDENTE_OPERACIONAL` |
| API | Timeout | Retentativa e backoff |
| API | 429 | Circuit breaker e proxima janela |
| API | 500/502/503 | Circuit breaker |
| Arquivos | Pasta sem permissao | Empresa em atencao, fila segue |
| Arquivos | Disco cheio | Para antes de corromper |
| Integracao | Pacote XML+PDF completo | RENOMEADOR organiza os dois |
| Integracao | Pacote sem XML | Quarentena/pendencia, nada final |
| Integracao | Pacote sem PDF | `PENDENTE_OPERACIONAL` |
| Integracao | Manifesto diverge do XML/PDF | Quarentena |
| Logs | Dia fechado | JSONL compactado em `.gz` |
| Logs | Retencao vencida | Logs antigos removidos |
| Escala | Muitas empresas | Nenhuma empresa trava todas |
| Escala | Empresa com muitas notas | Lote limitado, continua depois |
| Reconciliacao | Ultimos 7 dias | Faltantes detectados sem duplicar |

## Testes de aceite por fase

### Fase 0

- Revisar endpoints oficiais no dia.
- Confirmar modo somente leitura.
- Confirmar linha tecnica `IMPORT API PN ENTRADA REST` como `SOMENTE ORIGEM`, apontando para `entrada-rest`.
- Confirmar `CAMINHO REST` como destino final por empresa/mes.
- Confirmar que DMS nao usa `SOMENTE ORIGEM` do RENOMEADOR; DMS usa `entrada-dms`/publicador DMS e `CAMINHO DMS`.
- Confirmar que IMPORT API PN nao grava dentro de `PDF/` nem `XML/`.
- Confirmar que o RENOMEADOR organiza XML/PDF em `PDF/...` e `XML/...`.
- Confirmar cofre de certificado.

### Fase 1

- 100% dos cenarios de certificado passam.
- Nenhum segredo aparece em log.
- Empresa invalida nao trava empresa valida.
- Mapa `CNPJ -> CAMINHO DMS` carrega por mes.
- CNPJ duplicado com `CAMINHO DMS` conflitante bloqueia DMS ate revisao.

### Fase 2

- Catch-up comprovado.
- Lock comprovado.
- Reexecucao da mesma janela 10 vezes sem duplicidade.

### Fase 3

- XML valido arquivado.
- XML invalido isolado.
- NSU nao avanca antes do estado recuperavel.
- Reconciliacao encontra faltantes sem duplicar.

### Fase 4

- PDF obrigatorio comprovado.
- XML sem PDF fica pendente operacional.
- PDF falso nunca entra no destino final.

### Fase 5

- Movimento em duas fases comprovado.
- Hash final confere.
- Falha de pasta/rede nao corrompe destino.
- XML/PDF soltos em `entrada-rest` sao aceitos pelo RENOMEADOR.
- RENOMEADOR organiza PDF e XML sem duplicar regras no IMPORT API PN.
- XML Dominio em `entrada-dms` nao entra no RENOMEADOR REST e e publicado no `CAMINHO DMS` pelo publicador DMS.
- XML Dominio de Cliente A nunca cai no `CAMINHO DMS` do Cliente B.
- `CAMINHO DMS` vazio, sem permissao ou de mes ausente vira pendencia no painel, nao publicacao improvisada.

### Fase 6

- Painel mostra OK, pendencias e bloqueios.
- Logs compactam.
- Indice mensal permite buscar por chave.

### Fase 7

- 7 dias modo sombra.
- Backlog controlado.
- Nenhuma duplicidade.
- Pendencias retomadas.

### Fase 8

- 1 empresa piloto em producao assistida.
- Conferencia manual por amostra.
- Depois lote pequeno.

### Fase 9

- Rotina normal com reconciliacao ativa.
- Monitoramento semanal.
- Processo de troca de certificado validado.

## Evidencias para liberar producao

Antes de liberar producao normal, guardar:

- relatorio de modo sombra;
- painel de 7 dias;
- lista de pendencias resolvidas;
- log de teste de queda;
- log de teste de duplicidade;
- amostra XML/PDF conferida manualmente;
- backup do ledger;
- versao dos XSDs usados;
- endpoints oficiais usados;
- data de validade dos certificados.

## Criterio final de liberacao

Liberar producao somente quando:

- zero duplicidade em reexecucao;
- zero arquivo falso como PDF;
- zero nota marcada concluida sem XML e PDF;
- certificados validados;
- painel operacional entendido pelo operador;
- logs e retencao funcionando;
- modo sombra aprovado;
- piloto aprovado.
