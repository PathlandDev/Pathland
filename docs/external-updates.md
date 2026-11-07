# External UI updates with topic subscriptions

Pathland signals are single-threaded by contract: reads, writes, and effects run
on the session's actor. Background work (Kafka/SmallRye `@Incoming` consumers,
scheduled jobs, database notifications, other transports) runs on its own
threads, so it must not set a view's signal directly.

Topic subscriptions are the supported bridge. A view subscribes to a
**strongly typed, always-scoped** topic and gets a normal `Signal<T>`; external
code publishes a value for the same `(topic, subject)` pair from any thread, and
only the sessions subscribed to that pair update. The runtime applies the value
on each session's actor, so the existing effect/delta path is unchanged.

## 1. Declare the topic once

A `Topic<T, S>` carries the payload type `T` and the subject type `S`. Topic
identity is its name, so the view and the publisher share the same constant.

```kotlin
object CustomerTopics {
    val NAME: Topic<String, Long> = Topic.of("customer.name")
    val ORDERS: Topic<List<Order>, Long> = Topic.of("customer.orders")
}
```

```java
public final class CustomerTopics {
    public static final Topic<String, Long> NAME = Topic.of("customer.name");
    public static final Topic<List<Order>, Long> ORDERS = Topic.of("customer.orders");

    private CustomerTopics() {}
}
```

## 2. Subscribe in a view

```kotlin
class CustomerOrdersView(
    private val customerId: Long,
    private val initialName: String,
    private val initialOrders: List<Order>,
) : Component() {

    private val name = topic(CustomerTopics.NAME, customerId, initialName)
    private val orders = topic(CustomerTopics.ORDERS, customerId, initialOrders)

    override fun body(): View = VStack(spacing = 8) {
        Text(name)                          // Signal<String>
        ForEach(orders) { OrderCard(it) }   // Signal<List<Order>>
    }
}
```

`topic(...)` returns a regular `Signal<T>`, so `Text(signal)`,
`ForEach(signal)`, `signal.map { }`, and `computed { }` all work. Subscribing is
idempotent per `(topic, subject)` within a session; repeated calls return the
same local signal.

Passing the wrong subject or payload type is a compile error: `NAME` requires a
`Long` subject and a `String` value.

## 3. Publish from any thread

The starter produces a `PathlandSignalHub` bean for the root mount (use
`PathlandHost.signalHub(mount)` in a multi-app host). Publishing is
non-blocking.

```java
@ApplicationScoped
public class CustomerConsumers {

    @Inject PathlandSignalHub hub;

    @Incoming("customer-name-changed")
    void onNameChanged(CustomerNameChanged event) {
        hub.publish(CustomerTopics.NAME, event.customerId(), event.name());
    }
}
```

Only sessions that subscribed to `(customer.name, event.customerId())` are
updated. Two customers, two sessions: publishing customer 42's name never
touches customer 43.

## Global topics

Some signals are app-wide rather than per-entity (a feature flag, a global
counter, a service-status banner). Declare those with `Topic.global`:

```kotlin
object AppTopics {
    val EVENTS: Topic<Long, GlobalSubject> = Topic.global("events.consumed")
}
```

```kotlin
// subscribe (every session that renders this line)
val events = topic(AppTopics.EVENTS, 0L)
Text(computed { "Events consumed: ${events.get()}" })
```

```java
// publish (any thread)
hub.publish(AppTopics.EVENTS, count);
```

A global topic is still subscription-targeted: only sessions that called
`topic(AppTopics.EVENTS, …)` update. A session that never subscribed is
untouched. There is deliberately no "publish to every subject" operation.

The two-argument `topic(...)`/`publish(...)` overloads only accept
`Topic<T, GlobalSubject>`, so a scoped topic cannot be published globally and a
global topic cannot be published with a customer/order id. `GlobalSubject` is
reserved as an application subject.

## Semantics

- **Scoped, not broadcast.** The hub looks up subscribers by
  `(topic.name, subject)`. An unscoped global topic uses the singleton
  `GlobalSubject` as its subject, so it reaches only the sessions that
  subscribed to that topic — never every live session. There is no
  publish-to-all-subjects path.
- **Actor-confined.** Published values are applied on the session actor, so all
  signal reads/writes/effects keep the single-threaded contract.
- **Latest wins.** Publishes are coalesced per session: at most one drain task is
  scheduled, and only the newest value per subscription is applied. A hot
  producer cannot flood the actor.
- **`initial` seeds the signal.** A new session renders current data from its own
  source; subsequent changes arrive through `publish`. Late subscribers do not
  receive historical values.
- **Session-scoped lifetime.** Subscriptions end when the session closes.
  `SessionContext.current()?.unsubscribe(topic, subject)` is available for dynamic
  subjects; SSR sessions subscribe for the first paint but do not register with
  the hub.
- **Multi-app isolation.** Each mounted app has its own hub; `/` and `/billing`
  never see each other's topics.

## Notes

- Declare a topic once and share the constant. The topic name is the wire
  identity (a future Redis fan-out can route on it); two topics with the same
  name must carry the same types.
- The subject should be immutable with stable `equals`/`hashCode` — an id, a
  value class, or a record.
- For an app-wide stream, use an explicit singleton subject
  (`object GlobalSubject`) as the `S` type; every subscription is still scoped.
- If you need a lower-level escape hatch, `PathlandSession.submit(Runnable)` and
  `SessionContext.current()?.submit { }` run work on the session actor.
