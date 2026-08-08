# ADR-015 — carregamento opcional do KubeJS

Status: aceito; runtime 2001 auditado, adapter não carregável com segurança.

O mod não adiciona KubeJS em `mods.toml`. A versão Forge 1.20.1 exata existe e
foi auditada, porém não fornece staging isolado; por isso nenhum adapter runtime
foi criado nem carregado. Sem runtime, recipes vanilla/Forge continuam
funcionando. Com runtime 2001, a integração continua fechada até existir API
injetável conforme ADR-050.
