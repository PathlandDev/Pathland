package dev.pathland.demo.quarkus.kotlin

import dev.pathland.view.core.FontWeight
import dev.pathland.view.core.View
import dev.pathland.view.dsl.Button
import dev.pathland.view.dsl.ForEach
import dev.pathland.view.dsl.HStack
import dev.pathland.view.dsl.KotlinView
import dev.pathland.view.dsl.Text
import dev.pathland.view.dsl.TextField
import dev.pathland.view.dsl.VStack
import dev.pathland.view.dsl.computed
import dev.pathland.view.dsl.fontSize
import dev.pathland.view.dsl.fontWeight
import dev.pathland.view.dsl.frame
import dev.pathland.view.dsl.padding

/**
 * A small Kotlin-owned view that exercises persisted state, two-way text input,
 * reactive plain-Kotlin control flow, `ForEach`, and the Kotlin modifier sugar.
 */
class KotlinDemoView : KotlinView() {

    private var count by state(0)
    private val name = mutableState("", "demo.name")
    private var items by state(listOf("First item", "Second item"))

    override fun body(): View = VStack(spacing = 16f) {
        Text("Pathland Kotlin Demo")
            .fontSize(28f)
            .fontWeight(FontWeight.BOLD)
        Text("SSR plus live 16-byte opcode deltas, authored in Kotlin.")

        HStack(spacing = 12f) {
            Text("Count: $count")
            Button("Increment") { count += 1 }
            Button("Reset") { count = 0 }
        }

        TextField("Your name", name)
        Text(computed { "Hello, ${name.value.ifBlank { "world" }}" })

        HStack(spacing = 12f) {
            Button("Add item") { items = items + "Item ${items.size + 1}" }
            Button("Clear items") { items = emptyList() }
        }

        ForEach(items) { item, _ -> Text("• $item") }
    }.padding(24).frame { maxWidth(720f) }
}
