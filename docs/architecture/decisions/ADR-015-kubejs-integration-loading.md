# ADR-015 — carregamento opcional do KubeJS

Status: aceito para Fase 4B (research-only).

O mod não adiciona KubeJS em `mods.toml` nem compileOnly até existir versão Forge 1.20.1 exata. A integração futura será módulo tipado/versionado, descoberto por compatibilidade explícita. Sem runtime, recipes vanilla/Forge continuam funcionando e o provider KubeJS retorna diagnóstico fechado.

