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
| `RENOMEADOR/AGENTS.md` | regras duraveis do modulo RENOMEADOR |
| `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md` | documentacao tecnica e operacional do RENOMEADOR |
| `docs/operacao/MCP_AVALIACAO.md` | decisoes de MCP/LSP e criterios para promover novas ferramentas |
| `docs/operacao/AMBIENTE_SKILLS_MCP_LSP.md` | como reproduzir o ambiente em outra maquina |

O arquivo `.claude/settings.local.json` nao e versionado. Ele guarda permissoes locais daquela maquina e nao deve virar baseline do projeto.

## Estado atual dos LSPs

| LSP/plugin | Estado | Escopo | Uso no projeto |
|---|---|---|---|
| `jdtls-lsp@claude-plugins-official` | ativo | projeto | Java: navegacao, diagnostico, tipos e refactors em `.java` |
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
| `codebase-memory-mcp` | ativo no ambiente global | usuario | grafo de chamadas/dependencias para navegar impacto no codigo |

O `codebase-memory-mcp` nao esta configurado dentro do repositorio. Ele e configuracao global do usuario/agente e precisa ser instalado/configurado na outra maquina antes de indexar este repositorio.

Ao mudar bastante o codigo Java, reindexar o repositorio no `codebase-memory-mcp` apontando para a raiz:

```text
/srv/DocumentosCompartilhados/ALTOMACAO IMPORTACAO DE NF
```

## Skills do projeto

Nao ha skill local nova aprovada para `IMPORT API PN` ou `RENOMEADOR` neste momento.

As antigas skills locais de `RENOMEADOR/.claude/skills/` foram substituidas por documentacao unica e regras em:

- `AGENTS.md`
- `RENOMEADOR/AGENTS.md`
- `RENOMEADOR/DOCUMENTACAO_RENOMEADOR_NFSE.md`
- `AUDITORIA_RISCOS_RENOMEADOR.md`
- documentos de `IMPORT API PN/`

Motivo: ainda estamos consolidando o fluxo XML+PDF. Criar skills agora duplicaria regra que ainda esta sendo refinada.

## Skills operacionais usadas nesta entrega

Estas skills pertencem ao ambiente do agente, nao ao codigo de producao:

| Skill | Uso |
|---|---|
| `github:yeet` | fluxo de revisao, commit, push e publicacao no GitHub |
| `superpowers:verification-before-completion` | exige validacao real antes de declarar trabalho concluido |

Elas nao precisam existir dentro deste repositorio para o sistema Java rodar. So sao relevantes se a outra maquina tambem for usada para desenvolvimento assistido por agente.

## Como configurar outra maquina

1. Clonar o repositorio.
2. Instalar Java 17, Maven e Git.
3. Confirmar que `.claude/settings.json` veio com o repositorio.
4. Instalar o plugin `jdtls-lsp@claude-plugins-official` no ambiente do Claude Code, se for usar Claude Code nessa maquina.
5. Confirmar que o binario `jdtls` esta disponivel no ambiente do usuario.
6. Instalar/configurar `codebase-memory-mcp` globalmente, se quiser a mesma navegacao por grafo.
7. Indexar a raiz deste repositorio no `codebase-memory-mcp`.
8. Nao copiar certificados digitais, senhas, XMLs reais, PDFs reais, logs ou pastas operacionais para dentro do Git.
9. Rodar a validacao do modulo antes de trabalhar:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse -pl RENOMEADOR verify -Pintegration
```

## Como conferir se esta igual

Checklist rapido:

- `.claude/settings.json` contem `jdtls-lsp@claude-plugins-official: true`.
- `RENOMEADOR` compila e passa nos testes.
- `codebase-memory-mcp`, se usado, foi reindexado depois do clone.
- `.claude/settings.local.json` continua local e ignorado pelo Git.
- Nenhum segredo ou certificado foi colocado no repositorio.

## Proximas avaliacoes

- Avaliar `lemminx-lsp` antes da fase de validacao formal XML/XSD do `IMPORT API PN`.
- Criar skill propria somente quando o fluxo XML+PDF estiver estavel, testado e repetitivo.
- Registrar qualquer nova ferramenta primeiro em `docs/operacao/MCP_AVALIACAO.md`.
