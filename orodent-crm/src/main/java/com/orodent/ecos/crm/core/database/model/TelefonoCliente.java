package com.orodent.ecos.crm.core.database.model;

import java.util.UUID;

public record TelefonoCliente(UUID id, UUID clienteId, UUID contattoId, String descrizione) {}
