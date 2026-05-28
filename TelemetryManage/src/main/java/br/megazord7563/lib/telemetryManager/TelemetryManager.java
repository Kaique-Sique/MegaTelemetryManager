package br.megazord7563.lib.telemetryManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TelemetryManager {

    private static TelemetryManager instance;

    private final Map<Priority, List<TelemetryEntry>> entries = new HashMap<>();
    private final Map<Priority, Double> lastPublishTime = new HashMap<>();

    private TelemetryManager() {
        for (Priority p : Priority.values()) {
            entries.put(p, new ArrayList<>());
            lastPublishTime.put(p, 0.0);
        }
    }

    public static TelemetryManager getInstance() {
        if (instance == null) instance = new TelemetryManager();
        return instance;
    }

    public void registerInputs(Object inputsObject) {
        for (Field field : inputsObject.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Telemetry.class)) {

                //lê a annotation para pegar priority e key
                Telemetry annotation = field.getAnnotation(Telemetry.class);
                field.setAccessible(true);

                String key = annotation.key().isEmpty()
                    ? inputsObject.getClass().getSimpleName()
                      + "/" + capitalize(field.getName())
                    : annotation.key();

                // cria TelemetryEntry com os dados do field
                entries.get(annotation.priority())
                       .add(new TelemetryEntry(field, inputsObject, key));
            }
        }
    }

    public void periodic(double timestamp) {
        for (Priority priority : Priority.values()) { // execute para cada prioridade
            double interval = priority.getIntervalSeconds();
            double last = lastPublishTime.get(priority);

            if (timestamp - last >= interval) {
                lastPublishTime.put(priority, timestamp);

                for (TelemetryEntry entry : entries.get(priority)) {  // separa por prioridade os dados a serem publicados
                    //System.out.println("[TelemetryManager] "+ timestamp+"Publishing " + priority + " data...");
                    entry.publish();
                }
            }
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}