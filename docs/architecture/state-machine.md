# Máquina de estados

```text
                  +--> SCANNING --success--> IDLE
                  |       |
                  |       +--failure--> FAILED_SAFE --ack--> IDLE
IDLE -------------+
                  |--> PLANNING --success--> READY --new operation--> IDLE
                          |
                          +--failure--> FAILED_SAFE
```

Estados especificados para o lifecycle completo:

```text
IDLE SCANNING PLANNING PREPARING VALIDATING READY QUIESCING COMMITTING
SYNCHRONIZING VERIFYING SUCCESS ROLLED_BACK FAILED_SAFE DEGRADED
```

Na fase 1 apenas IDLE, SCANNING, PLANNING, READY e FAILED_SAFE são alcançáveis por operações. Enums dos demais estados documentam o protocolo futuro; não existem comandos ou métodos que os executem.

Transições permitidas da fase 1:

- IDLE → SCANNING;
- SCANNING → IDLE ou FAILED_SAFE;
- IDLE → PLANNING;
- PLANNING → READY ou FAILED_SAFE;
- READY → IDLE;
- FAILED_SAFE → IDLE.

Qualquer outra transição lança erro. Uma falha read-only é sempre `FAILED_SAFE`, nunca `DEGRADED`, pois nenhum estado de gameplay foi alterado.
