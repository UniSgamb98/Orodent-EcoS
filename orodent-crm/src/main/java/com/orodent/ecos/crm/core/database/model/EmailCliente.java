package com.orodent.ecos.crm.core.database.model;

import java.util.UUID;

public record EmailCliente(UUID id, UUID clienteId, UUID contattoId, String descrizione) {}
