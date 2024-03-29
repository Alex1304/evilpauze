package evilpauze;

import botrino.api.annotation.ConfigEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@ConfigEntry("evilpauze")
@Value.Immutable
@JsonDeserialize(as = ImmutableEvilPauzeConfig.class)
public interface EvilPauzeConfig {

    @JsonProperty("gd_server_id")
    long gdServerId();

    @JsonProperty("verification_channel_id")
    long verificationChannelId();

    @JsonProperty("member_role_id")
    long memberRoleId();

    @JsonProperty("verification_message")
    String verificationMessage();

    @JsonProperty("welcome_message")
    String welcomeMessage();

    @JsonProperty("cooldown_message")
    String cooldownMessage();

    @JsonProperty("staff_role_id")
    long staffRoleId();

    @JsonProperty("head_staff_role_id")
    long headStaffRoleId();
}
