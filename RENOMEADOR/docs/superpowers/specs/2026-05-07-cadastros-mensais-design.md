# Cadastros Mensais da Planilha Fiscal

## Decisao

A planilha fiscal passa a ter uma aba de cadastro por mes operacional, em vez de um unico `CADASTRO`.

- `CADASTRO ABRIL` preserva os caminhos atuais.
- `CADASTRO MAIO` ate `CADASTRO DEZEMBRO` replicam os dados fixos dos clientes e deixam em branco os caminhos mensais.
- Dados fixos: cliente, cidade, CNPJ, certificado digital, validade, senha e `SOMENTE ORIGEM`.
- Dados mensais: `CAMINHO DMS`, `CAMINHO REST` e `CAMINHO ENTRADA/SAIDA`.
- `CAMINHO ENTRADAS` e `CAMINHO SAIDAS` viram uma unica coluna `CAMINHO ENTRADA/SAIDA`.

## Uso Pelo Sistema

Quando importar a planilha sem aba explicita, o sistema escolhe a aba mensal pelo mes de execucao.

- `--mes 2026-05` usa `CADASTRO MAIO`.
- Sem `--mes`, usa o mes atual do computador.
- Se a aba mensal nao existir, o importador cai para `CADASTRO`, depois `CADASTRO ABRIL`, preservando compatibilidade.

## Observacao Sobre CNPJ Amarelo

CNPJ amarelo no cadastro e alerta de validacao: o CNPJ nao passou na regra tecnica ou esta incompleto. Para cliente de destino, deve ser corrigido. Para pasta generica de origem, pode permanecer desde que `SOMENTE ORIGEM` esteja marcado como `SIM`.
