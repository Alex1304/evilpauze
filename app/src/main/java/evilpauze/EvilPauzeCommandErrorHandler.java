package evilpauze;

import botrino.interaction.InteractionErrorHandler;
import botrino.interaction.context.InteractionContext;
import botrino.interaction.privilege.PrivilegeException;
import org.reactivestreams.Publisher;

public class EvilPauzeCommandErrorHandler implements InteractionErrorHandler {

    @Override
    public Publisher<?> handlePrivilege(PrivilegeException e, InteractionContext ctx) {
        return ctx.event()
                .createFollowup("You don't have permission to use this command.")
                .withEphemeral(true);
    }
}
