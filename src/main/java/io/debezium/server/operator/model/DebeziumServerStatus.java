package io.debezium.server.operator.model;

public class DebeziumServerStatus {

    private String deploymentConditionStatus;
    private boolean connectedToSource;
    private boolean connectedToSink;

    public String getDeploymentConditionStatus() {
        return deploymentConditionStatus;
    }

    public void setDeploymentConditionStatus(String deploymentConditionStatus) {
        this.deploymentConditionStatus = deploymentConditionStatus;
    }

    public boolean isConnectedToSource() {
        return connectedToSource;
    }

    public void setConnectedToSource(boolean connectedToSource) {
        this.connectedToSource = connectedToSource;
    }

    public boolean isConnectedToSink() {
        return connectedToSink;
    }

    public void setConnectedToSink(boolean connectedToSink) {
        this.connectedToSink = connectedToSink;
    }
}
