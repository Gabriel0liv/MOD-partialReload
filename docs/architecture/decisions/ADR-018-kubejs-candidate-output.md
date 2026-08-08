# ADR-018 — saída do candidato KubeJS

Status: desenho preservado; candidato não implementado.

O output futuro será `PreparedKubeJsRecipes`, preservando `PreparedRecipes`
vanilla como baseline, snapshot de scripts, mutation log, grafo, delta e
proveniência. O artefato será imutável e não exporá runtime executável.

O runtime exato foi localizado, mas nenhum candidato é produzido quando ele
está presente: `KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`. O desenho só volta a ser
implementável após a seam definida no ADR-050.
