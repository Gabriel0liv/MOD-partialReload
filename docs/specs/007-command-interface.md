# Spec 007 — Interface de comandos

## 1. Contexto

Administradores precisam operar e auditar a fase read-only no chat/console.

## 2. Problema

Comandos perigosos não podem cair em fallback ou sugerir aplicação inexistente.

## 3. Objetivos

Registrar interface estável por categorias amplas, com permissão configurável.

## 4. Não objetivos

Alias `/pr`, GUI, permission APIs externas ou comandos mutáveis.

## 5. Terminologia

`status` resume serviço; `scan` observa; `changed` lista delta; `plan` cria explicação; stubs perigosos recusam.

## 6. Requisitos funcionais

- RF-007-1: implementar `status`, `categories`, `providers`, `scan`, `changed`, `plan changed`, `plan <category>`.
- RF-007-2: scan/plan exigem nível configurado (default 4); todos os subcomandos administrativos usam o mesmo requisito na fase 1.
- RF-007-3: `apply`, `reload`, `rollback` respondem “commit não implementado” e não executam fallback.
- RF-007-4: saída status inclui versão, modo, estado, providers, último scan e apply support.
- RF-007-5: categoria aceita nome público lowercase.
- RF-007-6: scan assíncrono responde início e conclusão/erro.

## 7. Requisitos não funcionais

Mensagens são concisas, sem stack trace para operador; logs preservam exceção/contexto.

## 8. Invariantes

Nenhum comando executa conteúdo ou chama reload. Literal público nunca é criado por arquivo/provider.

## 9. Modelo de erros

Input inválido usa erro Brigadier; estado ausente/ocupado retorna mensagem segura; falha async é reportada.

## 10. Riscos

Output excessivo em servidores grandes; detalhes são contados/resumidos e respeitam config.

## 11. Critérios de aceitação

Árvore contém comandos previstos, status/scan executam em GameTest, perigosos são stubs seguros, permissão é aplicada.

## 12. Cenários de teste

Console op, jogador sem nível, categoria inválida, scan inicial, plan sem scan, apply digitado.

## 13. Decisões pendentes

Paginação, localization, permission nodes e alias.

## 14. Relação com outras specs

Projeta 002/004/005/006 ao operador; configuração em 008.
