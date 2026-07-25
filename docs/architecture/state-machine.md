# Máquina de estados

```text
                  +--> SCANNING --success--> IDLE/READY*
                  |       |
                  |       +--failure--> FAILED_SAFE --ack--> IDLE
IDLE -------------+
                  |--> PLANNING --success--> READY --new operation--> IDLE
                  |       |
                  |       +--failure--> FAILED_SAFE
                  |
                  +--> PREPARING --> VALIDATING --> READY
                          |              |
                          +--------------+--failure--> FAILED_SAFE
```

`*` Um scan retorna a READY quando um artefato preparado anterior permanece
armazenado; o scan nunca promove snapshot nem altera o artefato.

Estados especificados para o lifecycle completo:

```text
IDLE SCANNING PLANNING PREPARING VALIDATING READY QUIESCING COMMITTING
SYNCHRONIZING VERIFYING SUCCESS ROLLED_BACK FAILED_SAFE DEGRADED
```

Na fase 3A, functions podem percorrer QUIESCING → COMMITTING → VERIFYING →
SUCCESS, ou ROLLED_BACK/DEGRADED em falha. Loot data continua limitado a
PREPARING/VALIDATING/READY.

Transições permitidas:

- IDLE → SCANNING;
- SCANNING → IDLE ou FAILED_SAFE;
- IDLE → PLANNING;
- PLANNING → READY ou FAILED_SAFE;
- IDLE → PREPARING;
- PREPARING → VALIDATING ou FAILED_SAFE;
- VALIDATING → READY ou FAILED_SAFE;
- READY → IDLE ou QUIESCING;
- QUIESCING → COMMITTING ou FAILED_SAFE;
- COMMITTING → VERIFYING, ROLLED_BACK ou DEGRADED;
- VERIFYING → SUCCESS, ROLLED_BACK ou DEGRADED;
- SUCCESS → IDLE ou QUIESCING;
- ROLLED_BACK → IDLE;
- DEGRADED → IDLE somente após reinício;
- FAILED_SAFE → IDLE.

Qualquer outra transição lança erro. Conteúdo inválido produz artefato não
aplicável em READY; falha de infraestrutura/timeout produz FAILED_SAFE.
DEGRADED bloqueia operações mutáveis e exige reinício.

Um novo prepare descarta explicitamente o artefato anterior antes de entrar em
PREPARING. Uma tentativa concorrente, inclusive functions contra loot, é
rejeitada; nenhum lock é mantido durante IO/parsing. `discard` só é permitido
fora de PREPARING e VALIDATING.

Preparação de recipes usa exatamente `PREPARING → VALIDATING → READY` e não
cria estados de commit. `apply prepared` rejeita `PreparedRecipes`, mantendo o
artefato e o manager ativo inalterados.
