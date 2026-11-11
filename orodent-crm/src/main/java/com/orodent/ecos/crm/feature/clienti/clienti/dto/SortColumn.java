package com.orodent.ecos.crm.feature.clienti.clienti.dto;

public enum SortColumn {
    NAME("C.RAGIONE_SOCIALE"),
    TYPE("C.TIPO_CLIENTE"),
    CONTACT("REFERENTE"),
    OPERATOR("OPERATORE"),
    STATUS("C.STATO_TRATTATIVA"),
    LAST_CONTACT("ULTIMO_CONTATTO");

    private final String sqlColumn;

    SortColumn(String sqlColumn) {
        this.sqlColumn = sqlColumn;
    }

    public String sqlColumn() {
        return sqlColumn;
    }
}
