package com.orodent.ecos.crm.feature.clienti.navigator;

import java.util.UUID;

public interface ClientiNav {
    void showClienti();

    void showNuovoCliente();

    void showSchedaCliente(UUID clienteId);
}
