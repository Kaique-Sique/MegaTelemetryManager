package br.megazord7563;

import java.lang.reflect.Field;
import java.util.function.Supplier;

import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.DoubleArrayPublisher;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.FloatPublisher;
import edu.wpi.first.networktables.IntegerPublisher;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;

public class TelemetryEntry {

    private final String key;
    private final Supplier<Object> valueSupplier; // lê o field atual sem reflection
    private final NTPublisherWrapper publisher;   // publica no NT

    public TelemetryEntry(Field field, Object owner, String key) {
        this.key = key;

        // reflection acontece UMA VEZ só aqui no construtor
        field.setAccessible(true);
        this.valueSupplier = () -> {
            try {
                return field.get(owner);
            } catch (Exception e) {
                System.err.println("[TelemetryManager] Erro ao ler field: " + key);
                return null;
            }
        };

        // detecta o tipo e cria o publisher NT correto
        this.publisher = createPublisher(key, field.getType());
    }

    // chamado pelo TelemetryManager no periodic — zero reflection aqui
    public void publish() {
        Object value = valueSupplier.get();
        if (value != null) {
            publisher.publish(value);
        }
    }

    public String getKey() {
        return key;
    }

    // -------------------------------------------------------------------------
    // Detecta o tipo do field e cria o publisher NT correto
    // -------------------------------------------------------------------------
    private NTPublisherWrapper createPublisher(String key, Class<?> type) {
        NetworkTable table = NetworkTableInstance.getDefault().getTable("Telemetry");

        // --- Primitivos ---
        if (type == double.class || type == Double.class) {
            DoublePublisher pub = table.getDoubleTopic(key).publish();
            return value -> pub.set((double) value);

        } else if (type == float.class || type == Float.class) {
            FloatPublisher pub = table.getFloatTopic(key).publish();
            return value -> pub.set((float) value);

        } else if (type == int.class || type == Integer.class) {
            IntegerPublisher pub = table.getIntegerTopic(key).publish();
            return value -> pub.set((int) value);

        } else if (type == long.class || type == Long.class) {
            IntegerPublisher pub = table.getIntegerTopic(key).publish();
            return value -> pub.set((long) value);

        } else if (type == boolean.class || type == Boolean.class) {
            BooleanPublisher pub = table.getBooleanTopic(key).publish();
            return value -> pub.set((boolean) value);

        } else if (type == String.class) {
            StringPublisher pub = table.getStringTopic(key).publish();
            return value -> pub.set((String) value);

        // --- Arrays primitivos ---
        } else if (type == double[].class) {
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> pub.set((double[]) value);

        // --- Tipos WPILib Geometry ---
        } else if (type == Pose2d.class) {
            // serializa como [x, y, graus]
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> {
                Pose2d pose = (Pose2d) value;
                pub.set(new double[]{
                    pose.getX(),
                    pose.getY(),
                    pose.getRotation().getDegrees()
                });
            };

        } else if (type == Pose3d.class) {
            // serializa como [x, y, z, rollDeg, pitchDeg, yawDeg]
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> {
                Pose3d pose = (Pose3d) value;
                pub.set(new double[]{
                    pose.getX(),
                    pose.getY(),
                    pose.getZ(),
                    Math.toDegrees(pose.getRotation().getX()),
                    Math.toDegrees(pose.getRotation().getY()),
                    Math.toDegrees(pose.getRotation().getZ())
                });
            };

        } else if (type == Translation2d.class) {
            // serializa como [x, y]
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> {
                Translation2d t = (Translation2d) value;
                pub.set(new double[]{ t.getX(), t.getY() });
            };

        } else if (type == Translation3d.class) {
            // serializa como [x, y, z]
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> {
                Translation3d t = (Translation3d) value;
                pub.set(new double[]{ t.getX(), t.getY(), t.getZ() });
            };

        } else if (type == Rotation2d.class) {
            // serializa como graus
            DoublePublisher pub = table.getDoubleTopic(key).publish();
            return value -> {
                Rotation2d r = (Rotation2d) value;
                pub.set(r.getDegrees());
            };

        } else if (type == ChassisSpeeds.class) {
            // serializa como [vx, vy, omega]
            DoubleArrayPublisher pub = table.getDoubleArrayTopic(key).publish();
            return value -> {
                ChassisSpeeds speeds = (ChassisSpeeds) value;
                pub.set(new double[]{
                    speeds.vxMetersPerSecond,
                    speeds.vyMetersPerSecond,
                    speeds.omegaRadiansPerSecond
                });
            };

        // --- Tipo não suportado — avisa mas não quebra o robô ---
        } else {
            System.err.println("[TelemetryManager] Tipo não suportado: "
                + type.getSimpleName() + " no campo '" + key + "' — ignorando.");
            return value -> {};
        }
    }

    // interface funcional interna — abstrai o publisher tipado
    @FunctionalInterface
    private interface NTPublisherWrapper {
        void publish(Object value);
    }
}