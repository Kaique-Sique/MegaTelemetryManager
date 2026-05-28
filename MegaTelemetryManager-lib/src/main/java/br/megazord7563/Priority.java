package br.megazord7563;

public enum Priority {
    LOW(10),
    MEDIUM(20),
    HIGH(50);

    private final int updateFrequency; // hz

    Priority(int updateFrequency) {
        this.updateFrequency = updateFrequency;
    }

    // converte hz → intervalo em segundos
    public double getIntervalSeconds() {
        return 1.0 / updateFrequency;
    }
}