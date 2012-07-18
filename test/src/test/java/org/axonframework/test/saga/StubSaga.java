/*
 * Copyright (c) 2010-2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.test.saga;

import org.axonframework.commandhandling.CommandBus;
import org.axonframework.domain.Event;
import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.scheduling.EventScheduler;
import org.axonframework.saga.annotation.AbstractAnnotatedSaga;
import org.axonframework.saga.annotation.EndSaga;
import org.axonframework.saga.annotation.SagaEventHandler;
import org.axonframework.saga.annotation.StartSaga;
import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * @author Allard Buijze
 */
public class StubSaga extends AbstractAnnotatedSaga {
    private transient CommandBus commandBus;
    private transient EventBus eventBus;
    private transient EventScheduler scheduler;
    private List<Event> handledEvents = new ArrayList<Event>();

    @StartSaga
    @SagaEventHandler(associationProperty = "aggregateIdentifier")
    public void handleSagaStart(TriggerSagaStartEvent event) {
        handledEvents.add(event);
        scheduler.schedule(Duration.standardMinutes(10), new TimerTriggeredEvent(this, event.getAggregateIdentifier()));
    }

    @StartSaga(forceNew = true)
    @SagaEventHandler(associationProperty = "aggregateIdentifier")
    public void handleForcedSagaStart(ForceTriggerSagaStartEvent event) {
        handledEvents.add(event);
        scheduler.schedule(Duration.standardMinutes(10), new TimerTriggeredEvent(this, event.getAggregateIdentifier()));
    }

    @SagaEventHandler(associationProperty = "aggregateIdentifier")
    public void handleEvent(TriggerExistingSagaEvent event) {
        handledEvents.add(event);
        eventBus.publish(new SagaWasTriggeredEvent(this));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "aggregateIdentifier")
    public void handleEndEvent(TriggerSagaEndEvent event) {
        handledEvents.add(event);
    }

    @SagaEventHandler(associationProperty = "aggregateIdentifier")
    public void handleFalseEvent(TriggerExceptionWhileHandlingEvent event) {
        handledEvents.add(event);
        throw new RuntimeException("This is a mock exception");
    }

    @SagaEventHandler(associationProperty = "aggregateIdentifier")
    public void handleTriggerEvent(TimerTriggeredEvent event) {
        handledEvents.add(event);
        commandBus.dispatch("Say hi!", new CommandCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
                if (result != null) {
                    commandBus.dispatch(result);
                }
            }

            @Override
            public void onFailure(Throwable cause) {
                fail("Didn't expect exception");
            }
        });
    }


    public EventBus getEventBus() {
        return eventBus;
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public EventScheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(EventScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public CommandBus getCommandBus() {
        return commandBus;
    }

    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @Override
    public void associateWith(String key, Object value) {
        super.associateWith(key, value);
    }

    @Override
    public void removeAssociationWith(String key, Object value) {
        super.removeAssociationWith(key, value);
    }

    @Override
    public void end() {
        super.end();
    }
}
