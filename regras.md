Documento de Regras de Negócio do Sistema
Importação, Renomeação, Conferência e Organização de Documentos Fiscais
1. Objetivo do sistema

O sistema tem como objetivo automatizar a importação, conferência, renomeação e organização de documentos fiscais em PDF e XML.

Ele trabalha com dois tipos principais de documentos:

DMS / Serviços Tomados
REST / Serviços Prestados

O sistema deve usar a planilha Excel oficial como base central de informações.

A regra mais importante é:

A planilha é o banco de dados do sistema. O sistema deve obedecer exatamente os caminhos, CNPJs, certificados e informações cadastradas nela.

2. A planilha como banco de dados central

A planilha Excel é a fonte oficial do sistema.

Cada linha da planilha representa uma empresa.

Mesmo que várias empresas sejam do mesmo grupo econômico, cada empresa deve ser tratada de forma separada, individual e independente.

O sistema não deve trabalhar pelo grupo econômico.

Ele deve trabalhar pelo:

CNPJ da linha.

Exemplo:

Linha	Empresa	CNPJ	Caminho REST	Caminho DMS	Certificado
1	Empresa A	CNPJ A	Pasta REST A	Pasta DMS A	Certificado A
2	Empresa B	CNPJ B	Pasta REST B	Pasta DMS B	Certificado B

Mesmo que Empresa A e Empresa B sejam do mesmo grupo, o sistema deve entender:

CNPJ diferente = empresa diferente = caminho diferente = processo separado.

3. Regra principal do CNPJ

O CNPJ é a chave principal do sistema.

Sempre que o sistema estiver trabalhando, ele deve respeitar o CNPJ da linha atual da planilha.

Isso significa que:

o sistema lê a linha da planilha;
identifica o CNPJ daquela linha;
pega o certificado digital daquela linha;
pega o caminho DMS daquela linha;
pega o caminho REST daquela linha;
importa somente documentos daquele CNPJ;
salva somente nos caminhos daquele CNPJ.

O sistema não pode misturar documentos de uma empresa com outra.

4. Funcionamento ao clicar em “Ligar Sistema”

Quando o usuário clicar em Ligar Sistema, o sistema deve iniciar a rotina completa.

A ordem interna dos módulos pode variar, mas todas as regras precisam ser cumpridas.

O sistema deve:

Ler a planilha oficial em Excel.
Identificar a competência/mês escolhida no painel.
Começar a leitura das empresas linha por linha.
Pegar o CNPJ da linha atual.
Pegar o certificado digital da linha atual.
Pegar o caminho DMS da linha atual.
Pegar o caminho REST da linha atual.
Acessar o Portal Nacional usando o certificado correto.
Importar documentos DMS para o caminho DMS correto.
Importar documentos REST para a pasta de entrada REST.
Usar o Renomeador REST para organizar os documentos REST.
Conferir se os documentos que existem no Portal Nacional já estão nas pastas corretas.
Importar novamente o que estiver faltando.
Repetir a conferência até não encontrar mais documentos pendentes.
Passar para a próxima linha da planilha.
Finalizar somente depois de concluir todas as empresas.
5. Competência/mês de trabalho

O sistema deve respeitar o mês configurado no painel.

Exemplo:

Se no painel estiver configurado para trabalhar no mês atual, o sistema deve buscar, importar e conferir somente os documentos do mês vigente.

Ele não deve importar documentos de outros meses, a não ser que o usuário configure isso.

A competência deve ser respeitada em:

DMS;
REST;
PDF;
XML;
conferência no Portal Nacional;
conferência nas pastas locais;
rotina de reimportação.
6. Módulo Import API PN
6.1 Função do Import API PN

O Import API PN é o módulo responsável por acessar o Portal Nacional e importar os documentos fiscais.

Ele deve trabalhar usando as informações da planilha.

Para cada linha da planilha, ele deve:

Ler o CNPJ da empresa.
Ler o caminho do certificado digital.
Abrir o Portal Nacional com o certificado correto.
Selecionar ou validar a empresa correta pelo CNPJ.
Importar os documentos DMS.
Importar os documentos REST.
Conferir se está faltando algum documento.
Repetir a importação se faltar algo.
7. Regra do certificado digital

O sistema deve pegar o certificado digital informado na mesma linha da empresa.

O certificado pode dar acesso a mais de uma empresa, mas o sistema não pode importar tudo que aparecer no certificado.

Ele deve importar somente os documentos do CNPJ da linha atual.

Regra:

Mesmo que o certificado tenha várias empresas, o sistema só deve trabalhar com o CNPJ da linha que está sendo processada.

Exemplo:

Se a linha atual da planilha tem o CNPJ 12.345.678/0001-00, o sistema deve:

usar o certificado daquela linha;
abrir o Portal Nacional;
verificar se está trabalhando no CNPJ 12.345.678/0001-00;
importar somente documentos desse CNPJ;
ignorar outras empresas do mesmo certificado naquele momento.
8. Importação de DMS / Serviços Tomados
8.1 Como o DMS deve funcionar

Os documentos DMS devem ser importados diretamente para o caminho DMS informado na planilha.

O sistema deve:

Ler a linha do cliente.
Identificar o CNPJ.
Pegar o caminho DMS daquela linha.
Acessar o Portal Nacional com o certificado correto.
Importar os documentos DMS daquele CNPJ.
Salvar diretamente na pasta DMS correta.

Regra:

DMS não deve ser misturado com REST. DMS deve ir para o caminho DMS da linha do CNPJ correspondente.

8.2 Conferência dos documentos DMS

Depois de importar os documentos DMS, o sistema deve conferir se tudo foi realmente salvo.

A conferência deve funcionar assim:

O sistema olha no Portal Nacional quais documentos DMS existem para aquele CNPJ e mês.
Depois olha na pasta DMS informada na planilha.
Compara o que existe no Portal Nacional com o que existe na pasta.
Se faltar documento, importa novamente.
Confere outra vez.
Só conclui quando não houver mais documentos faltando.

Exemplo:

Portal Nacional mostra 20 documentos DMS.
Pasta DMS do cliente tem 18 documentos.

Sistema identifica que faltam 2.
Sistema importa novamente os 2 documentos faltantes.

Depois confere outra vez.
Se agora a pasta tiver os 20 documentos, DMS daquele cliente está concluído.
9. Importação de REST / Serviços Prestados
9.1 Como o REST deve funcionar

Os documentos REST devem ser importados pelo Import API PN, mas a organização final deles é responsabilidade do Renomeador REST.

O Import API PN deve baixar os documentos REST e colocar na pasta central de entrada REST.

Exemplo de pasta:

Import API PN Entrada REST

Essa pasta funciona como uma entrada temporária.

Depois disso, o Renomeador REST deve identificar o CNPJ dos arquivos e mandar cada documento para o caminho REST correto da empresa.

10. Pasta central de entrada REST

A pasta Import API PN Entrada REST recebe os documentos REST importados do Portal Nacional.

Ela pode receber:

PDFs REST;
XMLs REST;
arquivos de vários CNPJs;
documentos pendentes de organização.

Mas ela não é o destino final.

O destino final sempre será o caminho REST informado na planilha para o CNPJ daquele documento.

Regra:

Tudo que entrar na pasta de entrada REST deve ser analisado pelo Renomeador, identificado pelo CNPJ e enviado para o caminho REST correto da planilha.

11. Módulo Renomeador REST
11.1 Função do Renomeador

O Renomeador REST é responsável por organizar os documentos REST.

Ele deve:

Olhar a pasta de entrada REST.
Olhar também os caminhos REST indicados na planilha, se necessário.
Ler os arquivos PDF e XML.
Identificar o CNPJ dentro de cada documento.
Procurar esse CNPJ na planilha.
Encontrar o caminho REST correto.
Renomear o arquivo.
Organizar o arquivo na pasta correta.
Separar documentos retidos e não retidos.
Colocar arquivos problemáticos em revisão, sem misturar empresas.
11.2 Regra de destino do Renomeador

O Renomeador não escolhe o caminho sozinho.

Ele deve sempre seguir a planilha.

Fluxo correto:

Documento REST encontrado
↓
Sistema lê o CNPJ do documento
↓
Sistema procura esse CNPJ na planilha
↓
Sistema encontra a linha correta
↓
Sistema pega o caminho REST daquela linha
↓
Sistema renomeia o arquivo
↓
Sistema envia para o caminho REST correto

Regra:

O caminho REST da planilha é o destino oficial.

11.3 Documentos retidos e não retidos

O Renomeador também é responsável por identificar se o documento REST é:

retido;
não retido.

Essa regra já está implementada no sistema.

Então, além de renomear, ele deve organizar os documentos conforme essa classificação.

Exemplo:

Caminho REST do Cliente
├── Retidos
├── Não Retidos
├── XML
├── PDF
└── Revisão

A estrutura exata pode variar conforme o sistema, mas a lógica deve ser mantida.

12. Arquivos não reconhecidos

Se o sistema não conseguir identificar um PDF ou XML, ele não deve jogar esse arquivo em qualquer lugar.

Ele pode criar uma pasta de revisão.

Exemplos:

PDF não reconhecido
XML não reconhecido
Revisão
Não identificado

Mas a regra é:

Arquivo não reconhecido deve permanecer dentro da estrutura controlada pelo sistema, sem ser enviado para pasta de outro cliente.

Se o CNPJ não for identificado, o sistema não deve inventar o cliente.

Ele deve separar para revisão.

13. Conferência dos documentos REST

A conferência REST deve usar o caminho REST final do cliente.

Mesmo que o Import API PN baixe primeiro para a pasta de entrada REST, a conferência final deve olhar a pasta oficial REST da empresa.

O sistema deve:

Ler o CNPJ da linha atual.
Pegar o caminho REST da linha.
Verificar quais documentos REST daquele mês existem nessa pasta.
Comparar com o que existe no Portal Nacional.
Verificar se falta algum PDF ou XML.
Se faltar, importar novamente.
Baixar o documento para a pasta de entrada REST.
O Renomeador identifica, renomeia e envia para o caminho REST correto.
O sistema confere novamente a pasta REST final.

Regra:

Quem define se falta importar algo é a pasta final do cliente, não apenas a pasta de entrada REST.

14. Regra de repetição da importação

O sistema não deve importar uma vez e parar sem conferir.

Ele deve trabalhar em ciclo:

Importa
↓
Confere a pasta correta
↓
Compara com o Portal Nacional
↓
Se faltar, importa novamente
↓
Confere novamente
↓
Quando não faltar nada, conclui

Se o sistema importar e perceber que ainda falta documento, ele deve tentar novamente.

A rotina só deve ser considerada concluída quando o sistema conferir que não há mais documentos pendentes para aquele CNPJ e competência.

15. Regra de duas conferências sem novos documentos

Quando o sistema fizer a conferência e perceber que não entrou mais nenhuma nota nova depois de repetir a verificação, ele pode considerar aquele tipo de documento como concluído.

Exemplo:

Primeira conferência:
Sistema verifica se falta documento.

Segunda conferência:
Sistema verifica novamente.

Se não aparecer mais nada novo e não houver pendência, conclui o cliente.

Essa regra evita que o sistema pare antes da hora.

16. Processamento linha por linha

O sistema deve seguir a planilha de cima para baixo.

Para cada linha, ele deve fazer o processo completo daquele CNPJ.

Fluxo:

Ler linha da planilha
↓
Identificar CNPJ
↓
Pegar certificado digital
↓
Pegar caminho DMS
↓
Pegar caminho REST
↓
Acessar Portal Nacional
↓
Importar e conferir DMS
↓
Importar e conferir REST
↓
Renomear e organizar REST
↓
Conferir se ainda falta algo
↓
Se faltar, repetir importação
↓
Se não faltar, concluir linha
↓
Ir para próxima linha
17. Agendamento automático

O sistema deve funcionar automaticamente em horários programados.

Horários definidos:

05:00 da manhã
12:00
17:00

Quando o sistema iniciar em um desses horários, ele não deve parar no meio.

Ele só deve finalizar quando terminar a rotina completa.

Exemplo:

Se o sistema iniciou às 05:00, ele deve continuar rodando até:

passar por todas as linhas da planilha;
conferir todos os CNPJs;
importar todos os documentos pendentes;
organizar REST;
conferir DMS;
conferir REST;
garantir que não há mais documentos faltando.

Regra:

O sistema não para por horário. Ele para quando termina o serviço.

18. Regra de não misturar empresas

Essa é uma das regras mais importantes.

O sistema nunca deve misturar:

empresas diferentes;
CNPJs diferentes;
documentos de grupos econômicos diferentes;
documentos de empresas do mesmo grupo;
pastas REST;
pastas DMS;
certificados;
documentos reconhecidos;
documentos não reconhecidos.

A regra é:

Se o documento pertence ao CNPJ X, ele deve ir para o caminho do CNPJ X.

Não importa se o grupo tem várias empresas.

O sistema deve obedecer o CNPJ da linha.

19. Regras de erro e revisão

Se acontecer algum erro, o sistema deve registrar o problema e não bagunçar os documentos.

Exemplos de erro:

CNPJ não encontrado no documento;
CNPJ encontrado, mas não existe na planilha;
certificado digital não encontrado;
caminho DMS vazio;
caminho REST vazio;
Portal Nacional não abriu corretamente;
arquivo baixado com erro;
PDF ilegível;
XML inválido;
documento duplicado;
documento não reconhecido.

Quando isso acontecer, o sistema deve:

Não enviar o arquivo para cliente errado.
Não apagar o arquivo sem registro.
Colocar em pasta de revisão.
Registrar o motivo do erro.
Continuar o processo dos outros clientes, se possível.
20. Responsabilidade de cada parte do sistema
20.1 Planilha Excel

A planilha é responsável por guardar:

CNPJ;
nome da empresa;
caminho REST;
caminho DMS;
certificado digital;
informações necessárias para importação;
dados usados para localizar e organizar os arquivos.
20.2 Import API PN

O Import API PN é responsável por:

acessar o Portal Nacional;
usar o certificado digital correto;
abrir a empresa correta pelo CNPJ;
importar DMS;
importar REST;
comparar Portal Nacional com as pastas locais;
importar novamente documentos faltantes;
respeitar a competência/mês configurada;
respeitar cada linha da planilha.
20.3 Renomeador REST

O Renomeador REST é responsável por:

ler documentos REST;
identificar CNPJ;
procurar CNPJ na planilha;
descobrir caminho REST correto;
renomear arquivos;
organizar arquivos;
separar retidos e não retidos;
colocar arquivos problemáticos em revisão;
não misturar documentos de clientes diferentes.
21. Critério de conclusão do sistema

O sistema só pode finalizar o ciclo quando:

todas as linhas da planilha forem processadas;
todos os CNPJs forem conferidos;
todos os certificados necessários forem usados;
todos os caminhos DMS forem conferidos;
todos os caminhos REST forem conferidos;
todos os documentos DMS faltantes forem importados;
todos os documentos REST faltantes forem importados;
o Renomeador REST tiver organizado os documentos;
não houver mais documentos pendentes no Portal Nacional para a competência configurada;
os arquivos não reconhecidos estiverem separados para revisão.
22. Frase simples da regra do sistema

O sistema deve funcionar como um robô fiscal que lê a planilha como banco de dados, trata cada linha como uma empresa única pelo CNPJ, usa o certificado digital correto, acessa o Portal Nacional, importa DMS direto para o caminho DMS, importa REST para a entrada REST, usa o Renomeador para enviar REST ao caminho correto, confere se falta algum documento e só finaliza quando todos os clientes da planilha estiverem completos.

23. Regra final absoluta

A planilha manda.
O CNPJ manda.
O caminho da linha manda.
O certificado da linha manda.
O grupo econômico não manda.
O sistema nunca deve misturar empresas.