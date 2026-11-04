@file:JvmName("ServerFixtures")

package dev.pathland.server

import dev.pathland.view.core.MediaController
import dev.pathland.view.core.View
import dev.pathland.view.core.signal.Signal
import dev.pathland.view.core.signal.WritableSignal
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.Component
import dev.pathland.view.dsl.ProxyView
import dev.pathland.view.dsl.Slider
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.unaryPlus
import dev.pathland.view.dsl.value
import dev.pathland.view.dsl.windowMetadata
import dev.pathland.view.dsl.computed

/** A Kotlin-authored single text view for the Java server tests. */
fun text(value: String): View = Text(value)

fun text(value: Signal<String>): View = Text(value)

/** A Kotlin-authored button with an empty title. */
fun button(action: Runnable): View = Button("Tap") { action.run() }

/** A Kotlin-authored button with a title. */
fun button(title: String, action: Runnable): View = Button(title) { action.run() }

/** A Kotlin-authored vertical stack over explicit children. */
fun vstack(vararg children: View): View = VStack {
    for (child in children) +child
}

/** A Kotlin-authored window-metadata wrapper. */
fun windowMetadata(content: View, title: String, description: String, locale: String): View =
    content.windowMetadata {
        title(title)
        description(description)
        locale(locale)
    }

/** A button whose label is a bound signal (the multi-session probe shape). */
fun buttonWithLabel(action: Runnable, label: Signal<String>): View =
    Button(computed { label.value }) { action.run() }

/** A Kotlin-authored slider over the given signal. */
fun slider(value: WritableSignal<Float>): View = Slider(value, 0f..100f)

/** A stack that registers [controller] for root media projection, then renders [children]. */
fun registering(controller: MediaController, vararg children: View): View = object : Component() {
    override fun body(): View {
        controller.register()
        return VStack {
            for (child in children) +child
        }
    }
}

/** A Kotlin-authored proxy view. */
fun proxy(endpoint: String): View = ProxyView(endpoint)

fun proxy(endpoint: String, route: String): View = ProxyView(endpoint, route)

/** A counter button (node 1) with an `n=<count>` label (node 2). */
fun counter(): View {
    val count = dev.pathland.view.core.signal.Signals.signal(0)
    return Button(computed { "n=${count.get()}" }) { count.update { v -> v + 1 } }
}

/** The reactive proxy/plain-text subtree used by the proxy lifecycle test. */
fun proxyDemo(show: Signal<Boolean>): View = VStack {
    +View.of {
        if (show.value) vstack(proxy("http://b.example")) else Text("no ad")
    }
    Text("local")
}
