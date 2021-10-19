package io.debezium.server.operator.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.server.operator.Constants;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

/**
 * A watcher for deployment(s) created by the operator.
 * <p>
 * At the moment, the operator creates a single deployment to run the Debezium Server.
 *
 */
public class DeploymentEventSource extends AbstractEventSource implements Watcher<Deployment> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeploymentEventSource.class);

    private KubernetesClient client;

    private DeploymentEventSource(KubernetesClient client) {
        this.client = client;
    }

    public static DeploymentEventSource createAndRegisterWatch(KubernetesClient client) {
        DeploymentEventSource eventSource = new DeploymentEventSource(client);
        eventSource.registerWatch();
        return eventSource;
    }

    @Override
    public void eventReceived(Action action, Deployment resource) {
        LOGGER.info(
                "Event received for action: {}, Service: {Name: {}, Namespace: {})",
                action.name(),
                resource.getMetadata().getName(),
                resource.getMetadata().getNamespace());
        if (action == Action.ERROR) {
            LOGGER.warn(
                    "SKipping {} event for custom resource [uid: {}, version: {}]",
                    action,
                    resource.getMetadata().getUid(),
                    resource.getMetadata().getResourceVersion());
            return;
        }
        eventHandler.handleEvent(new DeploymentEvent(action, resource, this));
    }

    @Override
    public void onClose(WatcherException cause) {
        if (cause == null) {
            return;
        }

        if (cause.isHttpGone()) {
            LOGGER.warn("Received error for watch, will try to reconnect.", cause);
            registerWatch();
        }
        else {
            // Note that this should not happen normally, since fabric8 client handles reconnect.
            // In case it tries to reconnect this method is not called.
            LOGGER.error("Unexpected error happened with watch. Will exit.", cause);
            System.exit(1);
        }
    }

    // -----------------------------< private methods >---

    public void registerWatch() {
        client
                .apps()
                .deployments()
                .inAnyNamespace()
                .withLabel(Constants.LABEL_MANAGED_BY, Constants.OPERATOR_IDENTIFIER)
                .watch(this);
    }
}
