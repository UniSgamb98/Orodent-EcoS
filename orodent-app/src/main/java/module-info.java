module com.orodent.ecos.app {
    requires com.orodent.ecos.core;
    requires com.orodent.ecos.database;
    requires com.orodent.ecos.module.api;
    uses com.orodent.ecos.module.EcosModule;
    requires javafx.controls;
    requires com.sun.jna;
    opens com.orodent.ecos.ui.windows to com.sun.jna;
    exports com.orodent.ecos.app;
    exports com.orodent.ecos;
}
