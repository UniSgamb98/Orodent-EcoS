package com.orodent.ecos.crm.feature.clienti.nuovocliente.dto;

import java.util.List;

public record NuovoClienteRequest(
        ClienteInput cliente,
        List<IndirizzoClienteInput> indirizzi,
        List<TelefonoClienteInput> telefoni,
        List<EmailClienteInput> email,
        List<SitoWebClienteInput> sitiWeb,
        List<ContattoClienteInput> contatti) {
    public NuovoClienteRequest {
        indirizzi = List.copyOf(indirizzi);
        telefoni = List.copyOf(telefoni);
        email = List.copyOf(email);
        sitiWeb = List.copyOf(sitiWeb);
        contatti = List.copyOf(contatti);
    }
}
