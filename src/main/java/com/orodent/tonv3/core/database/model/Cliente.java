package com.orodent.tonv3.core.database.model;

import java.time.LocalDateTime;

public record Cliente(
        String codiceCliente,
        String codiceIso,
        String categoria,
        String listino,
        String agente,
        String tipoCliente,
        String ragioneSociale,
        LocalDateTime importatoIl
) {
}
