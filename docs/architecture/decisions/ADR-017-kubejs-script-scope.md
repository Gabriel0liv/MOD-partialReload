# ADR-017 — escopo de scripts KubeJS

Status: aceito para a fase research-only.

`server_scripts` são classificados, mas não executados. `startup_scripts` têm status `RESTART_REQUIRED`; `client_scripts` são ignorados no servidor; server scripts mistos ou com efeitos desconhecidos são `BLOCKER`. A política futura preferida é executar somente handlers de recipes já compilados, após prova de isolamento.

