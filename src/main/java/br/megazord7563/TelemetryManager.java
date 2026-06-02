package br.megazord7563;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TelemetryManager {

    private static TelemetryManager instance;

    // ✅ lista de TelemetryEntry, não de Telemetry
    private final Map<Priority, List<TelemetryEntry>> entries = new HashMap<>();
    private final Map<Priority, Double> lastPublishTime = new HashMap<>();

    private TelemetryManager() {
        for (Priority p : Priority.values()) {
            entries.put(p, new ArrayList<>());
            lastPublishTime.put(p, 0.0);
        }
    }

    public static TelemetryManager getInstance() {
        if (instance == null)
            instance = new TelemetryManager();
        return instance;
    }

    public void registerInputs(Object inputsObject, String prefix) {
        Set<String> registeredKeys = new HashSet<>(); // ← evita duplicatas

        Class<?> clazz = inputsObject.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Telemetry.class)) {
                    Telemetry annotation = field.getAnnotation(Telemetry.class);
                    field.setAccessible(true);

                    String base = annotation.basePath();
                    String name = annotation.key().isEmpty()
                            ? capitalize(field.getName())
                            : annotation.key();

                    String key;
                    if (!base.isEmpty() && !prefix.isEmpty()) {
                        key = base + "/" + prefix + "/" + name;
                    } else if (!base.isEmpty()) {
                        key = base + "/" + name;
                    } else if (!prefix.isEmpty()) {
                        key = prefix + "/" + name;
                    } else {
                        key = inputsObject.getClass().getSimpleName() + "/" + name;
                    }

                    // ← só registra se a key ainda não foi registrada
                    if (!registeredKeys.contains(key)) {
                        registeredKeys.add(key);
                        entries.get(annotation.priority())
                                .add(new TelemetryEntry(field, inputsObject, key));
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public void registerInputs(Object inputsObject) {
        registerInputs(inputsObject, "");
    }

    public void periodic(double timestamp) {
        for (Priority priority : Priority.values()) { // execute para cada prioridade
            double interval = priority.getIntervalSeconds();
            double last = lastPublishTime.get(priority);

            if (timestamp - last >= interval) {
                lastPublishTime.put(priority, timestamp);

                // ✅ itera sobre TelemetryEntry
                for (TelemetryEntry entry : entries.get(priority)) { // separa por prioridade os dados a serem
                                                                     // publicados
                    entry.publish();
                }
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty())
            return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}