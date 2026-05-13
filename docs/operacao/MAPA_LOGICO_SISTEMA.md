# Mapa simples do sistema

Atualizado em 12/05/2026.

Este documento mostra o sistema como uma historinha: quem chama primeiro, o que ele
olha, para onde manda os arquivos e onde pode dar erro.

## Ideia principal

```text
O Portal mostra quais notas existem.
A planilha mostra onde cada empresa guarda as notas.
As pastas mostram o que ja chegou.
Se faltar arquivo na pasta, o sistema deve trazer de novo.
```

## Desenho grande

```mermaid
flowchart TD
    A[Voce abre o painel] --> B[Voce escolhe o mes]
    B --> C[Voce clica VERIFICAR TUDO]
    C --> D[O painel confere se esta tudo pronto]
    D --> E[Voce clica LIGAR SISTEMA]
    E --> F[O painel liga o RENOMEADOR]
    E --> G[O painel chama o IMPORTADOR]
    G --> H[O IMPORTADOR olha o Portal]
    G --> I[O IMPORTADOR olha as pastas finais]
    H --> J{Falta alguma nota?}
    I --> J
    J -- nao --> K[Nao mexe em nada]
    J -- sim --> L[Baixa/publica o que falta]
    L --> M[DMS vai direto para a pasta DMS]
    L --> N[REST cai na pasta de entrada]
    N --> O[RENOMEADOR pega da entrada]
    O --> P[RENOMEADOR manda para a pasta REST correta]
```

## O que acontece quando voce abre o painel

```mermaid
flowchart TD
    A[Abre python3 painel.py] --> B[Mostra botoes]
    A --> C[Mostra mes de atuacao]
    C --> D{Automatico ligado?}
    D -- sim --> E[Usa o mes atual]
    D -- nao --> F[Usa o mes que voce digitou]
```

Em maio de 2026:

```text
Automatico ligado  -> painel usa 2026-05
Automatico desligado + 2026-04 digitado -> painel usa 2026-04
```

## Desenho: abrir painel e clicar VERIFICAR TUDO

![Desenho simples do VERIFICAR TUDO](mapa-verificar-tudo.svg)

```mermaid
flowchart TD
    A["Pessoa roda:<br/>python3 painel.py"] --> B["Painel abre na tela"]
    B --> C["Pessoa escolhe o mes"]
    C --> D{"Automatico ligado?"}
    D -- "sim" --> E["Painel usa o mes vigente<br/>exemplo: 2026-05"]
    D -- "nao" --> F["Painel usa o mes digitado<br/>exemplo: 2026-04"]
    E --> G["Pessoa clica<br/>VERIFICAR TUDO"]
    F --> G

    G --> H["1. Conferir planilha/caminhos"]
    H --> H1["Olha PLANILHA_FISCAL.xlsm"]
    H1 --> H2["Ve empresas ativas do mes"]
    H2 --> H3["Ve entrada REST configurada"]

    G --> I["2. Conferir certificados"]
    I --> I1["Olha certificado de cada empresa"]
    I1 --> I2["Confere senha/certificado"]
    I2 --> I3["Mostra OK ou atencao"]

    G --> J["3. Consultar Portal e simular"]
    J --> J1["Usa o mesmo mes/ambiente/NSU/max-lotes do LIGAR"]
    J1 --> J2["Compara Portal x pastas finais"]
    J2 --> J3["Conta o que importaria sem gravar nada"]

    G --> K["4. Atualizar lista do RENOMEADOR"]
    K --> K1["Le todos os meses da planilha"]
    K1 --> K2["Gera empresas.yaml"]
    K2 --> K3["RENOMEADOR passa a saber os caminhos"]

    G --> L["5. Preflight do RENOMEADOR"]
    L --> L1["Confere YAML, rotas, backend e lock"]
    L1 --> L2["Registra watch sem processar arquivos"]

    H3 --> N{"Tudo OK?"}
    I3 --> N
    J3 --> N
    K3 --> N
    L2 --> N

    N -- "sim" --> O["Painel mostra:<br/>TUDO OK"]
    N -- "nao" --> P["Painel mostra:<br/>ATENCAO, BLOQUEADO ou ERRO_EXTERNO"]
```

```text
Neste desenho, VERIFICAR TUDO e so conferencia fiel.
Ele nao importa nota.
Ele nao move arquivo.
Ele nao altera ledger.
Ele so responde: posso ligar o sistema com seguranca?
```

## Botao VERIFICAR TUDO

```mermaid
flowchart TD
    A[Voce clica VERIFICAR TUDO] --> B[Confere a planilha]
    B --> C[Confere certificados]
    C --> D[Consulta Portal em modo somente leitura]
    D --> E[Atualiza a lista do RENOMEADOR]
    E --> F[Roda preflight do RENOMEADOR]
    F --> G[Mostra OK, ATENCAO, BLOQUEADO ou ERRO_EXTERNO]
```

Em palavras simples:

```text
VERIFICAR TUDO nao importa nota.
Ele usa o mesmo motor do reconciliar em dry-run, sem publicar XML/PDF, sem baixar
DANFSe e sem alterar ledger.
```

## Botao LIGAR SISTEMA

```mermaid
flowchart TD
    A[Voce clica LIGAR SISTEMA] --> B[O botao vira DESLIGAR SISTEMA]
    A --> C[O painel atualiza a lista do RENOMEADOR]
    C --> D[O painel liga o RENOMEADOR]
    D --> E[RENOMEADOR fica olhando a pasta de entrada]
    A --> F[O painel chama o IMPORTADOR]
    F --> G[IMPORTADOR compara Portal x pastas finais]
    G --> H[IMPORTADOR traz o que falta]
    H --> I[Depois o painel repete isso de tempos em tempos]
```

Em palavras simples:

```text
LIGAR SISTEMA liga dois trabalhadores:
1. RENOMEADOR fica olhando a entrada.
2. IMPORTADOR vai no Portal e busca o que falta.
```

## Trabalhador 1: IMPORTADOR

O IMPORTADOR e quem conversa com o Portal Nacional.

```mermaid
flowchart TD
    A[IMPORTADOR comeca] --> B[Olha o mes escolhido no painel]
    B --> C[Olha a planilha daquele mes]
    C --> D[Para cada empresa ativa]
    D --> E[Olha as pastas finais da empresa]
    D --> F[Consulta o Portal Nacional]
    E --> G{A nota ja esta completa nas pastas?}
    F --> G
    G -- sim --> H[Nao importa de novo]
    G -- nao --> I[Publica o que falta]
    I --> J[XML DMS vai direto para DMS]
    I --> K[XML/PDF REST vao para a entrada REST]
```

O IMPORTADOR decide assim:

```text
Portal tem a nota?
Sim.

A pasta final ja tem tudo?
Sim -> nao faz nada.
Nao -> publica de novo.
```

## Trabalhador 2: RENOMEADOR

O RENOMEADOR nao consulta o Portal. Ele so organiza XML/PDF REST.

```mermaid
flowchart TD
    A[RENOMEADOR ligado] --> B[Olha a pasta de entrada REST]
    B --> C{Entrou XML ou PDF?}
    C -- nao --> B
    C -- sim --> D[Le o arquivo]
    D --> E[Descobre CNPJ e data da nota]
    E --> F[Olha a planilha/lista de empresas]
    F --> G{Achou o caminho REST certo?}
    G -- nao --> H[Manda para TOMADOR NAO ENCONTRADO]
    G -- sim --> I{Como esta a nota?}
    I -- normal --> J[Manda para processados]
    I -- retida --> K[Manda para RETIDO]
    I -- cancelada --> L[Manda para canceladas]
```

Em palavras simples:

```text
RENOMEADOR pega o que caiu na entrada.
Ele descobre de quem e a nota.
Ele joga na pasta REST correta.
```

## Caminho do arquivo REST

```mermaid
flowchart LR
    A[Portal Nacional] --> B[IMPORTADOR]
    B --> C[Entrada REST tecnica]
    C --> D[RENOMEADOR]
    D --> E[CAMINHO REST da planilha]
```

Em palavras simples:

```text
Arquivo REST nao vai direto para o cliente.
Ele passa primeiro pela entrada.
Depois o RENOMEADOR separa e manda para o lugar certo.
```

## Caminho do arquivo DMS

```mermaid
flowchart LR
    A[Portal Nacional] --> B[IMPORTADOR]
    B --> C[CAMINHO DMS da planilha]
```

Em palavras simples:

```text
DMS nao passa pelo RENOMEADOR.
O IMPORTADOR manda DMS direto para a pasta DMS.
```

## Quando voce apaga arquivo da pasta final

```mermaid
flowchart TD
    A[Voce apaga um XML/PDF/DMS] --> B[Proxima rodada do painel]
    B --> C[IMPORTADOR olha o Portal]
    B --> D[IMPORTADOR olha a pasta final]
    C --> E{Portal tem a nota e pasta nao tem?}
    D --> E
    E -- nao --> F[Nao faz nada]
    E -- sim --> G[IMPORTADOR publica de novo]
    G --> H[Se for DMS, vai direto para DMS]
    G --> I[Se for REST, cai na entrada]
    I --> J[RENOMEADOR manda para REST correta]
```

Essa e a regra mais importante:

```text
Quem manda e o Portal + a pasta final.
Log e ledger nao podem impedir uma nota apagada de voltar.
```

## O que cada parte faz

| Parte | Explicacao simples |
|---|---|
| Painel | Tela que voce usa para verificar, escolher mes, ligar e desligar |
| Planilha | Lista das empresas e dos caminhos das pastas |
| Importador | Busca no Portal o que falta |
| Renomeador | Organiza XML/PDF REST na pasta certa |
| Entrada REST | Fila temporaria entre Importador e Renomeador |
| CAMINHO REST | Pasta final dos XML/PDF REST |
| CAMINHO DMS | Pasta final dos XML DMS |
| Ledger/log | Diario do que aconteceu, mas nao manda na decisao |

## Onde olhar quando algo der errado

```mermaid
flowchart TD
    A[Algo nao importou] --> B{Mes no painel esta certo?}
    B -- nao --> C[Corrigir mes e ligar de novo]
    B -- sim --> D{Empresa esta ativa na planilha daquele mes?}
    D -- nao --> E[Corrigir planilha]
    D -- sim --> F{Portal retornou nota?}
    F -- nao --> G[Problema esta no Portal ou certificado/consulta]
    F -- sim --> H{Arquivo ja existe na pasta final?}
    H -- sim --> I[Sistema pulou corretamente]
    H -- nao --> J{E DMS ou REST?}
    J -- DMS --> K[Ver caminho DMS]
    J -- REST --> L[Ver entrada REST e RENOMEADOR]
```

## Resumo de uma linha

```text
Voce liga o painel -> Importador busca o que falta -> Renomeador organiza REST -> DMS vai direto -> pastas finais viram a verdade.
```
