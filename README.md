# MegaTelemetryManager

>[!NOTE]
>
> **Rate-limited NetworkTables telemetry for FRC robots** — publish only what you need, only when you need it.

A lightweight annotation-driven telemetry library for WPILib robots. Instead of flooding NetworkTables every loop cycle, `MegaTelemetryManager` groups fields by priority and enforces per-priority publish intervals, keeping RoboRIO CPU usage low and NT traffic clean.

---

## The Problem

During the **2025 Reefscape season**, Megazord 7563 experienced noticeable RoboRIO performance degradation caused by telemetry publishing. As the robot code grew — more subsystems, more logged fields — every field was being pushed to NetworkTables on every periodic loop (~20ms), regardless of how often that data actually changed. The symptoms were subtle at first: occasional loop overruns, dashboard lag, and CPU spikes during matches. Tracing the root cause pointed directly to NT traffic volume.

The standard pattern of calling `SmartDashboard.putNumber(...)` or `NetworkTableInstance.getDefault()...publish()` in every `periodic()` creates unnecessary overhead:

- NT entries updating at 50Hz even when the data barely changes
- Increased RoboRIO CPU usage from repeated serialization
- Dashboard lag on high-traffic NT instances

**MegaTelemetryManager** was built as a direct response to that season. It lets you declare how often each field actually needs to be published — keeping the robot fast and the NT traffic meaningful.

---

## How It Works

1. Annotate fields with `@Telemetry`, setting a `Priority`
2. Register your inputs object with `TelemetryManager`
3. Call `periodic(timestamp)` every robot loop

The manager groups entries by priority and only publishes each group when its interval has elapsed. Reflection runs **once at registration** — the hot path (`periodic`) is zero-reflection.

```
Priority.HIGH   → 50 Hz  (every 20ms)
Priority.MEDIUM → 20 Hz  (every 50ms)
Priority.LOW    → 10 Hz  (every 100ms)
```

---

## Installation

Copy the four files into your robot project under `src/main/java/br/megazord7563/`:

```
TelemetryManager.java
TelemetryEntry.java
Telemetry.java
Priority.java
```

No additional dependencies beyond WPILib.

---

## Usage

### 1. Annotate your inputs class

```java
import br.megazord7563.Telemetry;
import br.megazord7563.Priority;

public class DriveInputs {

    @Telemetry(priority = Priority.HIGH, key = "Drive/VelocityLeft")
    public double velocityLeft = 0.0;

    @Telemetry(priority = Priority.HIGH, key = "Drive/VelocityRight")
    public double velocityRight = 0.0;

    @Telemetry(priority = Priority.MEDIUM)
    public Pose2d estimatedPose = new Pose2d();

    @Telemetry(priority = Priority.LOW, key = "Drive/MotorTemp")
    public double motorTemperature = 0.0;
}
```

> If `key` is left empty, the key is auto-generated as `ClassName/FieldName`.

### 2. Register inputs in your subsystem

```java
public class DriveSubsystem extends SubsystemBase {

    private final DriveInputs inputs = new DriveInputs();

    public DriveSubsystem() {
        TelemetryManager.getInstance().registerInputs(inputs);
    }

    @Override
    public void periodic() {
        // update your inputs fields here
        inputs.velocityLeft = leftEncoder.getRate();
        inputs.velocityRight = rightEncoder.getRate();
        inputs.estimatedPose = poseEstimator.getEstimatedPosition();

        // publish to NT with rate limiting
        TelemetryManager.getInstance().periodic(Timer.getFPGATimestamp());
    }
}
```

---

## Supported Types

| Java Type         | NT Topic Type    | Serialization              |
|-------------------|------------------|----------------------------|
| `double` / `Double` | DoubleTopic    | raw value                  |
| `float` / `Float`   | FloatTopic     | raw value                  |
| `int` / `Integer`   | IntegerTopic   | raw value                  |
| `long` / `Long`     | IntegerTopic   | raw value                  |
| `boolean` / `Boolean` | BooleanTopic | raw value                  |
| `String`            | StringTopic    | raw value                  |
| `double[]`          | DoubleArrayTopic | raw array                |
| `Pose2d`            | DoubleArrayTopic | `[x, y, degrees]`        |
| `Pose3d`            | DoubleArrayTopic | `[x, y, z, roll, pitch, yaw]` |
| `Translation2d`     | DoubleArrayTopic | `[x, y]`                 |
| `Translation3d`     | DoubleArrayTopic | `[x, y, z]`              |
| `Rotation2d`        | DoubleTopic    | degrees                    |
| `ChassisSpeeds`     | DoubleArrayTopic | `[vx, vy, omega]`        |

Unsupported types log a warning and are silently skipped — the robot will not crash.

---

## Architecture

```
@Telemetry annotation
       │
       ▼
TelemetryManager.registerInputs()
  └─ reads fields via reflection (once)
  └─ creates TelemetryEntry per field
  └─ groups entries by Priority

TelemetryManager.periodic(timestamp)
  └─ for each Priority:
       └─ if elapsed >= interval → publish all entries in group
            └─ TelemetryEntry.publish()
                 └─ Supplier<Object> (lambda, no reflection)
                 └─ NTPublisherWrapper.publish(value)
```

**Key design decisions:**
- Reflection is isolated to `registerInputs()` — called once at robot init
- Each `TelemetryEntry` holds a `Supplier<Object>` lambda that reads the field without further reflection
- Each `TelemetryEntry` holds a pre-built `NTPublisherWrapper` matched to the field's type
- `periodic()` only checks timestamps and calls `publish()` — minimal overhead

---

## Priority Reference

| Priority | Frequency | Interval | Recommended for                        |
|----------|-----------|----------|----------------------------------------|
| `HIGH`   | 50 Hz     | 20 ms    | Closed-loop states, encoder velocities |
| `MEDIUM` | 20 Hz     | 50 ms    | Pose estimation, vision data           |
| `LOW`    | 10 Hz     | 100 ms   | Temperatures, battery, diagnostics     |

---

## Customizing frequencies

Frequencies are defined in `Priority.java`. To change the update rate of any priority level, edit the Hz value in the enum:

```java
public enum Priority {
    LOW(10),     // ← change this value (Hz)
    MEDIUM(20),  // ← change this value (Hz)
    HIGH(50);    // ← change this value (Hz)

    private final int updateFrequency; // hz

    Priority(int updateFrequency) {
        this.updateFrequency = updateFrequency;
    }

    public double getIntervalSeconds() {
        return 1.0 / updateFrequency;
    }
}
```

For example, to push `HIGH` fields at 100Hz instead of 50Hz, change `HIGH(50)` to `HIGH(100)`. The interval is computed automatically — no other files need to be changed.

> **Note:** publishing faster than the robot loop period (~50Hz / 20ms) has no practical effect, as `periodic()` is called once per loop.

---

## NT Table Layout

All entries are published under the `Telemetry/` table in NetworkTables:

```
/Telemetry/
  Drive/VelocityLeft        ← double
  Drive/VelocityRight       ← double
  Drive/EstimatedPose       ← double[] [x, y, degrees]
  Drive/MotorTemperature    ← double
  Shooter/FlywheelRPM       ← double
  ...
```

---

## Package

```
br.megazord7563
```

---

## Requirements

- WPILib 2026+
- Java 17+
- Run via `./gradlew simulateJava` or deploy — do not run the JAR directly

---

## License

See [LICENSE](LICENSE).

---

<p align="center">
  Built by <a href="https://megazord7563.com.br">Megazord 7563</a> · Jundiaí, SP, Brazil · FRC Team #7563
</p>
