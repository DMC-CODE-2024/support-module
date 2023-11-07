package com.gl.ceir.supportmodule.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

@Component
public class HeaderOperationCustomizer implements OperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Parameter clientTypeHeader = new Parameter()
                .in("header")
                .name("X-Client-Type")
                .description("Client Type Header")
                .required(true)
                .schema(new StringSchema())
                .example("REGISTERED,UNREGISTERED,SYSTEM");

        Parameter clientIdHeader = new Parameter()
                .in("header")
                .name("X-Client-Id")
                .description("Client ID Header")
                .required(true)
                .schema(new StringSchema())
                .example("AgentId or msisdn(for public interface)");

        operation.addParametersItem(clientTypeHeader);
        operation.addParametersItem(clientIdHeader);

        return operation;
    }
}
