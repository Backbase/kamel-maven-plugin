package com.backbase.oss.integration;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
// camel-k: language=java
// camel-k: name=example-integration
@SuppressWarnings("unused")
public class ExampleApiIntegration extends RouteBuilder {
    @Override
    public void configure() {

        from("direct://getRoot")
                .log(LoggingLevel.INFO, "Example root endpoint has been called")
                .end();

        from("direct://getVersion")
                .log(LoggingLevel.INFO, "Return integration version")
                .setBody(exchange -> "{\n  \"version\": \"1.1.2\"\n}\n");
    }
}
