package io.debezium.server.operator.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("debezium.io")
@Version("v1")
public class DebeziumServer extends CustomResource<DebeziumServerSpec, DebeziumServerStatus> implements Namespaced {

}
