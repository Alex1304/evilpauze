package evilpauze;

import botrino.api.config.ConfigContainer;
import botrino.interaction.InteractionFailedException;
import botrino.interaction.annotation.ChatInputCommand;
import botrino.interaction.context.ChatInputInteractionContext;
import botrino.interaction.grammar.ChatInputCommandGrammar;
import botrino.interaction.listener.ChatInputInteractionListener;
import botrino.interaction.privilege.Privilege;
import com.github.alex1304.rdi.finder.annotation.RdiFactory;
import com.github.alex1304.rdi.finder.annotation.RdiService;
import discord4j.common.util.Snowflake;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.Loggers;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

@RdiService
@ChatInputCommand(name = "lockdown", description = "Lock channels in this server.")
public final class LockdownCommand implements ChatInputInteractionListener {

    private final ChatInputCommandGrammar<Options> grammar = ChatInputCommandGrammar.of(Options.class);
    private final DatabaseService db;
    private final PrivilegeFactory privilegeFactory;
    private final Snowflake staffRoleId;

    @RdiFactory
    public LockdownCommand(DatabaseService db, PrivilegeFactory privilegeFactory, ConfigContainer configContainer) {
        this.db = db;
        this.privilegeFactory = privilegeFactory;
        this.staffRoleId = Snowflake.of(configContainer.get(EvilPauzeConfig.class).staffRoleId());
        Loggers.getLogger(LockdownCommand.class).debug(grammar.toOptions().toString());
    }

    @Override
    public Publisher<?> run(ChatInputInteractionContext ctx) {
        final var guildId = ctx.event().getInteraction().getGuildId().orElse(null);
        if (guildId == null) {
            return Mono.error(new InteractionFailedException("Cannot use outside of guild"));
        }
        return grammar.resolve(ctx.event()).flatMap(options -> {
            final var isGlobal = options.global != null && options.global;
            final var isUnlock = options.unlock != null && options.unlock;
            Mono<Integer> action;
            if (isUnlock) {
                action = db.lockedChannelDao().get(guildId.asLong())
                        .map(LockedChannel::channelIds)
                        .flatMap(ids -> Flux.fromIterable(ids)
                                .flatMap(id -> ctx.event().getClient().getChannelById(Snowflake.of(id))
                                        .ofType(GuildMessageChannel.class)
                                        .onErrorResume(e -> Mono.empty()))
                                .collectList()
                                .flatMap(channels -> unlockChannels(guildId.asLong(), channels,
                                        ids.stream()
                                                .filter(not(channels.stream()
                                                        .map(Channel::getId)
                                                        .map(Snowflake::asLong)
                                                        .collect(Collectors.toUnmodifiableSet())::contains))
                                                .collect(Collectors.toUnmodifiableList()))
                                        .thenReturn(channels.size())));
            } else {
                action = ctx.event().getClient().getGuildChannels(guildId)
                        .ofType(GuildMessageChannel.class)
                        .filter(channel -> (isGlobal || ctx.channel().equals(channel)) && isLockable(channel))
                        .collectList()
                        .flatMap(channels -> lockChannels(guildId.asLong(), channels).thenReturn(channels.size()));
            }
            return action
                    .flatMap(count -> ctx.event()
                            .createFollowup(String.format("Successfully %slocked %d channels",
                                    isUnlock ? "un" : "", count))
                            .withEphemeral(true))
                    .then();
        });

    }

    @Override
    public List<ApplicationCommandOptionData> options() {
        return grammar.toOptions();
    }

    private boolean isLockable(GuildMessageChannel channel) {
        final var everyonePerms = getPermissions(channel, channel.getGuildId());
        final var staffPerms = getPermissions(channel, staffRoleId);
        return !everyonePerms.getDenied().contains(Permission.VIEW_CHANNEL)
                && !everyonePerms.getDenied().contains(Permission.SEND_MESSAGES)
                && !everyonePerms.getAllowed().contains(Permission.SEND_MESSAGES)
                && !staffPerms.getAllowed().contains(Permission.SEND_MESSAGES);
    }

    private static PermissionOverwrite getPermissions(GuildMessageChannel channel, Snowflake id) {
        return channel.getOverwriteForRole(id)
                .map(PermissionOverwrite.class::cast)
                .orElse(PermissionOverwrite.forRole(id, PermissionSet.none(), PermissionSet.none()));
    }

    private Mono<Void> lockChannels(long guildId, List<GuildMessageChannel> channels) {
        return Flux.fromIterable(channels)
                .flatMap(channel -> editChannelPermissions(channel, false))
                .then(db.lockedChannelDao().update(guildId, set -> Stream.concat(set.stream(),
                        channels.stream()
                                .map(Channel::getId)
                                .map(Snowflake::asLong))
                        .collect(Collectors.toUnmodifiableSet())))
                .then();
    }

    private Mono<Void> unlockChannels(long guildId, List<GuildMessageChannel> channels, List<Long> deletedChannels) {
        return Flux.fromIterable(channels)
                .flatMap(channel -> editChannelPermissions(channel, true))
                .then(db.lockedChannelDao().update(guildId, set -> set.stream()
                        .filter(not(Stream.concat(deletedChannels.stream(), channels.stream()
                                .map(Channel::getId)
                                .map(Snowflake::asLong))
                                .collect(Collectors.toUnmodifiableSet())::contains))
                        .collect(Collectors.toUnmodifiableSet())))
                .then();
    }

    private Mono<Void> editChannelPermissions(GuildMessageChannel channel, boolean isUnlock) {
        final var everyonePerms = getPermissions(channel, channel.getGuildId());
        final var staffPerms = getPermissions(channel, staffRoleId);
        final var editEveryonePerms = channel.addRoleOverwrite(channel.getGuildId(),
                PermissionOverwrite.forRole(channel.getGuildId(), everyonePerms.getAllowed(), isUnlock
                        ? everyonePerms.getDenied().andNot(PermissionSet.of(Permission.SEND_MESSAGES))
                        : everyonePerms.getDenied().or(PermissionSet.of(Permission.SEND_MESSAGES))));
        final var editStaffPerms = channel.addRoleOverwrite(staffRoleId,
                PermissionOverwrite.forRole(staffRoleId, isUnlock
                                ? staffPerms.getAllowed().andNot(PermissionSet.of(Permission.SEND_MESSAGES))
                                : staffPerms.getAllowed().or(PermissionSet.of(Permission.SEND_MESSAGES)),
                        staffPerms.getDenied()));
        return Mono.when(editEveryonePerms, editStaffPerms);
    }

    @Override
    public Privilege privilege() {
        return privilegeFactory.headStaff();
    }

    private static final class Options {
        @ChatInputCommandGrammar.Option(
                type = ApplicationCommandOption.Type.BOOLEAN,
                name = "global",
                description = "Whether to lock all channels of the server or only this one."
        )
        Boolean global;

        @ChatInputCommandGrammar.Option(
                type = ApplicationCommandOption.Type.BOOLEAN,
                name = "unlock",
                description = "Whether to lock or unlock channels."
        )
        Boolean unlock;
    }
}
