# ADR-014 — Fronteira do provider KubeJS

Status: aceito e reconfirmado pela auditoria 4J.

KubeJS permanece provider separado e opcional. A coleção vanilla preparada é a
entrada conceitual de scripts futuros; scripts nunca são executados contra o
manager ativo.

A auditoria do runtime 2001.6.5-build.26 confirmou que essa fronteira continua
necessária. Sem event registry local, o provider permanece somente de
inspeção/classificação e registra `KUBEJS_RECIPE_STAGING_NOT_ISOLATABLE`.
