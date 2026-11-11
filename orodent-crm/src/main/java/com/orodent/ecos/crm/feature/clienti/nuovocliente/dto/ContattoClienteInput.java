package com.orodent.ecos.crm.feature.clienti.nuovocliente.dto;

import java.util.List;

public record ContattoClienteInput(
        String descrizione, List<TelefonoClienteInput> telefoni, List<EmailClienteInput> email) {
    public ContattoClienteInput {
        telefoni = List.copyOf(telefoni);
        email = List.copyOf(email);
    }
}
