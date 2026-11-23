module com.orodent.ecos.registro {
    requires com.orodent.ecos.module.api;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.registro.RegistroModule;
}
