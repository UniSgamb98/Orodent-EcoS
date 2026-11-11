package com.orodent.ecos.crm;

import com.orodent.ecos.module.*;

/** CRM from Clients DEPLOYIED fc733861891c68cf7da2b0a17bae0397da80a026. */
public final class CrmModule implements EcosModule {
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor(
                "crm",
                "CRM",
                "Lead, contatti, chiamate e calendario commerciale.",
                "M3 4h18v16H3z M7 8h4v4H7z M14 8h4 M14 12h4 M7 16h11");
    }

    public ModuleSession open(ModuleContext context) {
        return new CrmController(context);
    }
}
