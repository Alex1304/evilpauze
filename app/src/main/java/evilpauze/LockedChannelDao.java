package evilpauze;

import org.immutables.criteria.backend.Backend;
import org.immutables.criteria.backend.WriteResult;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.function.UnaryOperator;

import static evilpauze.LockedChannelCriteria.lockedChannel;

public final class LockedChannelDao {

    private final LockedChannelRepository repository;

    public LockedChannelDao(Backend backend) {
        this.repository = new LockedChannelRepository(backend);
    }

    public Mono<LockedChannel> get(long guildId) {
        return repository.find(lockedChannel.guildId.is(guildId)).oneOrNone();
    }

    public Mono<WriteResult> update(long guildId, UnaryOperator<Set<Long>> channelIds) {
        return repository.find(lockedChannel.guildId.is(guildId))
                .oneOrNone()
                .defaultIfEmpty(ImmutableLockedChannel.builder().guildId(guildId).channelIds(Set.of()).build())
                .map(lockedChannel -> ImmutableLockedChannel.copyOf(lockedChannel)
                        .withChannelIds(channelIds.apply(lockedChannel.channelIds())))
                .flatMap(repository::upsert);
    }
}
