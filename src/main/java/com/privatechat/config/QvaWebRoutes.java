package com.privatechat.config;

import java.util.Arrays;
import java.util.stream.Stream;

final class QvaWebRoutes {
    static final String INDEX = "/app/index.html";
    static final String[] SHELL_ROUTES = {
            "/app", "/app/", "/app/register", "/app/network", "/app/members",
            "/app/host", "/app/messages", "/app/vault"
    };
    static final String[] PUBLIC_RESOURCES = Stream.concat(
            Arrays.stream(SHELL_ROUTES), Stream.of(INDEX, "/app/assets/**")
    ).toArray(String[]::new);

    private QvaWebRoutes() {}
}
