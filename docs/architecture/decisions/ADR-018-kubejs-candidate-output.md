# ADR-018 — saída do candidato KubeJS

Status: aceito para desenho.

O output futuro será `PreparedKubeJsRecipes`, preservando `PreparedRecipes` vanilla como baseline, snapshot de scripts, mutation log, grafo, delta e proveniência. O artefato será imutável e não exporá runtime executável. Nesta fase nenhum candidato KubeJS é produzido quando o runtime exato está ausente.

