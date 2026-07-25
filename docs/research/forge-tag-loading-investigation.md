# Investigação de tags Forge

Forge 47.4.10 usa os listeners vanilla para tags e dispara atualização após o
binding normal. APIs como `Registry.bindTags` e eventos/hook de tags alteram o
estado ativo e são proibidas na preparação. A representação Forge de remoções
não foi assumida genericamente; o provider registra `remove` como operação
candidata e requer validação adicional antes de prometer semântica Forge.

