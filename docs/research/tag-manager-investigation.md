# Investigação do TagManager

No mapeamento 1.20.1, `TagLoader` lê arquivos por diretório e produz mapas de
tags; `TagManager` agrega loaders de registries. `TagEntry` representa elemento
ou referência a outra tag, e `TagFile` contém `replace` e `values`. O binding
ocorre depois, por registries/holders, e não é parte do candidato desta fase.

`ResourceManager.listResourceStacks` preserva todas as contribuições de packs;
o provider usa essa visão, não somente o winner. Function tags são um domínio
separado de `ServerFunctionManager`.

