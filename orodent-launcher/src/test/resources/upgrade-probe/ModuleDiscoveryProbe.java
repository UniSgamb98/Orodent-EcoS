import java.util.List;

/** Resolves providers through JPMS against the actual downloaded distribution, without a display. */
class ModuleDiscoveryProbe {
    public static void main(String[] args) throws Exception {
        Class<?> catalogType = Class.forName("com.orodent.ecos.app.modules.ModuleCatalog");
        Object catalog = catalogType.getMethod("discover").invoke(null);
        List<?> descriptors = (List<?>) catalogType.getMethod("descriptors").invoke(catalog);
        var ids = new java.util.ArrayList<String>();
        for (Object descriptor : descriptors) {
            ids.add((String) descriptor.getClass().getMethod("id").invoke(descriptor));
        }
        if (!ids.equals(List.of("statistiche", "ton"))) throw new AssertionError("Provider JPMS mancanti: " + ids);
        System.out.println("DISCOVERED statistiche,ton");
    }
}
