# ALTOMACAO Fiscal

Projeto de automacoes fiscais separado por modulos.

## Estrutura

| Caminho | Funcao |
|---|---|
| `PLANILHA_FISCAL.xlsm` | cadastro compartilhado de clientes, certificados e caminhos |
| `RENOMEADOR/` | modulo de renomear, separar e organizar PDFs de NFS-e |
| `docs/referencias/planilha/` | referencias visuais da planilha compartilhada |
| `docs/operacao/` | decisoes sobre ferramentas, MCPs e LSPs |

## RENOMEADOR

O modulo atual de producao fica em `RENOMEADOR/`.

Comandos principais a partir da raiz:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse verify -Pintegration
mvn -f RENOMEADOR/pom.xml -Dmaven.repo.local=/tmp/m2-nfse package
java -jar RENOMEADOR/target/renomeador-nfse-0.1.0-SNAPSHOT.jar --help
```

No Windows, use os scripts em:

```text
RENOMEADOR\scripts\windows\
```

## Operacao

Para producao inicial do renomeador, use batch conferido:

1. abra `PLANILHA_FISCAL.xlsm` no Excel oficial e salve;
2. compile o modulo;
3. importe a planilha para `RENOMEADOR\operacao\empresas.yaml`;
4. rode `config check`;
5. rode batch em homologacao;
6. confira logs e saidas;
7. rode batch real.

Dados operacionais, logs, backend e YAML gerado nao devem entrar no Git.
