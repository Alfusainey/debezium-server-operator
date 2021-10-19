package io.debezium.server.operator.event;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

public class DeploymentEvent extends DefaultEvent {

    private Action action;
    private Deployment deployment;

    public DeploymentEvent(Action action, Deployment deployment, DeploymentEventSource eventSource) {
        super(deployment.getMetadata().getOwnerReferences().get(0).getUid(), eventSource);
        this.action = action;
        this.deployment = deployment;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    public Action getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "DeploymentEvent{"
                + "action="
                + action
                + ", resource=[ name="
                + deployment.getMetadata().getName()
                + ", kind="
                + deployment.getKind()
                + ", apiVersion="
                + deployment.getApiVersion()
                + " ,resourceVersion="
                + deployment.getMetadata().getResourceVersion()
                + ", markedForDeletion: "
                + (deployment.getMetadata().getDeletionTimestamp() != null
                        && !deployment.getMetadata().getDeletionTimestamp().isEmpty())
                + " ]"
                + '}';
    }
}
