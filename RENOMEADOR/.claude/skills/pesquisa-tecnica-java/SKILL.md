---
name: pesquisa-tecnica-java
description: Use antes de adotar nova dependencia Maven, novo MCP, novo plugin Claude ou nova biblioteca que nao esteja no baseline atual deste projeto.
---

# Pesquisa Tecnica Java

Voce pesquisa antes de implementar para nao gastar esforco em caminho errado.

## Regras maes

1. Nao adotar dependencia Maven sem checar licenca, versao ativa e tamanho do impacto.
2. Nao instalar MCP sem checklist de seguranca completo (ver `docs/operacao/MCP_AVALIACAO.md`).
3. Pesquisar primeiro no ecossistema Java padrao antes de chamar servico externo.
4. Nao trocar PDFBox sem benchmark comparativo real com os PDFs do lote piloto.
5. Registrar a decisao e o motivo em `docs/operacao/` ou no doc relevante.

## Stack atual do baseline (nao substituir sem pesquisa)

| Categoria | Biblioteca atual | Motivo da escolha |
|---|---|---|
| PDF | Apache PDFBox 3.x | Licenca Apache 2.0, texto seletavel confiavel, sem custo |
| CLI | Picocli 4.x | Anotacoes limpas, subcomandos, bash completion |
| Config | Jackson + jackson-dataformat-yaml | Padrao de mercado, estavel |
| Logging | SLF4J + Logback + AsyncAppender | Zero overhead no hot path com async |
| Testes | JUnit 5 + AssertJ | Padrao sênior atual |
| File watch | java.nio.file.WatchService (NIO2) | Nativo Java, sem dep extra, funciona em Linux e Windows |
| Hash | java.security.MessageDigest SHA-256 | Nativo Java, sem dep extra |

## Candidatos mapeados para proximas decisoes

### Se PDFBox provar insuficiente para algum caso

| Candidato | Ponto forte | Risco |
|---|---|---|
| **iText 7 (Community)** | Mais robusto para PDF complexo, API moderna | Licenca AGPL — verificar compatibilidade com o projeto |
| **Apache Tika** | Detecta tipo de arquivo + extrai texto de muitos formatos | Pesado (~50 MB), overkill se o problema for so PDF textual |
| **pdftotext (Poppler via ProcessBuilder)** | Muito rapido, preciso | Dependencia de binario externo, dificulta instalacao na empresa |

Recomendacao: so substituir PDFBox se aparecer PDF real que ele nao consegue ler. Antes disso, testar com `PDFTextStripper` configurado (`setSortByPosition(true)`, `setAddMoreFormatting(true)`).

### Se WatchService provar insuficiente (ex: latencia alta em Windows)

| Candidato | Ponto forte | Risco |
|---|---|---|
| **JNotify** | Wrapper nativo por SO, latencia muito baixa | Dep nativa, compilar por SO, mais complexo de distribuir |
| **Apache Commons IO FileAlterationObserver** | Pure Java, polling confiavel | Polling = CPU mesmo sem novos arquivos |
| **Polling com schedule** | Simples, zero dep | Latencia = intervalo de poll |

Recomendacao: WatchService NIO2 e suficiente. Problema real de latencia so aparece em redes compartilhadas (SMB/CIFS) — investigar a causa antes de trocar.

### Para OCR (fora do escopo V1, mas pesquisado para V2)

| Candidato | Tipo | Risco |
|---|---|---|
| **Tesseract via Tess4J** | JNI wrapper | Qualidade dependente do DPI do scan |
| **Google Vision API** | Cloud | Custo, privacidade dos dados da empresa |

Na V1: PDF escaneado vai para `revisar/` com motivo `"PDF sem texto selecionavel"`.

## Checklist antes de adicionar dependencia Maven

Preencher para cada candidato antes de decidir:

| Item | Resposta |
|---|---|
| Qual e a licenca? (MIT/Apache/LGPL/GPL/AGPL) | |
| Ultima versao estavel e quando foi lancada? | |
| Ha versao ativa de manutencao (commits nos ultimos 6 meses)? | |
| Qual e o tamanho do JAR + transitivas? | |
| Tem CVEs conhecidas na versao candidata? | |
| Tem suporte a Java 17? | |
| Funciona em Linux e Windows (ambiente da empresa)? | |
| Como remover se necessario? Qual e o blast radius? | |
| Integra com a stack existente sem conflito de versao? | |

## Checklist de seguranca para MCP novo

(Ver tambem `docs/operacao/MCP_AVALIACAO.md`)

| Item | Resposta |
|---|---|
| Que dados locais ele le? (PDFs, config, credenciais?) | |
| Usa rede? Para onde? | |
| Onde grava indice, cache ou log? | |
| Toca credencial ou CNPJ de empresa? | |
| Como desligar no Claude Code? | |
| Como limpar o que criou? | |
| Como voltar ao estado anterior? | |

MCP sem este checklist nao entra no projeto.

## Quando entrar

- proposta de nova dependencia no `pom.xml`
- avaliacao de MCP ou plugin Claude novo
- escolha de algoritmo ou estrategia nova (ex: como implementar o Ledger)
- qualquer integracao com servico externo (prefeitura, email, etc.)
- duvida entre duas libs para o mesmo problema

## Fechamento padrao

Ao final, responda:

1. qual problema foi pesquisado
2. quais alternativas foram avaliadas
3. qual foi escolhida e por que
4. quais riscos foram identificados
5. onde a decisao foi registrada
