package evilpauze;

import botrino.api.config.ConfigContainer;
import botrino.command.Command;
import botrino.command.CommandContext;
import botrino.command.Scope;
import botrino.command.annotation.Alias;
import botrino.command.annotation.TopLevelCommand;
import botrino.command.privilege.Privilege;
import com.github.alex1304.rdi.finder.annotation.RdiFactory;
import com.github.alex1304.rdi.finder.annotation.RdiService;
import discord4j.common.util.Snowflake;
import discord4j.core.object.PermissionOverwrite;
import discord4j.core.object.entity.channel.Channel;
import discord4j.core.object.entity.channel.GuildMessageChannel;
import discord4j.rest.util.Permission;
import discord4j.rest.util.PermissionSet;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

@Alias("lock")
@TopLevelCommand
@RdiService
public final class LockCommand implements Command {

    private final DatabaseService db;
    private final PrivilegeFactory privilegeFactory;
    private final Snowflake staffRoleId;

    @RdiFactory
    public LockCommand(DatabaseService db, PrivilegeFactory privilegeFactory, ConfigContainer configContainer) {
        this.db = db;
        this.privilegeFactory = privilegeFactory;
        this.staffRoleId = Snowflake.of(configContainer.get(EvilPauzeConfig.class).staffRoleId());
    }

    @Override
    public Mono<Void> run(CommandContext ctx) {
        final var guildId = ctx.event().getGuildId().orElseThrow();
        final var isGlobal = ctx.input().getFlag("g").isPresent();
        final var isUnlock = ctx.input().getFlag("u").isPresent();
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
                .flatMap(count -> ctx.channel()
                        .createMessage(String.format("Successfully %slocked %d channels",
                                isUnlock ? "un" : "", count)))
                .then();
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

    @Override
    public Scope scope() {
        return Scope.GUILD_ONLY;
    }
}
