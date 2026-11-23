module com.orodent.ecos.misurazioni {
    requires com.orodent.ecos.module.api;
    requires j2mod;

    provides com.orodent.ecos.module.EcosModule with
            com.orodent.ecos.misurazioni.MisurazioniModule;
}
