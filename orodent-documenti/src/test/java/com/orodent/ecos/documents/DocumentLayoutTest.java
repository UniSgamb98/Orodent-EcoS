package com.orodent.ecos.documents;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import org.junit.jupiter.api.Test;

class DocumentLayoutTest {
    static String object =
            """
{"type":"canvasObject","attrs":{"id":"obj-1","kind":"text","x":20,"y":30,"width":180,"height":42,"z":0,"page":0,"fontFamily":"Arial","fontSize":"14pt","lineHeight":1.2,"color":"#000000","textAlign":"left","bold":false,"italic":false,"underline":false,"keepRatio":false},"content":[{"type":"paragraph","content":[{"type":"text","text":"Etichetta"}]}]}
""";

    static JsonObject model() {
        return JsonParser.parseString(
                        "{\"page\":{\"format\":\"custom\",\"orientation\":\"portrait\",\"width\":102,\"height\":38,\"margin\":3,\"count\":1},\"layout\":\"canvas\",\"content\":{\"type\":\"doc\",\"content\":["
                                + object
                                + "]}}")
                .getAsJsonObject();
    }

    @Test
    void preservesPhysicalGeometryAndRejectsObjectsOutsideThePageOrInvalidStyles() {
        assertDoesNotThrow(() -> DocumentLayout.validate(model()));
        for (String name : new String[] {"x", "y", "width", "height", "page", "z"}) {
            var m = model();
            var attrs =
                    m.getAsJsonObject("content")
                            .getAsJsonArray("content")
                            .get(0)
                            .getAsJsonObject()
                            .getAsJsonObject("attrs");
            attrs.addProperty(name, name.equals("z") ? -1 : 1000);
            assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(m));
        }
        var duplicate = model();
        duplicate
                .getAsJsonObject("content")
                .getAsJsonArray("content")
                .add(JsonParser.parseString(object));
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(duplicate));
        var inject = model();
        inject.getAsJsonObject("content")
                .getAsJsonArray("content")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("attrs")
                .addProperty("fontSize", "14pt;position:fixed");
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(inject));
    }

    @Test
    void automaticFieldsCannotContainEditableTextOrMultipleValues() {
        var model = model();
        var object =
                model.getAsJsonObject("content").getAsJsonArray("content").get(0).getAsJsonObject();
        object.getAsJsonObject("attrs").addProperty("kind", "field");
        var field = JsonParser.parseString("{\"type\":\"labelValue\",\"attrs\":{\"key\":\"lot\"}}");
        var content = new JsonArray();
        content.add(field);
        object.add("content", content);
        assertDoesNotThrow(() -> DocumentLayout.validate(model));
        content.add(field.deepCopy());
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(model));
        content.remove(1);
        object.getAsJsonObject("attrs").addProperty("kind", "text");
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(model));
    }

    @Test
    void persistentGroupsRequireAtLeastTwoMembersOnTheSamePage() {
        var model = model();
        model.addProperty("schemaVersion", 7);
        var children = model.getAsJsonObject("content").getAsJsonArray("content");
        var first = children.get(0).getAsJsonObject();
        first.getAsJsonObject("attrs").addProperty("groupId", "grp-test");
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(model));
        var second = first.deepCopy();
        second.getAsJsonObject("attrs").addProperty("id", "obj-2");
        children.add(second);
        assertDoesNotThrow(() -> DocumentLayout.validate(model));
        model.addProperty("schemaVersion", 6);
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(model));
        model.addProperty("schemaVersion", 7);
        model.getAsJsonObject("page").addProperty("count", 2);
        second.getAsJsonObject("attrs").addProperty("page", 1);
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(model));
        second.getAsJsonObject("attrs").addProperty("page", 0);
        first.getAsJsonObject("attrs").addProperty("groupId", "invalid group!");
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(model));
    }

    @Test
    void permitsEmptyDraftsAndLegacyFlowButKeepsLabelsToOnePage() {
        var blank = model();
        blank.getAsJsonObject("content").add("content", new JsonArray());
        assertDoesNotThrow(() -> DocumentLayout.validate(blank));
        var multi = model();
        multi.addProperty("purpose", "ETICHETTA");
        multi.getAsJsonObject("page").addProperty("count", 2);
        assertThrows(IllegalArgumentException.class, () -> DocumentLayout.validate(multi));
        var flow =
                JsonParser.parseString(
                                "{\"page\":{\"orientation\":\"portrait\",\"margin\":15},\"content\":{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}}")
                        .getAsJsonObject();
        assertDoesNotThrow(() -> DocumentLayout.validate(flow));
    }
}
