package evilpauze;

import botrino.api.i18n.Translator;
import botrino.api.util.DurationUtils;
import botrino.interaction.annotation.Acknowledge;
import botrino.interaction.annotation.ChatInputCommand;
import botrino.interaction.context.ChatInputInteractionContext;
import botrino.interaction.listener.ChatInputInteractionListener;
import discord4j.core.event.domain.interaction.InteractionCreateEvent;
import discord4j.gateway.GatewayClient;
import org.reactivestreams.Publisher;

import java.time.Duration;

import static reactor.function.TupleUtils.function;

@Acknowledge(Acknowledge.Mode.NONE)
@ChatInputCommand(name = "ping", description = "Ping the bot to check if it is alive.")
public final class PingCommand implements ChatInputInteractionListener {

    private static String computeLatency(Translator tr, InteractionCreateEvent event, long apiLatency) {
        return tr.translate(Strings.APP, "pong") + '\n'
                + "API latency:" + DurationUtils.format(Duration.ofMillis(apiLatency)) + "\n"
                + "Discord Gateway latency: " + event.getClient()
                .getGatewayClient(event.getShardInfo().getIndex())
                .map(GatewayClient::getResponseTime)
                .map(DurationUtils::format)
                .orElse("unknown");
    }

    @Override
    public Publisher<?> run(ChatInputInteractionContext ctx) {
        return ctx.event().reply(ctx.translate(Strings.APP, "pong"))
                .thenReturn(0)
                .elapsed()
                .flatMap(function((apiLatency, __) -> ctx.event()
                        .editReply(computeLatency(ctx, ctx.event(), apiLatency))))
                .then();
    }
}
