# ADR-024 — view candidata de tags

Adota-se uma view read-only construída exclusivamente de `PreparedTags`.
Ela preserva ordem, diferencia ausência de tag de tag vazia e nunca expõe
holders ou bindings ativos. Conditions não recebem contexto candidato nesta
fase; dependências inseguras são bloqueadas.
