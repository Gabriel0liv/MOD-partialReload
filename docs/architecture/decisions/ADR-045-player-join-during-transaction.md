# ADR-045 — join durante transação

Join durante preparação/quiescência aborta antes da mutação. Join após mutação
é recusado ou mantido fora do quorum até receber uma geração completa.
