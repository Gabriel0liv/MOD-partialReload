# Side effects de listeners

`TagsUpdatedEvent` pode atingir listeners de mods fora do controle do Partial
Reload. O preflight limita a Fase 4E a zero players e registries estáticos; não
há garantia de compensação para efeitos externos irreversíveis. Um listener
desconhecido é diagnóstico de risco, não autorização implícita de commit.
