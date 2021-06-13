package evilpauze;

import botrino.api.config.ConfigContainer;
import botrino.api.util.DurationUtils;
import botrino.api.util.Markdown;
import com.github.alex1304.rdi.finder.annotation.RdiFactory;
import com.github.alex1304.rdi.finder.annotation.RdiService;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.guild.MemberJoinEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.TextChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.rest.util.Permission;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;

import static reactor.function.TupleUtils.function;

@RdiService
public final class GatewayService {

    private static final Logger LOGGER = Loggers.getLogger(GatewayService.class);

    private final GatewayDiscordClient gateway;
    private final EvilPauzeConfig config;

    @RdiFactory
    public GatewayService(GatewayDiscordClient gateway, ConfigContainer configContainer) {
        this.gateway = gateway;
        this.config = configContainer.get(EvilPauzeConfig.class);
        start();
    }

    private static <X> Mono<X> deleteMessageIfInvalidVerify(Message message, EvilPauzeConfig config) {
        return Mono.just(message)
                .filter(msg -> msg.getChannelId().asLong() == config.verificationChannelId()
                        && msg.getType() == Message.Type.DEFAULT
                        && !msg.getAuthor().map(User::isBot).orElse(true))
                .filterWhen(msg -> msg.getChannel().ofType(TextChannel.class)
                        .flatMap(channel -> channel.getEffectivePermissions(msg.getAuthor()
                                .map(User::getId)
                                .orElseThrow()))
                        .map(perms -> !perms.contains(Permission.ADMINISTRATOR))
                        .onErrorReturn(true))
                .flatMap(Message::delete)
                .then(Mono.empty());
    }

    private static Mono<Void> checkJoinTime(Message msg, Member member, String cooldownMessage) {
        var joinedSince = Duration.between(member.getJoinTime().orElse(Instant.EPOCH), msg.getTimestamp()).withNanos(0);
        if (joinedSince.minus(Duration.ofMinutes(5)).isNegative()) {
            return Mono.error(new JustJoinedException(member.getMention() + ", it's been only " +
                    Markdown.bold(DurationUtils.format(joinedSince))
                    + " since you joined the server. " + cooldownMessage
                    + " You may retry in " + DurationUtils.format(Duration.ofMinutes(5).minus(joinedSince)) + "."));
        }
        return Mono.empty();
    }

    public void start() {
        final var messageCreate = gateway.on(MessageCreateEvent.class, msgEvent -> Mono.just(msgEvent)
                .filter(__ -> msgEvent.getMessage().getContent().equals(config.verificationMessage()))
                .flatMap(__ -> msgEvent.getMessage().getChannel()
                        .filter(channel -> channel.getId().asLong() == config.verificationChannelId())
                        .doOnNext(channel -> LOGGER.debug("Channel data: {}", channel))
                        .flatMap(channel -> msgEvent.getMessage().getAuthorAsMember()
                                .filter(member -> !member.getRoleIds().contains(Snowflake.of(config.memberRoleId())))
                                .map(member -> Tuples.of(channel, member))))
                .switchIfEmpty(deleteMessageIfInvalidVerify(msgEvent.getMessage(), config))
                .flatMap(function((channel, member) -> checkJoinTime(msgEvent.getMessage(), member,
                        config.cooldownMessage())
                        .then(channel.createMessage(member.getMention() + ", you are now entering the server. Enjoy " +
                                "your stay!"))
                        .then(Mono.delay(Duration.ofSeconds(5)))
                        .then(member.addRole(Snowflake.of(config.memberRoleId())))
                        .onErrorResume(JustJoinedException.class, e -> channel.createMessage(e.getMessage()).then()))));

        final var memberJoin = gateway.on(MemberJoinEvent.class, joinEvent -> Mono.just(joinEvent)
                .filter(event -> event.getGuildId().asLong() == config.gdServerId())
                .flatMap(event -> gateway
                .getChannelById(Snowflake.of(config.verificationChannelId()))
                .ofType(TextChannel.class)
                .delayElement(Duration.ofSeconds(1))
                .flatMap(channel -> channel
                        .createMessage(joinEvent.getMember().getMention() +" Welcome to the server!")
                        .withEmbed(EmbedCreateSpec.create()
                                .withTitle("Verify yourself")
                                .withDescription(config.welcomeMessage())))));

        Mono.when(messageCreate, memberJoin).subscribe(null,
                t -> LOGGER.error("Event listener error", t),
                () -> LOGGER.info("Event listener completed"));
    }

    private static class JustJoinedException extends RuntimeException {
        private JustJoinedException(String message) {
            super(message);
        }
    }
}
