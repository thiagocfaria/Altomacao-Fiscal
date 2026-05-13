# PLANO - Gerador local de DANFSe a partir do XML

Criado em: 10/05/2026
Revisado em: 10/05/2026
Modulo afetado: `IMPORT API PN`
Decisao do dono: gerar PDF localmente a partir do XML que ja chega real do ADN.
Restricao visual: layout deve ser **operacionalmente equivalente e visualmente fiel** ao DANFSe oficial do Portal Nacional. Nao precisa ser pixel-identico, mas nao pode faltar campo, trocar ordem fiscal relevante, parecer outro documento ou simular assinatura/chancela que nao pertence ao nosso PDF.
Restricao de manutencao: facil de mexer no layout. Para isso ser verdadeiro em producao, o template deve aceitar override externo fora do JAR; template apenas em `resources/` exige novo pacote para alterar layout.

## 0. Parecer tecnico da revisao

O plano e o caminho certo para o nosso sistema, mas ainda nao esta "perfeito". Ele precisa destes ajustes antes de virar implementacao:

- trocar a promessa de "imperceptivelmente diferente" por "fiel e conferivel", para nao sugerir falsificacao do PDF oficial;
- nao depender de JAXB "presente no JDK 17", porque JAXB nao vem mais embutido no JDK moderno; usar DOM/StAX ou adicionar Jakarta JAXB explicitamente;
- tratar OpenHTMLtoPDF como motor principal, mas desenhar o CSS com tabelas, blocos e paged media simples, sem depender de flexbox avançado;
- validar o PDF local contra o XML por campos fiscais, nao so por aparencia;
- manter templates e fontes com caminho externo configuravel, alem da copia padrao embarcada;
- guardar PDFs oficiais de referencia somente como amostras controladas, fora do Git quando contiverem dados reais;
- registrar no PDF que o documento foi gerado localmente a partir do XML oficial, quando isso for necessario juridica/operacionalmente, sem inventar hash ou selo do Portal.

## 1. Por que gerar localmente, e nao baixar do Portal

A API DANFSe oficial (`https://adn.nfse.gov.br/danfse/...`) sera **suspensa em 01/07/2026** pela Nota Tecnica SE/CGNFS-e n. 008, de 05/05/2026. Construir dependencia dela hoje seria construir algo que morre em 52 dias.

Gerar localmente:

- nao depende de API externa
- nao consome cota do Portal
- nao quebra quando a API for desligada
- pode ser ajustado a qualquer momento se a Receita mudar o layout

A NT 008 inclusive **publica o layout oficial novo do DANFSe** que deve ser seguido a partir da suspensao. Ou seja, o documento que mata a API velha tambem entrega o desenho oficial que vamos seguir aqui.

## 2. Arquitetura proposta

```
ADN /DFe/{NSU}                  (real, ja existe)
        |
        v JSON com LoteDFe[].ArquivoXml
ExtratorLoteDfeJson              (real, ja existe)
        |
        v byte[] xml
PublicadorRestEntrada            (real, ja existe; salva XML em entrada-rest)
        |
        v
[NOVO] GeradorDanfseLocal
       1. Parser XML -> NotaFiscalServico (modelo Java)
       2. Renderer HTML (template Mustache/Thymeleaf preenchido com a nota)
       3. Conversor HTML -> PDF (OpenHTMLtoPDF)
       4. Salva PDF na MESMA pasta entrada-rest, mesmo NSU, extensao .pdf
        |
        v entrada-rest/PN_{cnpj}_NSU_{nsu}_{aaaamm}.pdf
        |
        v
RENOMEADOR roteia (ja existe; vai mover para CAMINHO REST do cliente)
```

O PDF sai com **o mesmo padrao de nome** que o XML correspondente, so muda extensao. O RENOMEADOR ja sabe processar PDF na entrada-rest, entao o resto do fluxo nao precisa mudar nada.

## 3. Decisao tecnica: HTML -> PDF com OpenHTMLtoPDF

Escolhida porque:

- layout em HTML/CSS - facil de iterar visualmente
- DANFSe oficial e essencialmente um formulario com tabelas, blocos e cabecalhos - HTML expressa isso naturalmente
- alteracao de layout pode ser feita por template externo quando o comando estiver empacotado com essa opcao
- biblioteca livre (LGPL), sem royalty
- maduro, usado em sistemas fiscais brasileiros conhecidos

Pilha:

| Camada | Biblioteca |
|---|---|
| Parser XML do schema NFS-e nacional | DOM/StAX do JDK ou Jakarta JAXB como dependencia explicita |
| Modelo Java tipado | Records + enums |
| Engine de template | Mustache (`com.github.spullara.mustache.java:compiler`) - simples, sem logica embutida |
| HTML -> PDF | OpenHTMLtoPDF (`com.openhtmltopdf:openhtmltopdf-pdfbox`) - sucessor moderno do Flying Saucer original, suporta CSS3 melhor |
| Fontes | Liberation Sans / DejaVu Sans embutidas - garantem renderizacao identica em qualquer maquina |

OpenHTMLtoPDF e preferido sobre o Flying Saucer original. Mesmo assim, o layout deve usar CSS conservador para PDF: tabelas, larguras fixas, bordas simples, `@page`, fontes embutidas e blocos previsiveis. Nao depender de flexbox para a estrutura principal do DANFSe.

## 4. Estrutura de pastas

```
IMPORT API PN/
  src/main/
    java/br/com/nfse/importadorpn/
      danfse/                            (NOVO pacote)
        modelo/
          NotaFiscalServico.java         (record raiz)
          Emitente.java
          Tomador.java
          Servico.java
          Tributo.java
          Endereco.java
        parser/
          ParserNfseNacional.java        (XML -> NotaFiscalServico)
        render/
          RendererDanfse.java            (modelo + template -> HTML)
          ConversorHtmlPdf.java          (HTML -> PDF via OpenHTMLtoPDF)
        GeradorDanfseLocal.java          (fachada usada pelo restante do sistema)
    resources/
      danfse/
        layout.html.mustache             (template padrao embarcado)
        layout.css                       (estilo)
        fontes/                          (TTFs embarcadas)
  src/test/
    java/.../danfse/
      ParserNfseNacionalTest.java
      RendererDanfseTest.java
      ConversorHtmlPdfTest.java
      GeradorDanfseLocalIT.java          (teste de integracao com XML real)
    resources/
      danfse/
        nota-real-anonimizada.xml
        danfse-oficial-comparativo.pdf   (referencia visual)
```

O template `layout.html.mustache` deve ser o ponto principal de ajuste visual. Para nao recompilar em producao, o comando deve aceitar uma pasta externa opcional, por exemplo `--template-dir`, e usar o template embarcado apenas como padrao.

## 5. Layout DANFSe - blocos obrigatorios

Conforme NT 008/2026, o DANFSe deve conter:

1. **Cabecalho do Sistema Nacional NFS-e**
   - logo "NFS-e" oficial
   - "DANFS-e - Documento Auxiliar da NFS-e"
   - chave de acesso (50 caracteres, formatada em grupos de 4)
   - codigo de barras / QR code da chave
2. **Identificacao da NFS-e**
   - numero da NFS-e
   - serie
   - data e hora de emissao
   - competencia
   - codigo de verificacao
3. **Emitente do servico (prestador)**
   - razao social, nome fantasia, CNPJ/CPF, IE, IM
   - endereco completo
   - telefone, e-mail
4. **Tomador do servico**
   - mesmos campos do emitente
5. **Discriminacao do servico**
   - codigo de tributacao do municipio
   - CNAE
   - codigo do servico LC 116/2003
   - descricao livre do servico
   - quantidade, valor unitario, valor total
6. **Valores**
   - valor dos servicos
   - deducoes
   - base de calculo
   - aliquota ISS
   - ISS retido / a recolher
   - PIS, COFINS, INSS, IR, CSLL retidos quando houver
   - valor liquido
7. **Outras informacoes**
   - regime especial de tributacao (se houver)
   - opcao pelo Simples Nacional
   - incentivo fiscal
   - observacoes da NFS-e
8. **Rodape**
   - URL de validacao do Portal Nacional
   - informacao de autenticidade verificavel pelo XML/chave, sem inventar assinatura do Portal

Cada bloco vira uma `<section>` no template HTML, estilizada com CSS para reproduzir o visual oficial.

## 6. Estrategia de fidelidade visual

Meta: usuario comum reconhece o documento como DANFSe e consegue conferir os mesmos dados principais sem estranhamento visual.

Tres tecnicas combinadas:

1. **Comparacao lado a lado pixel-a-pixel automatica** durante desenvolvimento
   - script de teste que recebe o XML, gera o PDF local, e compara visualmente com um DANFSe oficial baixado pela API enquanto ela ainda existe (vou usar a API antes de 01/07 so para ter referencia visual, nao em producao)
   - usa biblioteca tipo `pdf-image-diff` ou converter ambos em PNG e fazer diff
2. **Fontes embutidas iguais ao oficial**
   - Liberation Sans no lugar de Arial. Indistinguivel em PDF
3. **Espacamentos e cores equivalentes**
   - copia o CSS-like de espacamentos do oficial
   - cores RGB exatas do logo NFS-e

Aceitavel: pequenas diferencas em milimetros nas margens e ausencia de elementos que so o Portal possa assinar/emitir. Inaceitavel: layout diferente, fontes muito diferentes, campos faltando, ordem trocada, valor divergente, CNPJ divergente, chave divergente ou qualquer elemento que faca o PDF local parecer uma segunda emissao oficial.

## 7. Implementacao por fases

### Fase 1 - Modelo e Parser (1 dia)
- declarar records do modelo `NotaFiscalServico`
- escrever `ParserNfseNacional` que le um XML real e devolve o record preenchido
- testes unitarios com XMLs reais ja em entrada-rest

### Fase 2 - Template HTML mais grosseiro (1 dia)
- montar `layout.html.mustache` com todos os 8 blocos preenchidos
- estilo basico (sem perfeicao visual)
- gerar PDF de uma nota e abrir para verificar que todos os dados estao la

### Fase 3 - Refino visual ate ficar fiel ao oficial (2-3 dias)
- baixar 1 ou 2 DANFSe oficiais reais via API DANFSe enquanto ela existe (so para REFERENCIA, nao para producao)
- ajustar CSS ate o diff visual ficar minimo
- conferir em monitor e em impressao

### Fase 4 - Integracao no fluxo (meio dia)
- chamar `GeradorDanfseLocal.gerar(xml)` apos `PublicadorRestEntrada.publicar(...)` no `RegistroConsultaAdn.registrarLoteJson`
- salvar PDF na entrada-rest com mesmo nome do XML, extensao `.pdf`
- atualizar ledger: `estado_pdf=CONCLUIDO` ao terminar
- se a geracao local falhar, manter XML concluido e PDF pendente; nao voltar automaticamente para API DANFSe depois de 01/07/2026

### Fase 5 - Backfill dos XMLs ja existentes (meio dia)
- novo subcomando: `gerar-pdf-faltante --backend ... --mes 2026-05`
- varre `entrada-rest/`, acha cada XML sem PDF correspondente, gera o PDF
- atualiza ledger
- relatorio: gerados / ja existiam / falhas
- **so roda quando o operador disparar manual** - nao automatico

### Fase 6 - Testes em duas empresas (depois do certificado da segunda)
- captura real de XMLs dos 2 CNPJs
- gerador local cria PDF de cada um
- RENOMEADOR roteia
- conferencia visual lado a lado: o PDF gerado parece o oficial?

Total estimado: ~5-6 dias uteis trabalhando focado.

## 8. Riscos e mitigacoes

| Risco | Impacto | Mitigacao |
|---|---|---|
| XML do Portal pode mudar | quebra parser | parser usa schema XSD oficial; mudanca no XSD vira mudanca rastreavel no codigo |
| Layout DANFSe pode ser revisado pela Receita | divergencia visual | template HTML/CSS isolado, ajuste sem recompilar |
| Fontes diferentes em maquinas diferentes | PDF visual diferente | fontes embarcadas em `resources/danfse/fontes/` - sempre iguais |
| Hash de autenticidade nao podemos replicar | PDF tem rodape diferente | aceitar; o DANFSe oficial assina o conteudo, a gente nao precisa porque a fonte da verdade fiscal e o XML, nao o PDF |
| Antes de 01/07 a API DANFSe pode falhar e nao podermos comparar | menos refinamento visual | baixar 5-10 DANFSe oficiais nessa semana e guardar como referencia visual congelada |
| PDF local parecer documento oficial assinado pelo Portal | risco juridico/operacional | nao inventar selo, assinatura ou hash do Portal; deixar verificacao baseada na chave/XML |
| Template embarcado exigir novo JAR para ajuste | manutencao lenta | permitir `--template-dir` externo com fallback para recursos embarcados |

## 9. O que NAO esta no escopo deste plano

- DMS (publicador para `CAMINHO DMS`) - e outro modulo, outro plano
- Gerar PDF de eventos da NFS-e (cancelamento, substituicao) - so DANFSe da nota original
- Assinar digitalmente o PDF gerado - a fonte fiscal continua sendo o XML; o PDF e operacional
- Suportar layouts municipais antigos diferentes do nacional - todos os XMLs vem do ADN nacional

## 10. Decisao do dono pendente para destravar

Para comecar a Fase 1 preciso de:

- [ ] Confirmacao de que `OpenHTMLtoPDF` e aceitavel como dependencia (LGPL, livre)
- [ ] Confirmacao de que posso baixar 5-10 DANFSe oficiais reais agora pela API DANFSe (apenas como referencia visual congelada, nao em producao) - **ou** ja existem PDFs DANFSe oficiais arquivados em algum cliente que eu possa usar de referencia
- [ ] Permissao para criar o pacote `danfse/` dentro de `IMPORT API PN/src/main/java/br/com/nfse/importadorpn/`
- [ ] Decisao se o parser sera DOM/StAX sem dependencia nova ou Jakarta JAXB com dependencia explicita
- [ ] Decisao se o template externo sera configurado por `--template-dir`, variavel de ambiente ou arquivo de configuracao
- [ ] Permissao para aumentar o JAR final por causa das fontes embarcadas (~5-10MB a mais)

## 11. Como saber que terminamos

Definicao de pronto:

1. todo XML que cai em `entrada-rest` ganha um PDF gerado localmente em segundos
2. ledger marca `estado_pdf=CONCLUIDO` automaticamente, sem mais `PENDENTE`
3. backfill processa todos os XMLs ja existentes em uma rodada
4. RENOMEADOR move XML+PDF para o `CAMINHO REST` do cliente igual hoje move o XML
5. teste cego: 5 PDFs gerados sao misturados com 5 DANFSe oficiais; observador comum nao distingue
6. teste com 2 empresas confirma que cada PDF vai para a pasta certa do cliente certo
7. comando `publicar-rest-simulado` continua existindo so para teste, mas nao e mais necessario para producao
8. teste automatico extrai texto do PDF local e confere chave, CNPJ, numero, data, valor do servico e valor liquido contra o XML
9. falha de geracao nao apaga XML, nao publica PDF parcial e deixa ledger em `PDF_PENDENTE`

Quando esses 9 itens estiverem verdes, o sistema **realmente** importa PDF + XML por nota, sem simulacao.
