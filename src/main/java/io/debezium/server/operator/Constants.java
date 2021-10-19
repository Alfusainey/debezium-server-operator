package io.debezium.server.operator;

import java.io.IOException;
import java.io.InputStream;

import io.fabric8.kubernetes.client.utils.Serialization;

public class Constants {

    /**
     * Label key use for watching/finding all resources managed by the debezium server operator.
     */
    public static final String LABEL_MANAGED_BY = "app.kubernetes.io/managed-by";
    public static final String OPERATOR_IDENTIFIER = "debezium-server-operator";

    /**
     * Creates a Resource instance from a given manifest definition file.
     * 
     * @param <R> The kind of resource to load.
     * @param clazz The class representing the kind of resource to be loaded.
     * @param yamlFileName  The name of file containing the manifest for the given kind.
     * 
     * @return  An instance of resource whose fields are filled with the contents of the manifest file.
     */
    public static <R> R loadYaml(Class<R> clazz, String yamlFileName) {
        try (InputStream is = Constants.class.getResourceAsStream(yamlFileName)) {
            return Serialization.unmarshal(is, clazz);
        }
        catch (IOException e) {
            throw new IllegalStateException("Cannot find manifest file on classpath: " + yamlFileName);
        }
    }

}
