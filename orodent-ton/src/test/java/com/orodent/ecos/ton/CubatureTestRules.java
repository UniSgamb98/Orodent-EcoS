package com.orodent.ecos.ton;

import com.orodent.ecos.database.CubatureRuleCatalogue;
import com.orodent.ecos.database.CubatureRuleVersions;

import java.sql.*;

public final class CubatureTestRules {
    private CubatureTestRules() {}

    public static void register(Connection c, String code) throws SQLException {
        var draft = new CubatureRuleCatalogue.Draft(code, code, "", true);
        var rule = CubatureRuleCatalogue.save(c, null, draft);
        var selected = CubatureRuleVersions.detail(c, rule.id(), null).selected();
        CubatureRuleVersions.save(c, rule, selected, draft, "D * Hmedia * P = risultato", true);
    }
}
