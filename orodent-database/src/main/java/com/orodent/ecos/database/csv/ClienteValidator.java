package com.orodent.ecos.database.csv;

import com.orodent.ecos.core.csv.ModelValidator;
import com.orodent.ecos.database.model.Cliente;

import java.util.ArrayList;
import java.util.List;

public final class ClienteValidator implements ModelValidator<Cliente> {

    @Override
    public List<ValidationError> validate(Cliente cliente) {
        List<ValidationError> errors = new ArrayList<>();
        required(errors, ClienteCsvMapper.CODICE, cliente.codiceCliente(), 50);
        optional(errors, ClienteCsvMapper.CODICE_ISO, cliente.codiceIso(), 10);
        optional(errors, ClienteCsvMapper.CATEGORIA, cliente.categoria(), 100);
        optional(errors, ClienteCsvMapper.LISTINO, cliente.listino(), 100);
        optional(errors, ClienteCsvMapper.AGENTE, cliente.agente(), 150);
        optional(errors, ClienteCsvMapper.TIPO_CLIENTE, cliente.tipoCliente(), 50);
        required(errors, ClienteCsvMapper.RAGIONE_SOCIALE, cliente.ragioneSociale(), 500);
        return List.copyOf(errors);
    }

    private void required(List<ValidationError> errors, String field, String value, int max) {
        if (value == null || value.isBlank()) {
            errors.add(new ValidationError(field, "Il valore è obbligatorio"));
        } else {
            optional(errors, field, value, max);
        }
    }

    private void optional(List<ValidationError> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.add(new ValidationError(field, "La lunghezza massima è " + max + " caratteri"));
        }
    }
}
