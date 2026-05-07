---
name: validacao-extracao-pdf
description: Use quando mudar qualquer parser ou adicionar novo campo. Confere que os PDFs do lote piloto extraem os valores corretos — protege contra regressao silenciosa de extracao.
---

# Validacao de Extracao de PDF

Voce garante que o que o sistema extrai bate com o que esta visivel no PDF.

## Regras maes

1. Nunca aceitar parser novo sem rodar contra o lote piloto completo.
2. Nunca comparar valor monetario com `==` de String — usar `BigDecimal.compareTo`.
3. Nunca aceitar extracao de CNPJ sem validar o digito verificador.
4. Nunca declarar regressao resolvida sem re-executar o lote inteiro.
5. Qualquer campo obrigatorio ausente = arquivo vai para `revisar/`, nao para `processados/`.

## Golden values do lote piloto

Os valores abaixo foram verificados manualmente lendo os PDFs originais.
Qualquer alteracao em parser deve reproduzir exatamente estes resultados.

### Portal Nacional — NF 5

```
arquivo:        NF 5 OK.pdf
layout:         PORTAL_NACIONAL
numero:         5
data_emissao:   2026-04-17
cnpj_prestador: 65.710.456/0001-90
nome_prestador: 65.710.456 KELLE EVANETE LEMES DE ALMEIDA
cnpj_tomador:   25.014.360/0001-73
nome_tomador:   DGA ENERGIA E AUTOMACAO LTDA
valor_servico:  256.50
valor_liquido:  256.50
retida:         false
cancelada:      false
nome_esperado:  NFSE_5_KELLE_EVANETE_LEMES_DE_ALMEIDA_20260417_256,50.pdf
```

### Portal Nacional — NF 14

```
arquivo:        NF 14 OK.pdf
layout:         PORTAL_NACIONAL
numero:         14
data_emissao:   2026-04-07
cnpj_prestador: 60.000.770/0001-66
nome_prestador: 60.000.770 IURY BORGES ROCHA
cnpj_tomador:   25.014.360/0001-73
nome_tomador:   DGA ENERGIA E AUTOMACAO LTDA
valor_servico:  6000.00
valor_liquido:  6000.00
retida:         false
cancelada:      false
nome_esperado:  NFSE_14_IURY_BORGES_ROCHA_20260407_6000,00.pdf
```

### ABRASF — NF 55034

```
arquivo:        NF 55034 OK.pdf
layout:         ABRASF
numero:         55034
data_emissao:   2026-03-05
cnpj_prestador: 04.294.816/0001-26
nome_prestador: UMA MEDICINA E SEGURANCA DE TRABALHO LTDA
cnpj_tomador:   04.116.617/0002-09
nome_tomador:   COPEMBOM CHOCOLATES EIRELI
valor_servico:  114.00
valor_liquido:  114.00
retida:         false
cancelada:      false
nome_esperado:  NFSE_55034_UMA_MEDICINA_E_SEGURANCA_DE_TRABALHO_LTDA_20260305_114,00.pdf
```

### ABRASF multi-nota — NotasPdf.pdf

```
arquivo:        NotasPdf.pdf
layout:         ABRASF
total_notas:    confirmar ao implementar (estimado: 6-7 notas)
cnpj_tomador_comum: 33.265.761/0001-24
```

Notas dentro deste arquivo:

| NF | Prestador | Data | Valor | Retida | Cancelada |
|---|---|---|---|---|---|
| 346928 | Unimed Anapolis | 2026-03-02 | 1.288,52 | nao | nao |
| 889 | PB Cameras e Alarmes | 2026-03-03 | 125,97 | nao | nao |
| 252 | Invento Marketing Digital | 2026-03-04 | 5.115,88 | nao | nao |
| 362 | Otimize Solutions | 2026-03-25 | 450,00 | nao | nao |
| **48** | **Csa Gestao e Treinamentos** | **2026-03-27** | **100.000,00** | **nao** | **SIM** |
| 49 | Csa Digital | 2026-03-31 | 25.000,00 | nao | nao |
| 50 | Csa Gestao e Treinamentos | 2026-03-31 | 20.000,00 | nao | nao |

NF 48 deve receber sufixo `##CANCELADA##` e ir para `revisar/canceladas/`.
As demais notas validas devem ser separadas em arquivos individuais.

## Como validar

### Teste automatico recomendado

```java
@Test
void nf5_portal_nacional_deve_extrair_todos_campos() {
    File pdf = new File("src/test/resources/fixtures/NF 5 OK.pdf");
    ResultadoExtracao resultado = parser.extrair(pdf);

    assertThat(resultado.numero()).isEqualTo("5");
    assertThat(resultado.cnpjTomador()).isEqualTo("25.014.360/0001-73");
    assertThat(resultado.valorServico()).isEqualByComparingTo(new BigDecimal("256.50"));
    assertThat(resultado.valorLiquido()).isEqualByComparingTo(new BigDecimal("256.50"));
    assertThat(resultado.retida()).isFalse();
    assertThat(resultado.cancelada()).isFalse();
    assertThat(resultado.layout()).isEqualTo(Layout.PORTAL_NACIONAL);
}
```

### Verificacao manual de regressao

Para cada arquivo do lote, conferir:
1. campo `numero` bate com numero visivel no PDF?
2. `cnpjTomador` sem formatacao extra (so digitos e pontuacao padrao)?
3. `valorServico` e `valorLiquido` como BigDecimal sem arredondamento?
4. `retida = true` so quando algum campo de retencao esta preenchido?
5. `cancelada = true` so quando ha texto de cancelamento no PDF?
6. nome gerado respeita o padrao e nao contem caracter invalido?

## Quando entrar

- qualquer mudanca em `PortalNacionalParser` ou `AbrasfParser`
- adicao de novo campo a `NotaFiscal`
- mudanca na logica de retencao
- adicao de novo layout
- suspeita de regressao em campo ja funcionando

## Fechamento padrao

Ao final, responda:

1. quais PDFs do lote foram validados
2. quais campos foram verificados
3. se algum golden value divergiu
4. se a extracao esta aprovada para todos os arquivos do lote
