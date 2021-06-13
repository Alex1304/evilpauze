package evilpauze;

import botrino.api.annotation.ConfigEntry;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@ConfigEntry("mongodb")
@Value.Immutable
@JsonDeserialize(as = ImmutableMongoDBConfig.class)
public interface MongoDBConfig {

    @JsonProperty("database_name")
    String databaseName();

    @JsonProperty("connection_string")
    String connectionString();
}
