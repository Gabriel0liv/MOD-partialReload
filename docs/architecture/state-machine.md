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

Na fase 3B, PREPARING e VALIDATING são compartilhados globalmente pelas
preparações de functions e loot data. QUIESCING,
COMMITTING, SYNCHRONIZING, VERIFYING, SUCCESS, ROLLED_BACK e DEGRADED continuam
apenas documentados.

Transições permitidas da fase 1:

- IDLE → SCANNING;
- SCANNING → IDLE ou FAILED_SAFE;
- IDLE → PLANNING;
- PLANNING → READY ou FAILED_SAFE;
- IDLE → PREPARING;
- PREPARING → VALIDATING ou FAILED_SAFE;
- VALIDATING → READY ou FAILED_SAFE;
- READY → IDLE;
- FAILED_SAFE → IDLE.

Qualquer outra transição lança erro. Conteúdo inválido produz artefato não
aplicável em READY; falha de infraestrutura/timeout produz FAILED_SAFE. Como
nenhum estado de gameplay é alterado, DEGRADED não é usado.

Um novo prepare descarta explicitamente o artefato anterior antes de entrar em
PREPARING. Uma tentativa concorrente, inclusive functions contra loot, é
rejeitada; nenhum lock é mantido durante IO/parsing. `discard` só é permitido
fora de PREPARING e VALIDATING.
