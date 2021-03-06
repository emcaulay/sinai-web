
package edu.ucla.library.sinai.verticles;

import static edu.ucla.library.sinai.Constants.CONFIG_KEY;
import static edu.ucla.library.sinai.Constants.MESSAGES;
import static edu.ucla.library.sinai.Constants.SHARED_DATA_KEY;

import info.freelibrary.util.Logger;
import info.freelibrary.util.LoggerFactory;

import edu.ucla.library.sinai.Configuration;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public abstract class AbstractSinaiVerticle extends AbstractVerticle {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass().getName(), MESSAGES);

    @Override
    public void stop(final Future<Void> aFuture) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} deployed: {}", getClass().getName(), deploymentID());
        }

        aFuture.complete();
    }

    protected Configuration getConfiguration() {
        return (Configuration) vertx.sharedData().getLocalMap(SHARED_DATA_KEY).get(CONFIG_KEY);
    }

    protected MessageConsumer<JsonObject> getJsonConsumer() {
        return vertx.eventBus().consumer(getClass().getName());
    }

    protected MessageConsumer<String> getStringConsumer() {
        return vertx.eventBus().consumer(getClass().getName());
    }

    /**
     * A naive approach to retrying the sending of messages... to be improved.
     *
     * @param aJsonObject
     * @param aVerticleName
     * @param aCount
     */
    protected void sendMessage(final JsonObject aJsonObject, final String aVerticleName, final int aCount) {
        final long sendTimeout = DeliveryOptions.DEFAULT_TIMEOUT * aCount;
        final int retryCount = getConfiguration().getRetryCount();
        final DeliveryOptions options = new DeliveryOptions();
        final EventBus eventBus = vertx.eventBus();

        // Slow down timeout expectations if we're taking a long time processing images
        if (aCount > 0) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Slowing down the {}'s timeout to: {}", getClass().getSimpleName(), sendTimeout);
            }

            options.setSendTimeout(sendTimeout);
        }

        eventBus.send(aVerticleName, aJsonObject, options, response -> {
            if (response.failed()) {
                if (aCount < retryCount) {
                    LOGGER.warn("Unable to send message to {}; retrying: {}", aVerticleName, aJsonObject);
                    sendMessage(aJsonObject, aVerticleName, aCount + 1);
                } else {
                    if (response.cause() != null) {
                        LOGGER.error(response.cause(), "Unable to send message to {}: {}", aVerticleName,
                                aJsonObject);
                    } else {
                        LOGGER.error("Unable to send message to {}: {}", aVerticleName, aJsonObject);
                    }
                }
            }
        });
    }
}
