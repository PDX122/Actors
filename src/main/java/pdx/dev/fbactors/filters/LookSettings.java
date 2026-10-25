package pdx.dev.fbactors.filters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LookSettings {
    public static final int MAX_FILTERS = 3;
    public boolean enabled = false;
    public float intensity = 1;
    public float exposure = 0;
    public float contrast = 1;
    public float saturation = 1;
    public float temperature = 0;
    public float tint = 0;
    public float vignette = 0;
    public float grain = 0;
    public List<String> filters = new ArrayList<>();
    public Map<String, Map<String, Float>> tuning = new LinkedHashMap<>();

    public LookSettings copy() {
        LookSettings copy = new LookSettings();
        copy.enabled = enabled;
        copy.intensity = intensity;
        copy.exposure = exposure;
        copy.contrast = contrast;
        copy.saturation = saturation;
        copy.temperature = temperature;
        copy.tint = tint;
        copy.vignette = vignette;
        copy.grain = grain;
        copy.filters.addAll(filters);
        tuning.forEach((key, value) -> copy.tuning.put(key, new LinkedHashMap<>(value)));
        return copy;
    }

    public void normalize() {
        intensity = bounded(intensity, 0, 1, 1);
        exposure = bounded(exposure, -3, 3, 0);
        contrast = bounded(contrast, 0.5f, 1.8f, 1);
        saturation = bounded(saturation, 0, 2.5f, 1);
        temperature = bounded(temperature, -1, 1, 0);
        tint = bounded(tint, -1, 1, 0);
        vignette = bounded(vignette, 0, 1, 0);
        grain = bounded(grain, 0, 0.2f, 0);
        if (filters == null) filters = new ArrayList<>();
        filters = new ArrayList<>(filters.stream().filter(id -> FilterCatalog.find(id) != null)
            .distinct().limit(MAX_FILTERS).toList());
        Map<String, Map<String, Float>> clean = new LinkedHashMap<>();
        if (tuning != null) tuning.forEach((id, values) -> {
            if (FilterCatalog.find(id) == null || values == null) return;
            Map<String, Float> params = new LinkedHashMap<>();
            for (var param : FilterCatalog.params(id)) {
                Float value = values.get(param.id());
                if (value != null) params.put(param.id(), bounded(value, param.min(), param.max(), param.value()));
            }
            clean.put(id, params);
        });
        tuning = clean;
    }

    public boolean add(String id) {
        if (FilterCatalog.find(id) == null || filters.contains(id) || filters.size() >= MAX_FILTERS) return false;
        filters.add(id);
        enabled = true;
        return true;
    }

    public float parameter(String id, FilterCatalog.Param param) {
        return tuning.getOrDefault(id, Map.of()).getOrDefault(param.id(), param.value());
    }

    public void parameter(String id, FilterCatalog.Param param, float value) {
        tuning.computeIfAbsent(id, key -> new LinkedHashMap<>()).put(param.id(),
            bounded(value, param.min(), param.max(), param.value()));
    }

    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
