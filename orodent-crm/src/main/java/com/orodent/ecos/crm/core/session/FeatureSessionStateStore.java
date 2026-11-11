package com.orodent.ecos.crm.core.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FeatureSessionStateStore {

    private final Map<StateKey, Object> states = new HashMap<>();

    public <T> void save(UUID operatoreId, FeatureKey feature, T state) {
        if (operatoreId == null || feature == null || state == null) {
            return;
        }
        states.put(new StateKey(operatoreId, feature), state);
    }

    public <T> Optional<T> find(UUID operatoreId, FeatureKey feature, Class<T> stateType) {
        if (operatoreId == null || feature == null || stateType == null) {
            return Optional.empty();
        }
        Object state = states.get(new StateKey(operatoreId, feature));
        return stateType.isInstance(state) ? Optional.of(stateType.cast(state)) : Optional.empty();
    }

    public void clear() {
        states.clear();
    }

    private record StateKey(UUID operatoreId, FeatureKey feature) {}
}
