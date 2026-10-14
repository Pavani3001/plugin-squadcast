package io.kestra.plugin.squadcast;

import java.net.URI;

import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Send Squadcast alert via webhook",
    description = "POSTs a JSON payload to the Squadcast Incoming Webhook URL you provisioned. Keep the URL secret; authentication is embedded in it. See the [Squadcast documentation](https://support.squadcast.com/docs/webhook) for payload details."
)
@Plugin(
    examples = {
        @Example(
            title = "Send a [Squadcast](https://www.squadcast.com/) alert via [incoming webhook](https://support.squadcast.com/integrations/incident-webhook-incident-webhook-api)",
            full = true,
            code = """
                id: squadcast_notification
                namespace: company.team

                tasks:
                  - id: send_squadcast_message
                    type: io.kestra.plugin.squadcast.SquadcastIncomingWebhook
                    url: "{{ secret('SQUADCAST_WEBHOOK') }}"
                    payload: |
                      {
                        "message": "Alert from Kestra flow {{ flow.id }}",
                        "description": "Error occurred in task {{ task.id }}",
                        "tags": {
                          "flow": "{{ flow.namespace }}.{{ flow.id }}",
                          "execution": "{{ execution.id }}",
                          "severity": "Critical"
                        },
                        "status": "trigger",
                        "event_id": "1"
                      }
                """
        ),
        @Example(
            title = "Resolve a Squadcast incident using event ID",
            full = true,
            code = """
                id: squadcast_notification
                namespace: company.team
                tasks:
                  - id: send_squadcast_message
                    type: io.kestra.plugin.squadcast.SquadcastIncomingWebhook
                    url: "{{ secret('SQUADCAST_WEBHOOK') }}"
                    payload: |
                      {
                        "status": "resolve",
                        "event_id": "1"
                      }
                """
        )
    },
    aliases = "io.kestra.plugin.notifications.squadcast.SquadcastIncomingWebhook"
)
public class SquadcastIncomingWebhook extends AbstractSquadcastConnection {
    @Schema(
        title = "Squadcast Incoming Webhook URL",
        description = "Full webhook endpoint from Squadcast (includes token); keep in a secret. See [Squadcast Webhook docs](https://support.squadcast.com/docs/webhook)."
    )
    @ToString.Exclude
    @PluginProperty(secret = true, dynamic = true, group = "main")
    @NotEmpty
    private String url;

    @Schema(
        title = "Raw JSON payload",
        description = "JSON body sent as-is after template rendering; must include Squadcast-required fields such as `status` and `event_id`"
    )
    @PluginProperty(group = "main")
    protected Property<String> payload;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String url = runContext.render(this.url);

        try (HttpClient client = new HttpClient(runContext, super.httpClientConfigurationWithOptions())) {
            String payload = runContext.render(this.payload).as(String.class).orElse(null);

            runContext.logger().debug("Send Squadcast webhook: {}", payload);
            HttpRequest request = HttpRequest.builder()
                .addHeader("Content-Type", "application/json")
                .uri(URI.create(url))
                .method("POST")
                .body(
                    HttpRequest.StringRequestBody.builder()
                        .content(payload)
                        .build()
                )
                .build();

            HttpResponse<String> response = client.request(request, String.class);

            runContext.logger().debug("Response: {}", response.getBody());

            if (response.getStatus().getCode() == 200) {
                runContext.logger().info("Request succeeded");
            }
        }

        return null;
    }
}
