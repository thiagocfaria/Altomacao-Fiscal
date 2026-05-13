# MCP e LSP — Avaliacoes e decisoes

Fonte canonica de todas as decisoes sobre ferramentas de analise de codigo neste projeto.

Para reproduzir o ambiente em outra maquina, use tambem `docs/operacao/AMBIENTE_SKILLS_MCP_LSP.md`.

Regra:

```text
Primeiro registrar e aprovar aqui.
So depois materializar em .claude/settings.json.
Se houver conflito entre este doc e a config, este doc manda.
```

---

## Estado atual do baseline (2026-04-30)

| Ferramenta | Estado | Motivo |
|---|---|---|
| `jdtls-lsp@claude-plugins-official` v1.0.0 | **operacional com JDK 21 local** (somente neste projeto) | LSP Java (Eclipse JDT.LS); navegacao de tipo, diagnostico, refactor para .java |
| `codebase-memory-mcp` | **operacional no chat codex-tulio; confirmar por sessao** (global) | Grafo de chamadas e dependencias para orientar navegacao e impacto |
| `rust-analyzer-lsp@claude-plugins-official` | **nao ativo aqui** (global, mas e Rust — sem efeito em .java) | Pertence ao projeto INTERFACE, sem impacto aqui |
| `lemminx-lsp` (XML) | **candidato prioritario para IMPORT API PN** | XML/XSD de NFS-e virou parte central do projeto; avaliar antes de implementar validacao XML e geracao/organizacao XML+PDF |

---

## Isolamento com o projeto INTERFACE

```text
jdtls-lsp: desativado GLOBALMENTE, ativo APENAS neste projeto via .claude/settings.json
rust-analyzer-lsp: ativo GLOBALMENTE, mas Java LSP e Rust LSP sao ortogonais — sem conflito
codebase-memory-mcp: global, mas cada projeto e indexado separadamente — sem conflito de grafo
```

O INTERFACE usa `rust-analyzer-lsp` para Rust. Este projeto usa `jdtls-lsp` para Java. Os dois LSPs sao ativados por extensao de arquivo (`.rs` e `.java`) — coexistem sem conflito.

---

## Como promover um MCP ou LSP para o baseline

### Passo 1: checklist de seguranca

| Item | Resposta |
|---|---|
| Que dados locais ele le? | |
| Usa rede? Para onde fala? | |
| Onde grava indice, cache ou log? | |
| Toca credencial, segredo, CNPJ de empresa? | |
| Como desligar no Claude Code? | |
| Como limpar o que criou? | |
| Como voltar ao estado anterior? | |

### Passo 2: bake-off com tarefas reais do projeto NFSe

Use sempre as mesmas 4 tarefas de referencia:

1. Rastrear `PortalNacionalParser` ate o ponto de uso em `InvoiceProcessingPipeline`
2. Mapear blast radius ao mexer em `InvoiceData`
3. Localizar o caminho do arquivo desde `WatchModeRunner`/`WatchFolderProcessor` ate `DestinationService`
4. Responder pergunta arquitetural sem abrir arquivos demais

### Regra de promocao

- So vira padrao se vencer em qualidade com clareza.
- Se empatar em qualidade, vence o que abre menos arquivo.
- Se nenhum vencer com clareza, nenhum entra.

---

## Registro de avaliacoes

### lemminx-lsp XML (pendente prioritario para IMPORT API PN)

**Tipo:** LSP XML local
**Escopo proposto:** projeto
**Uso previsto:** navegar e validar XML/XSD de NFS-e, manifests XML quando existirem, `pom.xml` e contratos XML do pacote XML+PDF.

**Motivo da prioridade:** o novo fluxo `IMPORT API PN -> RENOMEADOR` passa a depender de XML oficial de NFS-e, XSDs versionados e organizacao conjunta XML+PDF. O projeto ainda nao deve ativar a ferramenta sem bake-off, mas a avaliacao deixou de ser opcional distante.

**Checklist de seguranca preliminar:**
- Le dados locais: XML, XSD, `pom.xml` e documentos do workspace.
- Usa rede: deve ser avaliado antes de instalar/ativar; preferencia por uso local sem rede.
- Onde grava indice/cache/log: confirmar no bake-off.
- Toca credencial, segredo, CNPJ de empresa: pode ler XMLs e exemplos com CNPJ; nao deve indexar certificados, senhas nem pastas operacionais reais.
- Como desligar: remover entrada do LSP na configuracao do projeto.
- Como limpar: limpar cache/workspace do LSP apos identificar caminho.

**Bake-off especifico antes de promover:**
1. Validar um XML NFS-e contra XSD oficial versionado.
2. Navegar de um XSD para tipos referenciados sem abrir arquivos demais.
3. Detectar XML malformado em fixture local.
4. Confirmar que certificados, senhas, logs e XMLs reais operacionais ficam fora do escopo/indexacao.

**Decisao atual:** nao ativado ainda; avaliar antes da Fase 3 do IMPORT API PN.

---

## Skills do projeto

Estado em 11/05/2026:

- Nao ha skill local nova aprovada para `IMPORT API PN` ou `RENOMEADOR`.
- As regras atuais sao especificas deste projeto e devem continuar nos documentos do modulo e no `AGENTS.md`/documentacao do RENOMEADOR.
- Criar skill so fara sentido depois que o fluxo XML+PDF estiver implementado, testado e repetitivo.
- Neste chat `codex-thiago`, as skills de sistema do Codex e as skills `superpowers` estao disponiveis; elas sao ambiente de desenvolvimento, nao regra versionada do projeto.

Possiveis skills futuras, se o fluxo se estabilizar:

- `nfse-api-seguranca-certificado`: uso seguro de certificado real e modo somente leitura.
- `nfse-pacote-xml-pdf`: contrato entre IMPORT API PN e RENOMEADOR.
- `nfse-homologacao-producao`: execucao de modo sombra, piloto e liberacao.

Decisao atual: nao criar agora, para evitar duplicar regra ainda em desenho.

### Apache POI `poi-ooxml` (2026-05-05)

**Tipo:** dependencia Maven da aplicacao Java
**Escopo:** projeto
**Uso previsto:** ler planilhas `.xlsx` e `.xlsm` como fonte de cadastro de empresas, sem executar macros, para gerar `empresas.yaml` validado.

**Checklist de seguranca:**
- Le dados locais: sim, somente a planilha informada via CLI.
- Usa rede: nao em runtime; Maven usa rede apenas para baixar dependencia no build.
- Onde grava indice/cache/log: nao grava indice proprio; o importador grava apenas o YAML de saida escolhido pelo operador.
- Toca credencial, segredo, CNPJ de empresa: sim, le CNPJ e caminhos de pastas da planilha; logs nao devem despejar conteudo integral da planilha.
- Como desligar: remover comandos de importacao Excel e dependencia `org.apache.poi:poi-ooxml` do `RENOMEADOR/pom.xml`.
- Como limpar o que criou: apagar YAML gerado pelo operador.
- Como voltar ao estado anterior: usar `empresas.yaml` manual existente; `batch` e `watch` continuam lendo YAML.

**Decisao:** aprovado para V1.1, porque a planilha nao fica no caminho quente do watcher. O Excel e importado para YAML validado; depois o watcher roda somente sobre a configuracao estavel.

**Observacao de logging:** `org.apache.logging.log4j:log4j-to-slf4j` foi incluido na mesma decisao para rotear logs internos do Apache POI ao Logback ja usado pelo projeto, sem adicionar novo destino de log.

### jdtls-lsp@claude-plugins-official (2026-04-30)

**Tipo:** plugin oficial do Claude Code
**Escopo:** user (instalado), project (habilitado somente aqui via `.claude/settings.json`)
**Binario:** `/home/u/.local/bin/jdtls` → `/home/u/.local/share/jdtls/bin/jdtls`
**Versao jdtls:** 1.58.0-202604151538

**Checklist de seguranca:**
- Le dados locais do workspace Java (.java, `RENOMEADOR/pom.xml`): sim
- Usa rede: nao (LSP local)
- Grava indice/cache: sim, workspace do jdtls em `/tmp/jdtls-workspace/` ou similar
- Toca credencial: nao
- Como desligar: remover `"jdtls-lsp@claude-plugins-official": true` de `.claude/settings.json`
- Como limpar: `rm -rf /tmp/jdtls*`; nao afeta arquivos do projeto

**Status de instalacao verificado em 10/05/2026:**
- Plugin instalado: sim (`claude plugin install jdtls-lsp@claude-plugins-official`)
- Binario jdtls instalado: sim (`/home/u/.local/bin/jdtls` a partir de `/home/u/.local/share/jdtls/`)
- Java 17 disponivel: sim (`openjdk version "17.0.18"`)
- Java 21 disponivel para o `jdtls`: sim, via JDK local em `/home/u/.local/share/jdks/jdk-21-local/usr/lib/jvm/java-21-openjdk-amd64`
- Habilitado neste projeto: sim (`.claude/settings.json`)
- Desativado globalmente: sim (settings do usuario tem `"jdtls-lsp": false`)

**Status operacional atual:** operacional com ressalva. O wrapper
`/home/u/.local/bin/jdtls` define `JAVA_HOME` para o JDK 21 local e usa diretorios em
`/tmp` para configuracao/dados do servidor. O `java` global continua Java 17 para
Maven/comandos do projeto. Em 12/05/2026, `jdtls --version` iniciou o servidor e nao
encerrou sozinho nesta sessao Codex; para evitar processo pendurado, ele foi encerrado
manualmente. Tratar como LSP disponivel para navegacao, mas nao usar esse comando como
check automatizado sem `timeout`.

**Bake-off:** concluido para MCP/codebase-memory em momento anterior. Reavaliar LSP Java em mudanca relevante de versao.
**Proximo gatilho de reavaliacao:** mudanca relevante de arquitetura, troca de versao do jdtls ou falha recorrente do LSP/MCP.

---

### codebase-memory-mcp (herdado do global)

**Tipo:** MCP servidor local
**Escopo:** user global (configurado fora deste projeto)
**Status:** configurado globalmente; operacional no chat `codex-tulio` verificado em
12/05/2026. Confirmar por sessao com `index_status` antes de confiar.

**Status de sessao Codex verificado em 11/05/2026:**
- Config global Codex: `/home/u/.codex/config.toml` contem `mcp_servers.codebase-memory-mcp`.
- Config desta conta Codex: `/home/u/.codex-contas/thiago/config.toml` confia neste projeto, mas nao declara MCP proprio.
- Recursos MCP expostos na sessao: `list_mcp_resources` e `list_mcp_resource_templates` retornaram vazio. Isso e esperado para servidores que expoem ferramentas, mas nao prova que as ferramentas estejam usaveis pelo chat.
- `codebase-memory-mcp cli list_projects` lista este projeto, porem com `nodes=0` e `edges=0`.
- Tentativa de `index_repository` em 11/05/2026, apos ajustar `.cbmignore`, descobriu 182 arquivos e extraiu 2474 nos/7548 arestas, mas terminou com `status:error` na fase `dump`.

**Status de sessao Codex verificado em 12/05/2026 no `codex-tulio`:**

- `CODEX_HOME=/home/u/.codex-contas/tulio codex mcp list` mostra
  `codebase-memory-mcp` como `enabled`.
- `index_status` do MCP da sessao mostra este projeto com `status=ready`,
  `nodes=2676` e `edges=8704`.
- `search_graph` localizou simbolos reais em `IMPORT API PN/` e `RENOMEADOR/`.
- O CLI direto do `codebase-memory-mcp` pode retornar estado vazio/stale; para trabalho
  dentro do chat, valide pelo MCP exposto na propria sessao.

**Status operacional atual:** o MCP deve ser usado para orientacao de grafo quando
`index_status` da sessao estiver `ready`. Continue confirmando decisoes de comportamento
com codigo, `rg` e testes Maven.

**Status de sessao Codex verificado em 12/05/2026 neste chat:**

- `list_projects` mostrou este projeto com `nodes=2902` e `edges=9618`.
- `index_status` respondeu `status=ready`.
- `search_graph` localizou o fluxo atual do `VERIFICAR TUDO`, incluindo
  `PrevooVerificarTudo`, `SimuladorReconciliacaoDryRun`,
  `ReconciliadorPortalDestino` e `painel.comando_verificar_tudo`.

**Nota de uso:**
- Reindexar este projeto apos mudancas relevantes de codigo Java
- Usar `index_repository` apontando para a raiz deste repositorio
- O `.cbmignore` deste diretorio exclui PDFs de amostra, builds, caches, planilhas e artefatos operacionais do grafo
