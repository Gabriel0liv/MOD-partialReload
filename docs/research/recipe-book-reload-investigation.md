# Recipe book

O recipe book pertence ao estado de cada `ServerPlayer` e não é reconstruído
por `RecipeManager.replaceRecipes`. IDs removidos e desbloqueios exigem política
de cliente; por isso permanecem fora do suporte server-only.
