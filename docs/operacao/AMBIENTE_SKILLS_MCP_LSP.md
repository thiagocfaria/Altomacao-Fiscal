# Ambiente de skills, MCP e LSP

Documento operacional para reproduzir em outra maquina o mesmo ambiente de apoio usado neste projeto.

## Regra principal

```text
O repositorio guarda as regras duraveis do projeto.
Skills, MCPs e LSPs instalados no usuario devem ser tratados como ambiente de trabalho.
Nao colocar senha, certificado, token, cache, log operacional ou pasta real de cliente em configuracao versionada.
```

## Arquivos versionados que importam

| Arquivo | Funcao |
|---|---|
| `.claude/settings.json` | habilita plugins/LSPs aprovados somente para este projeto |
| `AGENTS.md` | ordem de leitura e regras duraveis da raiz |
| `docs/operacao/ARQUITETURA_PROFISSIONAL.md` | principios atuais de arquitetura, performance e uso automatico de ferramentas |
| `RENOMEADOR/AGENTS.md` | regras duraveis do modulo RENOMEADOR |
| `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md` | documentacao tecnica e operacional do RENOMEADOR |
| `docs/operacao/MCP_AVALIACAO.md` | decisoes de MCP/LSP e criterios para promover novas ferramentas |
| `docs/operacao/AMBIENTE_SKILLS_MCP_LSP.md` | como reproduzir o ambiente em outra maquina |

O arquivo `.claude/settings.local.json` nao e versionado. Ele guarda permissoes locais daquela maquina e nao deve virar baseline do projeto.

## Estado atual dos LSPs

| LSP/plugin | Estado | Escopo | Uso no projeto |
|---|---|---|---|
| `jdtls-lsp@claude-plugins-official` | operacional com JDK 21 local para o wrapper `jdtls` | projeto | Java: navegacao, diagnostico, tipos e refactors em `.java` |
| `lemminx-lsp` | candidato | ainda nao ativo | XML/XSD de NFS-e; avaliar antes de validar XML oficialmente |
| `rust-analyzer-lsp@claude-plugins-official` | nao usado aqui | global em outro projeto | Sem efeito neste repositorio Java |

Configuracao versionada atual:

```json
{
  "enabledPlugins": {
    "jdtls-lsp@claude-plugins-official": true
  }
}
```

## Estado atual dos MCPs

| MCP | Estado | Escopo | Uso no projeto |
|---|---|---|---|
| `codebase-memory-mcp` | operacional neste chat `codex-tulio`; confirmar com `index_status` em sessao nova | usuario | grafo de chamadas/dependencias para navegar impacto no codigo |

O `codebase-memory-mcp` nao esta configurado dentro do repositorio. Ele e configuracao global do usuario/agente e precisa ser instalado/configurado na outra maquina antes de indexar este repositorio.

Registro anterior de 10/05/2026: o projeto `srv-DocumentosCompartilhados-ALTOMACAO IMPORTACAO DE NF ` estava documentado como indexado no `codebase-memory-mcp` com status `ready`.

Status verificado em 10/05/2026 para LSP Java: o binario `/home/u/.local/bin/jdtls` existe, mas `jdtls --version` falha com `jdtls requires at least Java 21`. O ambiente atual responde `openjdk version "17.0.18"`. Portanto, o LSP Java nao deve ser tratado como operacional ate instalar/configurar Java 21 para o `jdtls`.

Status verificado em 12/05/2026 para LSP Java no `codex-tulio`:

- JDK 21 local extraido em `/home/u/.local/share/jdks/jdk-21-local/usr/lib/jvm/java-21-openjdk-amd64`.
- `/home/u/.local/bin/jdtls` define `JAVA_HOME` para esse JDK 21 somente no processo do
  `jdtls`, sem trocar o `java` global usado por Maven/comandos do projeto.
- O wrapper tambem usa diretorios gravaveis em `/tmp/jdtls-configuration-*` e
  `/tmp/jdtls-data-*`, evitando escrita em `/home/u/.eclipse`.
- `jdtls` inicia com Java 21. Em sandbox sem rede, pode registrar aviso de Gradle/Buildship
  tentando baixar metadados; isso nao bloqueia o LSP para este projeto Maven.
- Nesta sessao, `jdtls --version` iniciou o servidor e nao encerrou sozinho. Use `timeout`
  se precisar checar por comando; nao deixe processo de LSP pendurado.

Status verificado em 11/05/2026 neste chat `codex-thiago`:

- `/home/u/.codex/config.toml` contem `mcp_servers.codebase-memory-mcp` apontando para `/home/u/.local/bin/codebase-memory-mcp`.
- `/home/u/.codex-contas/thiago/config.toml` marca este projeto como `trusted`, mas nao declara MCP proprio.
- O MCP conecta como servidor de ferramentas, mas `list_mcp_resources` e `list_mcp_resource_templates` retornam vazio porque ele nao declara resources/templates.
- Pelo CLI, `list_projects` lista o projeto, mas com `nodes=0` e `edges=0`.
- Reindexacao em 11/05/2026, apos ajuste de `.cbmignore`, chegou a extrair `2474` nos e `7548` arestas, mas terminou com `status:error` na fase `dump`. Ate corrigir isso, usar `rg`/Maven como fonte principal de navegacao e tratar o MCP como indisponivel para analise de impacto.

Status verificado em 12/05/2026 neste chat `codex-tulio`:

- `/home/u/.codex-contas/tulio/config.toml` contem `mcp_servers.codebase-memory-mcp`
  apontando para `/home/u/.local/bin/codebase-memory-mcp`.
- `codex mcp list` com `CODEX_HOME=/home/u/.codex-contas/tulio` lista
  `codebase-memory-mcp` como `enabled`.
- A ferramenta MCP da sessao respondeu `index_status` para este projeto com
  `status=ready`, `nodes=2676` e `edges=8704`.
- `search_graph` encontrou simbolos reais do projeto, como `AppImportadorPn.reconciliarCadastro`
  e `InvoiceProcessingPipeline.destinoExisteNoDisco`.
- O CLI direto `/home/u/.local/bin/codebase-memory-mcp cli list_projects` ainda pode mostrar
  um estado vazio/stale; em chats Codex, confirme pelo proprio MCP da sessao antes de decidir.

Status verificado em 12/05/2026 nesta sessao:

- `list_projects` mostrou este projeto com `nodes=2902` e `edges=9618`.
- `index_status` respondeu `ready`.
- O MCP localizou o fluxo `VERIFICAR TUDO` atual: `PrevooVerificarTudo`,
  `SimuladorReconciliacaoDryRun`, `ReconciliadorPortalDestino` e `painel.py`.

Ao mudar bastante o codigo Java, reindexar o repositorio no `codebase-memory-mcp` apontando para a raiz:

```text
/srv/DocumentosCompartilhados/ALTOMACAO IMPORTACAO DE NF
```

## Skills do projeto

Nao ha skill local nova aprovada para `IMPORT API PN` ou `RENOMEADOR` neste momento.

As antigas skills locais de `RENOMEADOR/.claude/skills/` foram substituidas por documentacao unica e regras em:

- `AGENTS.md`
- `SITUACAO_ATUAL.md`
- `docs/operacao/ARQUITETURA_PROFISSIONAL.md`
- `REGRAS_INVARIANTES.md`
- `RENOMEADOR/AGENTS.md`
- `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`
- `IMPORT API PN/AGENTS.md`

Motivo: ainda estamos consolidando o fluxo XML+PDF. Criar skills agora duplicaria regra que ainda esta sendo refinada.

O usuario nao precisa chamar skill ou MCP manualmente. Em chats Codex/Claude, o agente
deve usar automaticamente as skills por gatilho e o `codebase-memory-mcp` quando houver
ganho real de navegacao de codigo/impacto: codigo Java, entendimento amplo do sistema,
arquitetura, refatoracao, limpeza, performance, qualidade, bugs, testes, regras
implementadas no codigo ou pedido explicito de "use o MCP".

## Skills operacionais usadas nesta entrega

Estas skills pertencem ao ambiente do agente, nao ao codigo de producao:

| Skill | Uso |
|---|---|
| `superpowers:using-superpowers` | garante verificacao das skills aplicaveis antes de agir |
| `superpowers:writing-skills` | revisao de quando criar, alterar ou evitar skills |
| `superpowers:verification-before-completion` | exige validacao real antes de declarar trabalho concluido |
| Skills `.system` do Codex | `imagegen`, `openai-docs`, `plugin-creator`, `skill-creator`, `skill-installer` |

Elas nao precisam existir dentro deste repositorio para o sistema Java rodar. So sao relevantes se a outra maquina tambem for usada para desenvolvimento assistido por agente.

Status verificado em 11/05/2026 neste chat `codex-thiago`: as skills de sistema do Codex e as skills `superpowers` estao disponiveis na sessao. Nao ha skill local versionada do projeto, por decisao consciente: as regras de NFS-e ainda pertencem a `AGENTS.md`, documentos do modulo e planos operacionais ate o fluxo XML+PDF estabilizar.

## Como configurar outra maquina

1. Clonar o repositorio.
2. Instalar Java 17, Maven e Git.
3. Confirmar que `.claude/settings.json` veio com o repositorio.
4. Instalar o plugin `jdtls-lsp@claude-plugins-official` no ambiente do Claude Code, se for usar Claude Code nessa maquina.
5. Confirmar que o binario `jdtls` esta disponivel no ambiente do usuario.
6. Confirmar que o `jdtls` esta apontando para Java 21 ou superior.
7. Instalar/configurar `codebase-memory-mcp` globalmente, se quiser a mesma navegacao por grafo.
8. Indexar a raiz deste repositorio no `codebase-memory-mcp`.
9. Confirmar que `list_projects` mostra `nodes` e `edges` maiores que zero e que `search_code`/`search_graph` respondem para uma classe real do projeto.
10. Nao copiar certificados digitais, senhas, XMLs reais, PDFs reais, logs ou pastas operacionais para dentro do Git.
11. Rodar a validacao do modulo antes de trabalhar:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
```

## Como conferir se esta igual

Checklist rapido:

- `.claude/settings.json` contem `jdtls-lsp@claude-plugins-official: true`.
- `RENOMEADOR` compila e passa nos testes.
- `jdtls` inicia sem erro `requires at least Java 21`.
- `codebase-memory-mcp`, se usado, foi reindexado depois do clone e `index_status`
  no chat mostra `ready` com `nodes`/`edges` maiores que zero.
- `.claude/settings.local.json` continua local e ignorado pelo Git.
- Nenhum segredo ou certificado foi colocado no repositorio.

## Proximas avaliacoes

- Avaliar `lemminx-lsp` antes da fase de validacao formal XML/XSD do `IMPORT API PN`.
- Criar skill propria somente quando o fluxo XML+PDF estiver estavel, testado e repetitivo.
- Registrar qualquer nova ferramenta primeiro em `docs/operacao/MCP_AVALIACAO.md`.
