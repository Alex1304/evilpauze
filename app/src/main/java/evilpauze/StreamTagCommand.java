package evilpauze;

import botrino.api.config.ConfigContainer;
import botrino.command.Command;
import botrino.command.CommandContext;
import botrino.command.CommandFailedException;
import botrino.command.Scope;
import botrino.command.annotation.Alias;
import botrino.command.annotation.TopLevelCommand;
import botrino.command.privilege.Privilege;
import com.github.alex1304.rdi.finder.annotation.RdiFactory;
import com.github.alex1304.rdi.finder.annotation.RdiService;
import discord4j.common.util.Snowflake;
import reactor.core.publisher.Mono;

@Alias("streamtag")
@TopLevelCommand
@RdiService
public final class StreamTagCommand implements Command {

    private final EvilPauzeConfig config;
    private final PrivilegeFactory privilegeFactory;

    @RdiFactory
    public StreamTagCommand(ConfigContainer configContainer, PrivilegeFactory privilegeFactory) {
        this.config = configContainer.get(EvilPauzeConfig.class);
        this.privilegeFactory = privilegeFactory;
    }

    @Override
    public Mono<Void> run(CommandContext ctx) {
        return ctx.event().getClient()
                .getRoleById(ctx.event().getGuildId().orElseThrow(), Snowflake.of(config.streamNotificationRoleId()))
                .switchIfEmpty(Mono.error(new CommandFailedException("Cannot find stream notification role")))
                .flatMap(role -> role.edit().withMentionable(!role.isMentionable())
                        .thenReturn(String.format("Made %s role %smentionable", role.getName(),
                                role.isMentionable() ? "un" : "")))
                .flatMap(ctx.channel()::createMessage)
                .then();
    }

    @Override
    public Privilege privilege() {
        return privilegeFactory.streamer();
    }

    @Override
    public Scope scope() {
        return Scope.GUILD_ONLY;
    }
}
