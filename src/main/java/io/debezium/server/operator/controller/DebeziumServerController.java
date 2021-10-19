package io.debezium.server.operator.controller;

import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.debezium.server.operator.Constants;
import io.debezium.server.operator.event.DeploymentEvent;
import io.debezium.server.operator.event.DeploymentEventSource;
import io.debezium.server.operator.model.DebeziumServer;
import io.debezium.server.operator.model.DebeziumServerStatus;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapEnvSource;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretEnvSource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;

public class DebeziumServerController implements ResourceController<DebeziumServer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebeziumServerController.class);

    private static final String DATA_VOLUME_NAME = "data-volume";
    private static final String DATA_VOLUME_MOUNTPATH = "/debezium/data";

    private static final String SERVER_IMAGE = "debezium/server:";

    @Inject
    KubernetesClient kubernetesClient;

    @Override
    public void init(EventSourceManager eventSourcemanager) {
        DeploymentEventSource eventSource = DeploymentEventSource.createAndRegisterWatch(kubernetesClient);
        eventSourcemanager.registerEventSource("deployment-event-source", eventSource);
    }

    @Override
    public UpdateControl<DebeziumServer> createOrUpdateResource(DebeziumServer server, Context<DebeziumServer> context) {
        final String name = server.getMetadata().getName();
        final String namespace = server.getMetadata().getNamespace();

        Optional<CustomResourceEvent> latestCREvent = context.getEvents().getLatestOfType(CustomResourceEvent.class);
        if (latestCREvent.isPresent()) {
            LOGGER.info("Latest CR event action {}", latestCREvent.get().getAction());
            Deployment existingDeployment = kubernetesClient
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();

            if (existingDeployment == null) {
                // get source-service
                String sourceServiceName = server.getSpec().getSourceSystem().getService().getName();
                Service sourceService = kubernetesClient
                        .services()
                        .inNamespace(namespace)
                        .withName(sourceServiceName)
                        .get();

                if (sourceService == null) {
                    throw new IllegalStateException("Cannot find source service " + server.getSpec().getSourceSystem().getService().getName()
                            + "in namespace " + server.getMetadata().getNamespace());
                }
                // get sink service
                Service sinkService = kubernetesClient
                        .services()
                        .inNamespace(namespace)
                        .withName(server.getSpec().getTargetSystem().getService().getName())
                        .get();
                if (sinkService == null) {
                    throw new IllegalStateException("Cannot find sink service " + server.getSpec().getSourceSystem().getService().getName()
                            + "in namespace " + server.getMetadata().getNamespace());
                }

                Deployment deployment = getServerDeployment(server);
                // Our custom resource owns the deployment and by doing so, we achieve two things:
                // (1) We get notified when the deployment is ready (i.e when k8s modifies its .status property).
                // we react to such modifications by changing the .status field of our custom resource
                // (2) The deployment will be orphaned once the custom resource is deleted, making it possible for k8s to GC the deployment object.

                OwnerReference ownerReference = new OwnerReference();
                ownerReference.setApiVersion(server.getApiVersion());
                ownerReference.setKind(server.getKind());
                ownerReference.setName(server.getMetadata().getName());
                ownerReference.setUid(server.getMetadata().getUid());
                deployment.getMetadata().getOwnerReferences().add(ownerReference);

                try {
                    kubernetesClient.apps().deployments().inNamespace(namespace).create(deployment);
                    LOGGER.info("Created server Deployment {} in {} ", deployment.getMetadata().getName(), deployment.getMetadata().getNamespace());
                }
                catch (Exception e) {
                    LOGGER.error(e.getMessage());
                }
            } else {
                // If the operator can monitor changes to:
                // (1) the server configuration and
                // (2) the source and sink services
                // then the only thing that will change is the server version.
                // so updating requires changing the server image version
                existingDeployment
                        .getSpec()
                        .getTemplate()
                        .getSpec()
                        .getContainers()
                        .get(0)
                        .setImage(SERVER_IMAGE + server.getSpec().getVersion());
                kubernetesClient.apps().deployments().inNamespace(namespace).createOrReplace(existingDeployment);
            }
        }

        Optional<DeploymentEvent> latestDeploymentEvent = context.getEvents().getLatestOfType(DeploymentEvent.class);
        if (latestDeploymentEvent.isPresent()) {
            LOGGER.info("Updating Custom resource status");
            Deployment deployment = latestDeploymentEvent.get().getDeployment();

            DeploymentStatus depStatus = deployment.getStatus() != null ? deployment.getStatus() : new DeploymentStatus();
            DebeziumServerStatus serverStatus = new DebeziumServerStatus();
            serverStatus.setDeploymentConditionStatus(depStatus.getConditions().get(0).getStatus());
            serverStatus.setConnectedToSource(depStatus.getAvailableReplicas() == 1);
            serverStatus.setConnectedToSink(depStatus.getAvailableReplicas() == 1);
            server.setStatus(serverStatus);

            return UpdateControl.updateStatusSubResource(server);
        }

        List<Event> events = context.getEvents().getList();
        for (Event event : events) {
            if (event instanceof DeploymentEvent) {
                DeploymentEvent depEvent = (DeploymentEvent) event;
                switch (depEvent.getAction()) {
                    case DELETED:
                        LOGGER.info("deployment created by our custom resource is deleted");
                        break;
                    case MODIFIED:
                        // TODO: we should disallow this case.
                        LOGGER.info("existing deployment is modified");
                        break;
                    default:
                        LOGGER.info("default case");
                        break;
                }
            }
        }
        return UpdateControl.noUpdate();
        
    }

    @Override
    public DeleteControl deleteResource(DebeziumServer server, Context<DebeziumServer> context) {
        LOGGER.info("Deleting DebeziumServer object {}", server.getMetadata().getName());
        // Nothing to do here since deleting the custom resource will orphan the deployment object
        // created by the custom resource. Thanks to owner references, k8s will delete the deployment.
        return DeleteControl.DEFAULT_DELETE;
    }

    private Deployment getServerDeployment(DebeziumServer server) {
        Deployment deployment = Constants.loadYaml(Deployment.class, "server-deployment.yaml");
        deployment.getMetadata().setName(server.getMetadata().getName());
        deployment.getMetadata().setNamespace(server.getMetadata().getNamespace());

        PodSpec podSpec = new PodSpec();
        deployment.getSpec().getTemplate().setSpec(podSpec);
        final io.fabric8.kubernetes.api.model.Container k8sContainer = new io.fabric8.kubernetes.api.model.Container();
        k8sContainer.setName("server");
        k8sContainer.setImage(SERVER_IMAGE + server.getSpec().getVersion());

        ConfigMap configMap = kubernetesClient
                .configMaps()
                .inNamespace(server.getMetadata().getNamespace())
                .withName(server.getSpec().getConfig().getConfigMapName())
                .get();

        if (configMap == null) {
            throw new IllegalStateException("Cannot find server configuration [ConfigMap {name: " + server.getSpec().getConfig().getConfigMapName() + " }]");
        }

        Secret secret = kubernetesClient.secrets()
                .inNamespace(server.getMetadata().getNamespace())
                .withName(server.getSpec().getConfig().getSecretName())
                .get();

        if (secret == null) {
            throw new IllegalStateException("Cannot find server secret configuration [Secret {name: " + server.getSpec().getConfig().getSecretName() + " }");
        }

        ConfigMapEnvSource configMapRef = new ConfigMapEnvSource();
        configMapRef.setName(configMap.getMetadata().getName());

        EnvFromSource configMapSource = new EnvFromSource();
        configMapSource.setConfigMapRef(configMapRef);
        k8sContainer.getEnvFrom().add(configMapSource);

        SecretEnvSource secretRef = new SecretEnvSource();
        secretRef.setName(secret.getMetadata().getName());

        EnvFromSource secretSource = new EnvFromSource();
        secretSource.setSecretRef(secretRef);
        k8sContainer.getEnvFrom().add(secretSource);

        Volume volume = new Volume();
        volume.setName(DATA_VOLUME_NAME);
        volume.setEmptyDir(new EmptyDirVolumeSource());
        podSpec.getVolumes().add(volume);

        VolumeMount vm = new VolumeMount();
        vm.setName(volume.getName());
        vm.setMountPath(DATA_VOLUME_MOUNTPATH);
        k8sContainer.getVolumeMounts().add(vm);

        podSpec.getContainers().add(k8sContainer);

        return deployment;
    }
}
