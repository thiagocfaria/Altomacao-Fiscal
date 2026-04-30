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
- validacao do CNPJ do tomador contra o CNPJ esperado;
- montagem do nome operacional;
- separacao logica de PDF agrupado por paginas quando o documento inteiro pertence a layout homologado.

Ainda nao implementado nesta fatia:

- processamento de pastas externas;
- `watch`;
- `batch` completo com movimentacao;
- ledger persistente;
- arquivamento de original;
- geracao fisica dos PDFs separados.

## Verificacao

Use um repositorio Maven local em `/tmp` quando o ambiente nao permitir escrita em `~/.m2`:

```bash
mvn -Dmaven.repo.local=/tmp/m2-nfse test
mvn -Dmaven.repo.local=/tmp/m2-nfse package
```

## Observacao de auditoria do plano

O plano fala em validar `NotasPdf.pdf` gerando 6 notas se a amostra realmente tiver 6. A amostra atual tem 7 paginas/notas, conforme `pdfinfo`, e os testes foram escritos para 7.
