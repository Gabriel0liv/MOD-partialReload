# KubeJS recipe baseline

`VanillaRecipesProvider` continua sendo a única fonte correta do baseline
independente: resources JSON vanilla/Forge, conditions e serializers reais. O
`RecipeManager` ativo já pode conter transformações KubeJS e nunca pode ser
reutilizado como baseline, pois isso acumularia `remove`, replacements e
adições a cada preparação.

A Fase 4J obteve o runtime Forge 2001.6.5 real, mas não encontrou forma isolada
de aplicar os callbacks editados a esse baseline. O desenho permanece:

```text
ResourceManager -> JSON vanilla/Forge -> staging KubeJS isolado -> JSON final
-> serializers reais -> PreparedRecipes
```

O estágio central não existe na API atual; portanto o fluxo não é executado e
o baseline vanilla/Forge permanece intacto. `startup_scripts`, tags KubeJS e
addons continuam fora de escopo.
