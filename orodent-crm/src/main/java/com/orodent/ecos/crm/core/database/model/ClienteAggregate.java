package com.orodent.ecos.crm.core.database.model;

import java.util.List;

public record ClienteAggregate(
        Cliente cliente,
        List<IndirizzoCliente> indirizzi,
        List<SitoWebCliente> sitiWeb,
        List<TelefonoCliente> telefoni,
        List<EmailCliente> email,
        List<ContattoCliente> contatti) {
    public ClienteAggregate {
        indirizzi = List.copyOf(indirizzi);
        sitiWeb = List.copyOf(sitiWeb);
        telefoni = List.copyOf(telefoni);
        email = List.copyOf(email);
        contatti = List.copyOf(contatti);
    }
}
