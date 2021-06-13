package evilpauze;

import botrino.command.CommandContext;
import botrino.command.CommandErrorHandler;
import botrino.command.privilege.PrivilegeException;
import reactor.core.publisher.Mono;

public class EvilPauzeCommandErrorHandler implements CommandErrorHandler {

    @Override
    public Mono<Void> handlePrivilege(PrivilegeException e, CommandContext ctx) {
        return ctx.channel().createMessage("You don't have permission to use this command.").then();
    }
}
