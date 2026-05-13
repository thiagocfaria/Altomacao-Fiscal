# AGENTS.md

Verdades duraveis do modulo `IMPORT API PN`.

## Ordem de leitura

1. leia `../REGRAS_INVARIANTES.md`
2. leia `../AGENTS.md`
3. leia este `AGENTS.md`
4. leia `../SITUACAO_ATUAL.md`
5. leia `../CORRECAO_ARQUITETURA.md`
6. se a tarefa tocar geracao local de PDF, leia `PLANO_GERADOR_DANFSE_LOCAL.md`

## Papel do modulo

`IMPORT API PN/` consulta o Portal Nacional/ADN, valida cadastro/certificado,
registra auditoria tecnica e publica XML/PDF para fluxos de destino:

- `IMPORT API PN/backend/entrada-rest` para o RENOMEADOR organizar na REST do cliente;
- `CAMINHO DMS`, pelo publicador DMS direto durante a reconciliacao;
- ledger tecnico apenas para auditoria e controle operacional.

## Regra invariante principal

O Portal Nacional + pastas destino do cliente sao a fonte de verdade.

O ledger interno deste modulo nao decide sozinho que uma nota deve ser ignorada. Se o
XML/PDF/DMS sumiu do destino do cliente, o fluxo deve recompor a partir do Portal na
proxima reconciliacao. Antes de mexer em `RegistroConsultaAdn`, `RepositorioImportacao`,
`ChavesPresentesNoDestino`, `PublicadorRestEntrada`, `PublicadorDmsDireto` ou comandos de
captura/reconciliacao, leia `../REGRAS_INVARIANTES.md`.

## Fronteiras de pacote

```
src/main/java/br/com/nfse/importadorpn/
|-- agenda/        -> janelas 05:00/12:00/17:00 e controle de execucao
|-- certificado/   -> validacao e contexto SSL de certificado
|-- configuracao/  -> leitura da PLANILHA_FISCAL.xlsm e cadastro
|-- execucao/      -> lock/bloqueio de execucao
|-- fila/          -> modelagem de fila/importacao planejada
|-- ledger/        -> auditoria tecnica, nunca fonte unica de verdade
|-- manutencao/    -> limpeza/retencao operacional
|-- portal/        -> ADN, DANFSe, NSU, JSON LoteDFe e reconciliacao
`-- publicacao/    -> entrada REST, DMS e artefatos publicados
```

Regras de fronteira:

- `configuracao/` le cadastro, mas nao chama Portal.
- `certificado/` nao conhece regra fiscal nem pasta de cliente.
- `portal/` consulta/extrai/decide com base no Portal e destino real, mas nao deve espalhar escrita em pastas sem passar por `publicacao/`.
- `ledger/` registra historico; nao pode bloquear recomposicao quando destino real esta ausente.
- `publicacao/` escreve artefatos tecnicos/operacionais e deve validar conteudo minimo antes de publicar (`%PDF`, XML presente, caminho seguro).
- `AppImportadorPn` orquestra CLI; nao duplicar regra de dominio dentro do parser de argumentos.

## Regras de seguranca

- Cliente ADN permanece em modo somente leitura; metodos que alterem estado fiscal devem continuar bloqueados.
- Nao commitar certificado, senha, XML/PDF real, ledger, backend ou respostas reais do ADN.
- Nao logar senha de certificado, conteudo integral de XML real, token, nem caminho externo sensivel alem do necessario para diagnostico.
- Dependencia nova, MCP, LSP ou ferramenta fiscal nova passa primeiro por `../docs/operacao/MCP_AVALIACAO.md`.
- Geracao local de DANFSe nao pode fingir ser documento oficial assinado pelo Portal.

## Validacao

Comandos a partir da raiz:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl "IMPORT API PN" test
mvn -Dmaven.repo.local=/tmp/m2-nfse test
```

Quando a mudanca afetar o contrato com o RENOMEADOR, validar tambem:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
```

Mudancas em reconciliacao, ultimo NSU, DMS, publicacao XML/PDF ou recomposicao de arquivo
apagado precisam de teste que prove o comportamento conservador.
