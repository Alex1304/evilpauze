package evilpauze;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.criteria.Criteria;
import org.immutables.criteria.reactor.ReactorReadable;
import org.immutables.criteria.reactor.ReactorWritable;
import org.immutables.value.Value;

import java.util.Set;

@Value.Immutable
@Criteria
@Criteria.Repository(facets = { ReactorReadable.class, ReactorWritable.class })
@JsonSerialize(as = ImmutableLockedChannel.class)
@JsonDeserialize(as = ImmutableLockedChannel.class)
public interface LockedChannel {

    @Criteria.Id
    @JsonProperty("_id")
    long guildId();

    Set<Long> channelIds();
}
