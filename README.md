# th2 integration test extensions for JUnit 5

The library contains extensions for JUnit 5 to simplify the components integration testing.
It provides you with:
+ MQ (RabbitMQ) integration
+ Cradle (Cassandra) integration
+ Automated resources clean up after test

For integration with MQ and Cradle the library is using [Testcontainers](https://testcontainers.com/).
It means the [**docker**](https://www.docker.com/) is required for using those extensions.

For MQ it uses [Rabbit MQ module](https://java.testcontainers.org/modules/rabbitmq/).
For the Cradle it uses [Cassandra module](https://java.testcontainers.org/modules/databases/cassandra/).

## Usage

Add dependency to `com.exactpro.th2:junit-jupiter-integration` into your test classpath.
_NOTE: dependency to JUnit is required_

In order to enable integrations you must annotate your test class with
`com.exactpro.th2.test.annotations.Th2IntegrationTest` annotations.

_NOTE: this annotation also annotates your class with **@TestInstance(TestInstance.Lifecycle.PER_CLASS)**._
_It means only one instance will be created to run all tests_

After that you can inject `CommonFactory` into your tests.
The parameter that will be used as a factory for actual application should be annotated with
`com.exactpro.th2.test.annotations.Th2AppFactory` annotation.
The parameter that will be used to verify the application under the test should be annotated with
`com.exactpro.th2.test.annotations.Th2TetsFactory` annotation.

```kotlin
import com.exactpro.th2.test.annotations.Th2AppFactory
import com.exactpro.th2.test.annotations.Th2TestFactory
import com.exactpro.th2.test.annotations.Th2IntegrationTest
import com.exactpro.th2.common.schema.factory.CommonFactory
import org.junit.jupiter.api.Test

@Th2IntegrationTest
class YourTest {
    @Test
    fun test(
        @Th2AppFactory appFactory: CommonFactory,
        @Th2TestFactory testFactory: CommonFactory,
    ) {
        // your test
    }
}
```

The `CommonFactory` is created right before test and is closed right after the test is completed.

### Pins configuration

By default, only the **events** pin is added to the pins' configuration.
If you need to add additional pins you can declare `RabbitMqSpec` field in the test class.
It will allow you to define pins with attributes and filters.

```kotlin
@Th2IntegrationTest
class YourTest {
    @JvmField
    internal val mq = RabbitMqSpec.create()
        .pins {
            subscribers {
                pin("sub") {
                    attributes("transport-group")
                    filter {
                        message {
                            field("test") shouldBeEqualTo "a"
                        }
                    }
                }
            }

            publishers {
                pin("pub") {
                    attributes("transport-group")
                }
            }
        }
}
```
_NOTE: the annotation @JvmField is required for Kotlin to make it work._

It will create corresponding queue in Rabbit MQ.
It will also create connection between **app factory** and **test factory**
Each publish pin in **app factory** will send a message to a queue that can be accessed throw **test factory**.
And each subscribe pin in **app factory** is bound with one of the routing keys in **test factory**.

In order to publish/subscribe to a specific pin in **app factory** you need to call a corresponding method in **test factory**
with an attribute that is equal to pin name in the defined MQ spec.
For example, if we want to send a transport message to _sub_ pin we should to the following:

```kotlin
testFactory.transportGroupBatchRouter.send(groupBatch, "sub")
```

And if we want to subscribe for messages that are sent from pin _pub_ we should do this:

```kotlin
import com.exactpro.th2.test.queue.CollectorMessageListener

val listener = CollectorMessageListener.createUnbound<GroupBatch>()
testFactory.transportGroupBatchRouter.subscribe(listener, "pub")
```

`CollectorMessageListener` is a helper class that allows you to collect messages from the message queue and do assertions on them.

### Cradle configuration

By default, the Cradle is not configured and a Cassandra container won't be started.
In order to add Cradle you must declare `CradleSpec` field in the test class.

```kotlin
@Th2IntegrationTest
class YourTest {
    @JvmField
    internal val cradle = CradleSpec.create()
}
```

`CradleSpec` allows you to configure:
+ keyspace name
+ storage settings (like _resultPageSize_, _bookRefreshIntervalMillis_, _query timeout_)
+ auto-page generation for default book

By default, the keyspace is created and initialized before each test.
The auto-pages functionality is enabled and will generate pages for default book (from BoxConfiguration class).
The **app factory** will have cradle configuration with enabling schema initialization.
The **test factory** will not have such functionality enabled
(it means you cannot use cradle from **test factory** for to interact with cradle before the schema is initialized)
But you can reuse the keyspace between all tests.
Use `reuseKeyspace()` to enable that.

_NOTE: when you are reusing keyspace it is probably better to disable auto-page generation and set up the keyspace manually_.

In order to do that you can inject `CradleManager` into a test method or beforeAll/beforeEach methods.

```kotlin
@Th2IntegrationTest
class YourTest {
    @JvmField
    internal val cradle = CradleSpec.create()
        .disableAutoPages()
        .reuseKeyspace()
    
    @BeforeAll
    fun setup(manager: CradleManager) {
        // set up the keyspace
    }
}
```

### Custom configuration

The component under the test might require to provide a custom configuration.
In order to do that you should declare `CustomConfigSpec` field in the test class.
The custom configuration can be created from a string source or from an object (it will be serialized as JSON into custom configuration file).

From a string source:

```kotlin
@Th2IntegrationTest
class YourTest {
    @JvmField
    internal val customConfig = CustomConfigSpec.fromString(
        """
        {
            "test": 42
        }
        """.trimIndent()
    )
}
```

From an object:

```kotlin
private class Config(val test: Int)

@Th2IntegrationTest
class YourTest {
    @JvmField
    internal val customConfig = CustomConfigSpec.fromObject(
        Config(
            test = 42,
        )
    )
}
```

You can also provide a custom configuration for a specific test method.
In order to do that you can use `com.exactpro.th2.test.annotations.CustomConfigProvider`
annotation with method name that produces an instance of `CustomConfigSpec` class.

_NOTE: the method that provides custom config spec must be public_

```kotlin
private class Config(val test: Int)

@Th2IntegrationTest
class YourTest {
    @JvmField
    internal val customConfig = CustomConfigSpec.fromObject(
        Config(
            test = 42,
        )
    )
    
    @Test
    fun withCommonCustomConfig(
        @Th2AppFactory factory: CommonFactory,
    ) {
        val customConfig = factory.getCustomConfiguration<Config>()
        Assertions.assertEquals(42, customConfig.test)
    }

    @Test
    @CustomConfigProvider("overriddenConfig")
    fun withOverriddenCustomConfig(
        @Th2AppFactory factory: CommonFactory,
    ) {
        val customConfig = factory.getCustomConfiguration<Config>()
        Assertions.assertEquals(43, customConfig.test)
    }
    
    fun overriddenConfig() = CustomConfigSpec.fromObject(
        Config(
            test = 43,
        )
    )
}
```

### Cleanup functionality

It is a very common case when some application resource must be closed right after the test.
For that purpose the library provides user with `CleanupExtension.Registry` class that allows to store `AutoClosable` objects.
They will be invoked in the descend order (LIFO). Very similar to Golang `defer` functionality.
If `CleanupExtension.Registry` is injected into test method the registered resources will be closed before the `CommonFactory` (app and test).

Here is an order of execution:
1. Create app and test common factories
2. Inject factories and `CleanupExtension.Registry` into a test method
3. Register resource with registry
4. Finish test method execution
5. Close resources registered with registry (LIFO order)
6. Close app and test common factories

This allows the application to close its resources before the `CommonFactory` is closed.

Here is an example of how you can use the registry:

```kotlin
@Th2IntegrationTest
class YourTest {
    @Test
    fun test(
        @Th2AppFactory appFactory: CommonFactory,
        @Th2TestFactory testFactory: CommonFactory,
        resources: CleanupExtension.Registry,
    ) {
        resources.add("app resource") {
            // will be closed second
        }
        resources.add("another resource") {
            // will be closed first
        }
    }
}
```

### Advanced test configuration

Sometimes you might want to configure the Rabbit MQ and Cassandra containers to reproduce a specific case.
For that purpose you can provide the integration implementation in your test class that will be taken instead of the default one.

For Rabbit MQ you need to provide an instance of `com.exactpro.th2.test.integration.RabbitMqIntegration`.
For the Cassandra you need to provide an instance of `com.exactpro.th2.test.integration.CradleIntegration`.

Here is an example of providing custom images for those integrations:

```kotlin
@Th2IntegrationTest
class YourTest {
    @JvmField
    val rabbit = RabbitMqIntegration
        .fromImage(DockerImageName.parse("rabbitmq:3.12.6-management-alpine"))
    
    @JvmField
    val cradle = CradleIntegration
        .fromImage(DockerImageName.parse("cassandra:4.1.3"))
}
```