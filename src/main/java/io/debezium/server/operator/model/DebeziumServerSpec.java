package io.debezium.server.operator.model;

public class DebeziumServerSpec {

    private int replicas;
    private String version;
    private Config config;
    private Component sourceSystem;
    private Component targetSystem;

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Component getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(Component sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Component getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(Component targetSystem) {
        this.targetSystem = targetSystem;
    }

}
