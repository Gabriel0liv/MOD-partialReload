# ADR-039 — quiescência e menus

Antes do commit, clientes devem fechar ou marcar como seguros menus recipe-sensitive.
Menus desconhecidos bloqueiam a transação; nenhuma UI é considerada coerente sem ACK.
