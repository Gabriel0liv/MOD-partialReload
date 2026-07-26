# ADR-029 — publicação de bindings

`Registry.bindTags` é API pública em 1.20.1. O commit constrói mapas completos
`TagKey -> List<Holder<?>>` antes de chamar binding. Cada registry é validado e
capturado como geração completa; falha antes do primeiro bind não muta estado.
