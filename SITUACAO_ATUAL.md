# SITUACAO ATUAL

Atualizado em 08/05/2026.

## Visao geral

O projeto foi separado para permitir novos modulos de automacao usando a mesma base de clientes/caminhos.

- `PLANILHA_FISCAL.xlsm` fica na raiz como cadastro compartilhado.
- `RENOMEADOR/` e o modulo responsavel por renomear, separar e organizar PDFs/XMLs de NFS-e.
- O `pom.xml` da raiz e agregador Maven e executa o modulo `RENOMEADOR`.
- `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md` e a documentacao unica do modulo RENOMEADOR.
- `AUDITORIA_RISCOS_RENOMEADOR.md` registra pontos frageis e correcoes recomendadas para producao longa.
- `docs/referencias/planilha/` guarda referencias visuais da planilha compartilhada.
- `docs/operacao/` guarda decisoes operacionais de ferramentas/MCPs.

## Estado do modulo RENOMEADOR

O modulo RENOMEADOR esta como modulo independente V1.4, com documentacao consolidada, suporte a XML do Portal Nacional e pronto para homologacao operacional no Windows antes de producao assistida.

- `RENOMEADOR/AGENTS.md`
- `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`

Resumo do estado:

- batch, watch e config implementados;
- planilha mensal multi-aba importada para YAML;
- roteamento por data de emissao do PDF/XML implementado;
- macro de duplo clique corrigida para todas as abas `CADASTRO ...`;
- batch de producao importa/valida uma vez e usa `--sem-atualizar-planilha`;
- backend tecnico fica fora da REST do cliente;
- ledger e indice de duplicidade ficam particionados por mes;
- batch/watch gravam `backend/painel-operacional.tsv` com `OK` ou `ATENCAO` para facilitar conferencia no Dashboard do Excel;
- watch grava healthcheck em `backend/health/watch-status.json` e faz varredura periodica;
- recuperacao de `PDF/TOMADOR NAO ENCONTRADO` e `XML/TOMADOR NAO ENCONTRADO` respeita o mes de emissao antes de mover pendencia;
- movimentacao sem `ATOMIC_MOVE` seguro usa copia verificada antes de apagar a origem;
- `empresas.yaml` rejeita campos desconhecidos para impedir erro de digitacao silencioso;
- `backendRoot` no YAML fixa o backend tecnico oficial para logs, indices, painel, healthcheck e lock;
- PDFs acima de 50MB ou 80 paginas caem em revisao antes de extracao textual pesada;
- XMLs do Portal Nacional tambem sao lidos, renomeados e enviados para `XML/...` dentro do `CAMINHO REST`;
- batch/watch usam executor limitado para timeout, sem crescimento livre de threads;
- logs TSV, ledger e indice escapam campos e isolam linhas corrompidas em `.corrompidas`;
- manutencao tecnica limpa `split-work/`, compacta logs e gera relatorio de `revisar/`;
- release Windows possui rotina com dependency tree, integracao, package e teste do JAR;
- caminhos relativos inseguros e `backendRoot` dentro da REST sao recusados;
- suite de regressao exige PDFs reais/extremos minimos;
- documentacoes antigas de desenvolvimento foram removidas do modulo.

Validacao atual:

- `mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR test`: 148 testes, 0 falhas.
- `mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration`: 148 testes unitarios + 1 teste de integracao, 0 falhas.

## Proximo passo geral

Antes de iniciar outro modulo, o RENOMEADOR fica como referencia fechada. Para operacao real, ainda precisa apenas da validacao no Windows/Excel oficial:

1. importar `PLANILHA_FISCAL.xlsm` para `RENOMEADOR/operacao/empresas.yaml`;
2. validar config;
3. rodar batch em homologacao;
4. conferir saidas/logs;
5. rodar batch real.

Novo modulo deve nascer em nova pasta propria, usando `PLANILHA_FISCAL.xlsm` como base compartilhada quando fizer sentido, sem misturar codigo dentro de `RENOMEADOR/`.
