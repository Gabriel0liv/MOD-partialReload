# ADR-033 — geração de rollback

Uma única `ActiveTagRecipeGeneration` é retida em memória. Ela contém a
coleção completa de recipes e bindings completos de todos os registries
publicados. Após rollback bem-sucedido a retenção é consumida.
