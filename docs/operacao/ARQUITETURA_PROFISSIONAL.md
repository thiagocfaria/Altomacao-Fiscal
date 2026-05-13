# Arquitetura Profissional

Guia curto para manter o projeto ALTOMACAO Fiscal simples, previsivel e seguro.

## Objetivo

O sistema deve ser facil de operar localmente, facil de auditar e resistente a erro
manual em pastas de cliente. Codigo novo deve preservar as fronteiras dos modulos,
reduzir acoplamento e vir com validacao proporcional ao risco.

## Fonte de verdade

- Portal Nacional/ADN informa quais notas existem.
- Pastas destino do cliente, configuradas na `PLANILHA_FISCAL.xlsm`, informam o que ja
  foi entregue.
- Ledger, indices, caches e logs sao auditoria/otimizacao. Nunca podem bloquear sozinhos
  a recomposicao de XML/PDF/DMS apagado no destino.
- Antes de mexer em roteamento, ledger, duplicidade, DMS, entrada REST ou reconciliacao,
  leia `REGRAS_INVARIANTES.md`.

## Fronteiras dos modulos

| Modulo | Responsabilidade | Nao deve fazer |
|---|---|---|
| `PLANILHA_FISCAL.xlsm` | cadastro compartilhado de clientes, caminhos e certificados | virar arquivo interno de um modulo |
| `RENOMEADOR/` | organizar XML/PDF de NFS-e na REST do cliente | consultar Portal Nacional/ADN |
| `IMPORT API PN/` | consultar ADN, baixar/publicar XML/PDF/DMS e reconciliar destino | organizar REST final sem passar pela fronteira combinada |
| `painel.py` | operar fluxo local: verificar, ligar, testar, reconciliar e desligar | concentrar regra fiscal profunda |
| `docs/operacao/` | mapas, decisoes de ferramenta e orientacao para agentes | guardar segredo, log real ou dado operacional |

`entrada-rest` e a fronteira tecnica entre importacao e organizacao REST.

## Padrao de codigo

- Prefira classes pequenas e servicos com uma responsabilidade clara.
- Extraia apenas quando reduzir complexidade real ou proteger uma regra importante.
- Nao faca reescrita grande sem teste de comportamento antes.
- Nao misture codigo de novo modulo dentro de `RENOMEADOR/`.
- Nao crie arquivo operacional, backend, ledger ou log na raiz.
- Para performance, evite varrer diretorios grandes repetidamente sem necessidade; quando
  usar cache/indice, trate como otimizacao e sempre confirme o estado real do disco quando
  a regra invariante exigir.

## Trabalho com agentes

O usuario nao precisa pedir MCP, LSP, skill ou ferramenta. O agente deve acionar
automaticamente o que for aplicavel.

Use `codebase-memory-mcp` automaticamente quando:

- a tarefa pedir alteracao de codigo Java;
- o usuario disser "use o MCP", "use codebase memory" ou equivalente;
- for preciso entender todo o sistema, um modulo inteiro ou o caminho completo de um fluxo;
- for necessario entender impacto, chamadas, dependencias ou arquitetura;
- houver pergunta sobre onde uma regra esta implementada;
- for refatoracao, limpeza, performance, qualidade, bug, teste, comportamento estranho
  ou mudanca em modulo compartilhado;
- houver risco de alterar contrato entre `IMPORT API PN/`, `RENOMEADOR/`, `painel.py`,
  planilha, DMS ou entrada REST.

Nao use MCP por reflexo quando:

- a tarefa for apenas editar texto conhecido;
- o estado do MCP nao estiver `ready`;
- a pergunta for simples e a leitura direta de um arquivo resolver com menos custo.

Mesmo com MCP pronto, confirme comportamento com codigo, `rg` e testes Maven antes de
afirmar que algo esta correto.

## Validacao

Escolha a menor validacao que prove a mudanca e suba a escada conforme o risco:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" test
python3 -m py_compile painel.py
```

Antes de declarar pronto, registre o que foi verificado e qualquer limite restante.
