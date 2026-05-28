package br.megazord7563.robot.subsystems;

import br.megazord7563.lib.telemetryManager.Priority;
import br.megazord7563.lib.telemetryManager.Telemetry;

// Cria uma classe de teste qualquer
public class TestInputs {

    @Telemetry(priority = Priority.HIGH)
    public double speed = 0.0;

    @Telemetry(priority = Priority.LOW)
    public boolean isEnabled = false;
}