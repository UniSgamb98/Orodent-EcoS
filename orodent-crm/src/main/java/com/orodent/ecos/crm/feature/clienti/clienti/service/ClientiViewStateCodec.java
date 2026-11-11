package com.orodent.ecos.crm.feature.clienti.clienti.service;

import com.orodent.ecos.crm.core.database.model.VersionedViewPayload;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.ClientiViewState;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.OperatoreFilter;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.SortColumn;
import com.orodent.ecos.crm.feature.clienti.clienti.dto.TextFilter;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.UUID;

public final class ClientiViewStateCodec {

    private static final int CURRENT_VERSION = 1;

    public VersionedViewPayload encode(ClientiViewState state) {
        Properties properties = new Properties();
        properties.setProperty("searchText", state.searchText());
        properties.setProperty("operatoreId", value(state.operatore().id()));
        properties.setProperty("operatoreLabel", state.operatore().label());
        properties.setProperty("tipologia", state.tipologia().value());
        properties.setProperty("stato", state.stato().value());
        properties.setProperty("sortColumn", state.sortColumn().name());
        properties.setProperty("ascending", Boolean.toString(state.ascending()));

        try (StringWriter writer = new StringWriter()) {
            properties.store(writer, null);
            return new VersionedViewPayload(CURRENT_VERSION, writer.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Impossibile codificare la ricerca clienti.", e);
        }
    }

    public ClientiViewState decode(VersionedViewPayload payload) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(payload.configuration()));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Configurazione della ricerca clienti non valida.", e);
        }

        ClientiViewState defaults = ClientiViewState.initial();
        UUID operatoreId = uuid(properties.getProperty("operatoreId"));
        String operatoreLabel = properties.getProperty("operatoreLabel", "Tutti");
        String tipologia = properties.getProperty("tipologia", defaults.tipologia().value());
        String stato = properties.getProperty("stato", defaults.stato().value());

        return new ClientiViewState(
                properties.getProperty("searchText", defaults.searchText()),
                operatoreId == null
                        ? OperatoreFilter.empty()
                        : new OperatoreFilter(operatoreId, operatoreLabel),
                tipologia.isBlank()
                        ? TextFilter.empty("Tutti")
                        : new TextFilter(tipologia, tipologia),
                stato.isBlank() ? TextFilter.empty("Tutti") : new TextFilter(stato, stato),
                sortColumn(properties.getProperty("sortColumn"), defaults.sortColumn()),
                booleanValue(properties.getProperty("ascending"), defaults.ascending()));
    }

    private String value(UUID id) {
        return id == null ? "" : id.toString();
    }

    private UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private SortColumn sortColumn(String value, SortColumn defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return SortColumn.valueOf(value);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private boolean booleanValue(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase()) {
            case "true" -> true;
            case "false" -> false;
            default -> defaultValue;
        };
    }
}
