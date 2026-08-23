package com.example.backend.agent.runtime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ops.agent")
public class OpsAgentProperties {

    /** Defaults to the existing implementation so rollout is opt-in. */
    private OpsRuntimeMode runtime = OpsRuntimeMode.LEGACY;

    private OpenCode openCode = new OpenCode();

    @Getter
    @Setter
    public static class OpenCode {

        /** Explicit opt-in guard for the OpenCode runtime. */
        private boolean enabled = false;

        private String baseUrl = "http://127.0.0.1:4096";

        /** The workspace directory passed to the OpenCode server, if any. */
        private String workspace = "";

        private String agent = "linshu-ops";

        private int connectTimeoutMillis = 5_000;

        private int requestTimeoutMillis = 120_000;

        /** Optional Basic Auth credentials for opencode serve. */
        private String username = "";

        private String password = "";

        /** Shared only with the local OpenCode tool process, never with the model. */
        private String toolGatewayToken = "";

        private List<String> allowedLogPathPrefixes = new ArrayList<>(List.of("/var/log/"));

        public boolean hasServerCredentials() {
            return StringUtils.hasText(username) && StringUtils.hasText(password);
        }

        public boolean isToolGatewayEnabled() {
            return StringUtils.hasText(toolGatewayToken);
        }
    }
}
