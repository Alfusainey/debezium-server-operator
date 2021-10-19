package io.debezium.server.operator;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.Operator;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

@QuarkusMain
public class DebeziumServerOperator implements QuarkusApplication {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebeziumServerOperator.class);

    @Inject
    Operator operator;

    public static void main(String args[]) {
        Quarkus.run(DebeziumServerOperator.class, args);
    }

    @Override
    public int run(String... args) throws Exception {
        LOGGER.info("Staring Debezium Server Operator");
        operator.start();
        Quarkus.waitForExit();
        return 0;
    }
}
