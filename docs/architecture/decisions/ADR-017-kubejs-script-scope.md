# ADR-017 — escopo de scripts KubeJS

Status: aceito e preservado após a auditoria 4J.

`server_scripts` são classificados, mas não executados. `startup_scripts` têm
status `RESTART_REQUIRED`; `client_scripts` são ignorados no servidor; server
scripts mistos ou com efeitos desconhecidos são `BLOCKER`.

Handlers ativos representam scripts antigos. Recompilar os editados exigiria
mutar listeners globais; portanto nem scripts `RECIPE_EVENT_ONLY` são
executáveis enquanto o stop gate estiver ativo.
