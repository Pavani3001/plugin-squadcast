package io.kestra.plugin.squadcast;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.google.common.io.Files;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@KestraTest
class SquadcastIncomingWebhookTest {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        RunContext runContext = runContextFactory.of(
            Map.of(
                "message", "A message *with some bold text* and _some italicized text_.",
                "priority", "P1",
                "description", "Squadcast test alert notification"
            )
        );

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SquadcastIncomingWebhook task = SquadcastIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(
                Property.ofExpression(
                    Files.asCharSource(
                        new File(
                            Objects.requireNonNull(
                                SquadcastIncomingWebhookTest.class.getClassLoader()
                                    .getResource("squadcast.peb")
                            )
                                .toURI()
                        ),
                        StandardCharsets.UTF_8
                    ).read()
                )
            )
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("ge *with some bold text* an"));
    }

    @Test
    void shouldNotRenderPayloadTwice() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of());

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SquadcastIncomingWebhook task = SquadcastIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(Property.ofExpression("{\"message\": \"{% raw %}{{ notAVariable }}{% endraw %}\"}"))
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("{{ notAVariable }}"));
    }

    @Test
    void shouldSendVariableValueContainingExpressionAsText() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("user_input", "{{ secret('API_KEY') }}"));

        EmbeddedServer embeddedServer = applicationContext.getBean(EmbeddedServer.class);
        embeddedServer.start();

        SquadcastIncomingWebhook task = SquadcastIncomingWebhook.builder()
            .url(embeddedServer.getURI() + "/webhook-unit-test")
            .payload(Property.ofExpression("{\"message\": \"{{ user_input }}\"}"))
            .build();

        task.run(runContext);

        assertThat(FakeWebhookController.data, containsString("{{ secret('API_KEY') }}"));
    }

}
