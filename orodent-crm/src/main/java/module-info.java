module com.orodent.ecos.crm {
    requires com.orodent.ecos.module.api;
    requires javafx.controls;
    requires java.sql;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.crm.CrmModule;
}
