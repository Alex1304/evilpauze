package evilpauze;

import botrino.api.config.ConfigContainer;
import botrino.interaction.privilege.Privilege;
import botrino.interaction.privilege.Privileges;
import com.github.alex1304.rdi.finder.annotation.RdiFactory;
import com.github.alex1304.rdi.finder.annotation.RdiService;
import discord4j.common.util.Snowflake;
import discord4j.rest.util.Permission;

@RdiService
public final class PrivilegeFactory {

    private final EvilPauzeConfig config;

    @RdiFactory
    public PrivilegeFactory(ConfigContainer configContainer) {
        this.config = configContainer.get(EvilPauzeConfig.class);
    }

    public Privilege admin() {
        return Privileges.checkPermissions(perms -> perms.contains(Permission.ADMINISTRATOR));
    }

    public Privilege headStaff() {
        return admin().or(Privileges.checkRoles(roles -> roles.contains(Snowflake.of(config.headStaffRoleId()))));
    }

    public Privilege staff() {
        return headStaff().or(Privileges.checkRoles(roles -> roles.contains(Snowflake.of(config.staffRoleId()))));
    }
}
