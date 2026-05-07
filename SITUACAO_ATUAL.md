# SITUACAO ATUAL

Atualizado em 07/05/2026.

## Visao geral

O projeto foi separado para permitir novos modulos de automacao usando a mesma base de clientes/caminhos.

- `PLANILHA_FISCAL.xlsm` fica na raiz como cadastro compartilhado.
- `RENOMEADOR/` e o modulo responsavel por renomear, separar e organizar PDFs de NFS-e.
- O `pom.xml` da raiz e agregador Maven e executa o modulo `RENOMEADOR`.
- `docs/referencias/planilha/` guarda referencias visuais da planilha compartilhada.
- `docs/operacao/` guarda decisoes operacionais de ferramentas/MCPs.

## Estado do modulo RENOMEADOR

O modulo RENOMEADOR esta em V1.3 de limpeza operacional e controle de armazenamento. O detalhe tecnico e operacional fica em:

- `RENOMEADOR/AGENTS.md`
- `RENOMEADOR/SITUACAO_ATUAL.md`
- `RENOMEADOR/ESPECIFICACAO_RENOMEADOR_NFSE.md`

Validacao atual apos a separacao em modulo:

- `mvn -Dmaven.repo.local=/tmp/m2-nfse test`: 119 testes, 0 falhas.
- `mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration`: 119 testes unitarios + 1 teste de integracao, 0 falhas.

## Proximo passo geral

Fechar a reorganizacao fisica do modulo `RENOMEADOR/`, validar Maven/scripts e usar producao inicial em batch conferido:

1. importar `PLANILHA_FISCAL.xlsm` para `RENOMEADOR/operacao/empresas.yaml`;
2. validar config;
3. rodar batch em homologacao;
4. conferir saidas/logs;
5. rodar batch real.
