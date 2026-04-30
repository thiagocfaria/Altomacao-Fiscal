# MCP e LSP — Avaliacoes e decisoes

Fonte canonica de todas as decisoes sobre ferramentas de analise de codigo neste projeto.

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
| `jdtls-lsp@claude-plugins-official` v1.0.0 | **ativo** (somente neste projeto) | LSP Java (Eclipse JDT.LS); navegacao de tipo, diagnostico, refactor para .java |
| `codebase-memory-mcp` | **ativo** (global, ja instalado e ja usado neste projeto) | Grafo de chamadas e dependencias para orientar navegacao e impacto |
| `rust-analyzer-lsp@claude-plugins-official` | **nao ativo aqui** (global, mas e Rust — sem efeito em .java) | Pertence ao projeto INTERFACE, sem impacto aqui |
| `lemminx-lsp` (XML) | candidato segunda onda | Util para pom.xml e XSD de NFSe; avaliar quando necessario |

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

1. Rastrear `PortalNacionalParser` ate o ponto de uso no `Pipeline`
2. Mapear blast radius ao mexer em `NotaFiscal` (record de domain)
3. Localizar caminho do arquivo do `FolderWatcher` ate o `SafeFileMover`
4. Responder pergunta arquitetural sem abrir arquivos demais

### Regra de promocao

- So vira padrao se vencer em qualidade com clareza.
- Se empatar em qualidade, vence o que abre menos arquivo.
- Se nenhum vencer com clareza, nenhum entra.

---

## Registro de avaliacoes

### jdtls-lsp@claude-plugins-official (2026-04-30)

**Tipo:** plugin oficial do Claude Code
**Escopo:** user (instalado), project (habilitado somente aqui via `.claude/settings.json`)
**Binario:** `/home/u/.local/bin/jdtls` → `/home/u/.local/share/jdtls/bin/jdtls`
**Versao jdtls:** 1.58.0-202604151538

**Checklist de seguranca:**
- Le dados locais do workspace Java (.java, pom.xml): sim
- Usa rede: nao (LSP local)
- Grava indice/cache: sim, workspace do jdtls em `/tmp/jdtls-workspace/` ou similar
- Toca credencial: nao
- Como desligar: remover `"jdtls-lsp@claude-plugins-official": true` de `.claude/settings.json`
- Como limpar: `rm -rf /tmp/jdtls*`; nao afeta arquivos do projeto

**Status de instalacao:**
- Plugin instalado: sim (`claude plugin install jdtls-lsp@claude-plugins-official`)
- Binario jdtls instalado: sim (`/home/u/.local/bin/jdtls` a partir de `/home/u/.local/share/jdtls/`)
- Java 17 disponivel: sim (`/usr/lib/jvm/java-17-openjdk-amd64`)
- Habilitado neste projeto: sim (`.claude/settings.json`)
- Desativado globalmente: sim (settings do usuario tem `"jdtls-lsp": false`)

**Bake-off:** pendente — sera executado quando o codigo Java existir.
**Proximo gatilho de reavaliacao:** primeira sessao com arquivo `.java` real.

---

### codebase-memory-mcp (herdado do global)

**Tipo:** MCP servidor local
**Escopo:** user global (configurado fora deste projeto)
**Status:** ativo globalmente, projeto ja indexado.

**Nota de uso:**
- Reindexar este projeto apos mudancas relevantes de codigo Java
- Usar `index_repository` apontando para a raiz deste repositorio
- O `.cbmignore` deste diretorio exclui os PDFs de amostra do grafo
