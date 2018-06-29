/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventsourcing.eventstore.AbstractEventStorageEngine;
import org.axonframework.mongo.eventsourcing.eventstore.documentperevent.DocumentPerEventStorageStrategy;
import org.axonframework.mongo.utils.MongoLauncher;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.IOException;

import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.AGGREGATE;
import static org.axonframework.eventsourcing.eventstore.EventStoreTestUtils.createEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;


/**
 * @author Rene de Waele
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:META-INF/spring/mongo-context.xml"})
@DirtiesContext
public class MongoEventStorageEngineTest extends AbstractMongoEventStorageEngineTest {

    private static final Logger logger = LoggerFactory.getLogger(MongoEventStorageEngineTest.class);

    private static MongodExecutable mongoExe;
    private static MongodProcess mongod;

    private MongoEventStorageEngine testSubject;
    @Autowired
    private ApplicationContext context;
    private DefaultMongoTemplate mongoTemplate;

    @BeforeClass
    public static void start() throws IOException {
        mongoExe = MongoLauncher.prepareExecutable();
        mongod = mongoExe.start();
    }

    @AfterClass
    public static void shutdown() {
        if (mongod != null) {
            mongod.stop();
        }
        if (mongoExe != null) {
            mongoExe.stop();
        }
    }

    @Before
    public void setUp() {
        MongoClient mongoClient = null;
        try {
            mongoClient = context.getBean(MongoClient.class);
        } catch (Exception e) {
            logger.error("No Mongo instance found. Ignoring test.");
            Assume.assumeNoException(e);
        }
        mongoTemplate = new DefaultMongoTemplate(mongoClient);
        mongoTemplate.eventCollection().dropIndexes();
        mongoTemplate.snapshotCollection().dropIndexes();
        mongoTemplate.eventCollection().deleteMany(new BasicDBObject());
        mongoTemplate.snapshotCollection().deleteMany(new BasicDBObject());
        testSubject = context.getBean(MongoEventStorageEngine.class);
        setTestSubject(testSubject);

        testSubject.ensureIndexes();
    }

    @Test
    @Override
    public void testUniqueKeyConstraintOnEventIdentifier() {
        logger.info("Unique event identifier is not currently guaranteed in the Mongo Event Storage Engine");
    }

    @Test
    public void testOnlySingleSnapshotRemains() {
        testSubject.storeSnapshot(createEvent(0));
        testSubject.storeSnapshot(createEvent(1));
        testSubject.storeSnapshot(createEvent(2));

        assertEquals(1, mongoTemplate.snapshotCollection().count());
    }

    @Test
    public void testFetchHighestSequenceNumber() {
        testSubject.appendEvents(createEvent(0), createEvent(1));
        testSubject.appendEvents(createEvent(2));

        assertEquals(2, (long) testSubject.lastSequenceNumberFor(AGGREGATE).get());
        assertFalse(testSubject.lastSequenceNumberFor("notexist").isPresent());
    }

    @Override
    protected AbstractEventStorageEngine createEngine(EventUpcaster upcasterChain) {
        return new MongoEventStorageEngine(new XStreamSerializer(), upcasterChain, mongoTemplate,
                                           new DocumentPerEventStorageStrategy());
    }

    @Override
    protected AbstractEventStorageEngine createEngine(PersistenceExceptionResolver persistenceExceptionResolver) {
        XStreamSerializer serializer = new XStreamSerializer();
        return new MongoEventStorageEngine(serializer, NoOpEventUpcaster.INSTANCE,
                                           persistenceExceptionResolver, serializer, 100, mongoTemplate,
                                           new DocumentPerEventStorageStrategy());
    }
}
