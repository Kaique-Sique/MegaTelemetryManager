package br.megazord7563.lib.telemetryManager;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.StringPublisher;
import edu.wpi.first.networktables.DoubleArrayPublisher;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.NetworkTable;

public class TelemetryEntry {

    private final Supplier<Object> valueSupplier; // lê o field atual
    private final NTPublisherWrapper publisher;   // publica no NT

    public TelemetryEntry(Field field, Object owner, String key) {

        // reflection acontece UMA VEZ só aqui
        field.setAccessible(true);
        this.valueSupplier = () -> {
            try {
                return field.get(owner); // lê valor atual do field
            } catch (Exception e) {
                return null;
            }
        };

        // cria o publisher NT correto baseado no tipo do field
        this.publisher = createPublisher(key, field.getType());
    }

    // chamado pelo TelemetryManager no periodic
    public void publish() {
        Object value = valueSupplier.get();
        if (value != null) {
            publisher.publish(value);
        }
    }

    // detecta o tipo e cria o publisher certo
    private NTPublisherWrapper createPublisher(String key, Class<?> type) {
        NetworkTable table = NetworkTableInstance.getDefault()
                                                 .getTable("Telemetry");

        if (type == double.class || type == Double.class) {
            DoublePublisher pub = table.getDoubleTopic(key).publish();
            return value -> pub.set((double) value);

        } else if (type == boolean.class || type == Boolean.class) {
            BooleanPublisher pub = table.getBooleanTopic(key).publish();
            return value -> pub.set((boolean) value);

        } else if (type == String.class) {
            StringPublisher pub = table.getStringTopic(key).publish();
            return value -> pub.set((String) value);

        } else if (type == double[].class) {
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> pub.set((double[]) value);

        } else {
            // tipo não suportado — ignora silenciosamente
            return value -> {};
        }
    }

    // interface funcional interna — só para organizar o código
    @FunctionalInterface
    private interface NTPublisherWrapper {
        void publish(Object value);
    }
}