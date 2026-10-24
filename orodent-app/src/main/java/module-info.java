module com.orodent.ecos.app {
    requires com.orodent.ecos.core;
    requires com.orodent.ecos.database;
    requires com.orodent.ecos.module.api;
    uses com.orodent.ecos.module.EcosModule;
    requires javafx.controls;
    exports com.orodent.ecos.app;
    exports com.orodent.ecos;
}
