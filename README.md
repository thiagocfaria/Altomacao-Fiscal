# ALTOMACAO Fiscal

Projeto de automacoes fiscais separado por modulos.

## Estrutura

| Caminho | Funcao |
|---|---|
| `PLANILHA_FISCAL.xlsm` | cadastro compartilhado de clientes, certificados e caminhos |
| `RENOMEADOR/` | modulo de renomear, separar e organizar PDFs/XMLs de NFS-e |
| `IMPORT API PN/` | modulo de consultar Portal Nacional/ADN e publicar XML/PDF/DMS |
| `docs/operacao/` | decisoes sobre ferramentas, MCPs e LSPs |
| `painel.py` | painel local para verificar, ligar, reconciliar e desligar o fluxo |

## RENOMEADOR

O modulo atual de producao fica em `RENOMEADOR/`.

Documentacao unica do modulo:

```text
RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md
```

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

Painel local a partir da raiz:

```bash
python3 painel.py
```

No PowerShell, a partir da raiz do projeto:

```powershell
mvn package
python3 .\painel.py
# ou, se o Windows tiver apenas o Python Launcher:
py -3 .\painel.py
```

No PowerShell, a partir de qualquer pasta, chame o launcher pelo caminho completo:

```powershell
cd "C:\caminho\para\Altomacao-Fiscal"
mvn package
& "C:\caminho\para\Altomacao-Fiscal\rodar_painel.ps1"
```

Observacao: `python3 painel.py` e um comando relativo; ele so encontra o arquivo se o
PowerShell estiver na pasta onde `painel.py` esta. Para abrir de qualquer lugar, use o
launcher acima ou informe o caminho completo do `painel.py`.

Variaveis opcionais do painel:

```bash
ALTOMACAO_ROOT="/caminho/do/projeto" \
ALTOMACAO_AMBIENTE=PRODUCAO \
ALTOMACAO_INTERVALO_SEGUNDOS=60 \
ALTOMACAO_MAX_LOTES_RECONCILIACAO=500 \
python3 painel.py
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

## Desenvolvimento assistido por agente

Antes de codar com Codex/Claude, leia:

```text
AGENTS.md
docs/operacao/CODING_AGENTES.md
docs/operacao/ARQUITETURA_PROFISSIONAL.md
```

Os guias registram a ordem de leitura, os gatilhos automaticos de skills/MCP, o estado
real de MCP/LSP e os comandos de validacao.
