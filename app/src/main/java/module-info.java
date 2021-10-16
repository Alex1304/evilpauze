import botrino.api.annotation.BotModule;

@BotModule
open module evilpauze {

    requires botrino.api;
    requires botrino.interaction;
    requires org.mongodb.driver.reactivestreams;
    requires org.immutables.criteria.common;
    requires org.immutables.criteria.mongo;
    requires org.immutables.criteria.reactor;
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;

    requires static org.immutables.value;
}