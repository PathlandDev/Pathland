@file:JvmName("KotlinFixtures")

package dev.pathland.spring

import dev.pathland.view.core.Environment
import dev.pathland.view.core.Platform
import dev.pathland.view.core.View
import dev.pathland.view.core.subscription.Topic
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.signal
import dev.pathland.view.dsl.topic
import dev.pathland.view.dsl.value

/** A single Kotlin-authored text view for the Java-only tests. */
fun text(value: String): View = Text(value)

/**
 * Kotlin-authored view used by the Java-only tests. The tests never compile or
 * author Kotlin views themselves; the Java host only mounts this factory.
 */
fun counterApp(): View {
    val count = signal(0)
    return VStack(spacing = 8f) {
        Text("Kotlin-authored view")
        Button("Bump") { count.value = count.value + 1 }
        Text(computed { "n=${count.value}" })
    }
}

/**
 * The counter shape expected by `MultiSessionProbe`: node 1 is the Button and
 * node 2 is its dynamic `n=<count>` label.
 */
fun counterProbe(): View {
    val count = signal(0)
    return Button(
        title = computed { "n=${count.value}" },
        action = { count.value = count.value + 1 },
    )
}


/** Topics shared by the topic-subscription tests. */
object TopicFixtures {
    @JvmField
    val CUSTOMER_NAME: Topic<String, Long> = Topic.of("test.customer.name")

    @JvmField
    val EVENTS = Topic.global<Long>("test.events")
}

/**
 * A text view bound to the customer name topic for the customer id in the
 * session route (`/42`). Used by the mounted `/topics` test app.
 */
fun customerTopicProbe(): View = object : Component() {
    override fun body(): View {
        val id = Environment.value(Platform.ACTIVE_PATH).get().trim('/').toLongOrNull() ?: 0L
        val name = topic(TopicFixtures.CUSTOMER_NAME, id, "initial-$id")
        val events = topic(TopicFixtures.EVENTS, 0L)
        return VStack {
            Text(name)
            Text(computed { "events-${events.get()}" })
        }
    }
}
