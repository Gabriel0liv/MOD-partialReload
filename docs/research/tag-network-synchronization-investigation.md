# Investigação de sincronização de tags

O reload normal vincula holders e envia atualização de tags aos clientes por
packet vanilla. Esta fase não chama binding, eventos ou packet. Recipes, loot e
advancements que consultam tags continuam usando bindings ativos; por isso o
blocker de composição recipes+tags permanece.

