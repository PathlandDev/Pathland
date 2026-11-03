@file:JvmName("KotlinFixtures")

package dev.pathland.quarkus

import dev.pathland.view.core.View
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.signal
import dev.pathland.view.dsl.value

/**
 * Kotlin-authored view used by the Java-only tests. The tests never author views
 * in Kotlin themselves; the Java CDI bean only mounts this factory.
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
