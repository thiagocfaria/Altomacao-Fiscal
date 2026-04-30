# Renomeador NFS-e

Projeto inicial para extrair dados de PDFs textuais de NFS-e, classificar layouts homologados e preparar a decisao de renomeacao/revisao.

## Estado atual

Implementado nesta primeira fatia:

- projeto Java 17 com Maven;
- extracao de texto por PDFBox;
- detector de layout para:
  - Portal Nacional / DANFSe v1.0;
  - ABRASF municipal / ISSNet;
  - PDF sem texto suficiente;
  - modelo nao suportado;
- parser de campos principais para Portal Nacional e ABRASF/ISSNet;
- deteccao de cancelamento;
- deteccao conservadora de retencao;
- envio para revisao quando houver evidencia conflitante de retencao;
- validacao do CNPJ do tomador contra o CNPJ esperado;
- montagem do nome operacional;
- separacao logica e fisica de PDF agrupado por paginas quando o documento inteiro pertence a layout homologado;
- carga e validacao inicial de `empresas.yaml`;
- selecao de empresas habilitadas;
- resolucao de pastas mensais por estrategia `atual`, `informado`, `lista` e `direto`;
- calculo de SHA-256;
- ledger persistente basico;
- guarda de estabilidade basica para arquivo legivel;
- preservacao do original com tratamento de colisao;
- jar Maven empacotado com dependencias.

Ainda nao implementado nesta fatia:

- processamento completo de pastas externas;
- `watch`;
- `batch` completo com movimentacao;
- CLI operacional.

## Verificacao

Use um repositorio Maven local em `/tmp` quando o ambiente nao permitir escrita em `~/.m2`:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse package
```

## Configuracao

Use `empresas.example.yaml` como base para o arquivo externo de empresas. O codigo nao deve depender de PDFs ou pastas operacionais dentro do repositorio.

Para a homologacao inicial, o `batch` deve permitir apontar para uma pasta ja existente. Quando a origem for a pasta de PDFs modelo do projeto (`NF MODELO ABRASP E PORTAL NACIONAL/`), a execucao deve preservar a entrada e gravar resultados em uma pasta de saida separada, para nao mover nem apagar os PDFs usados como regressao.

## Observacao de auditoria do plano

O plano fala em validar `NotasPdf.pdf` gerando 6 notas se a amostra realmente tiver 6. A amostra atual tem 7 paginas/notas, conforme `pdfinfo`, e os testes foram escritos para 7.
