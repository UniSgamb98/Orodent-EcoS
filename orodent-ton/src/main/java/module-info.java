module com.orodent.ecos.ton {
    requires com.orodent.ecos.module.api;
    requires transitive javafx.controls;
    provides com.orodent.ecos.module.EcosModule with com.orodent.ecos.ton.TonModule;
    exports com.orodent.ecos.ton.view;
}
