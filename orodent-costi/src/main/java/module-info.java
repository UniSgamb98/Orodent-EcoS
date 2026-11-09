module com.orodent.ecos.costi {
    requires com.orodent.ecos.module.api;
    requires javafx.controls;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.costi.CostiModule;
}
