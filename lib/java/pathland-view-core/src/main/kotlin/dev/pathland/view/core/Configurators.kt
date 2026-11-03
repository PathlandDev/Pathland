package dev.pathland.view.core

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/** Shared plumbing for single-value shortcuts. */
object Configurators {
    @JvmStatic
    fun <V, C> view(
        config: Supplier<C>,
        ctor: Function<C, V>,
        setter: Consumer<C>,
    ): ViewBuilder<V, C> where V : View, V : Configurable<C>, C : View.Config {
        val c = config.get()
        setter.accept(c)
        return ViewBuilder(ctor.apply(c))
    }

    @JvmStatic
    fun <M, C> modifier(config: Supplier<C>, ctor: Function<C, M>, setter: Consumer<C>): M {
        val c = config.get()
        setter.accept(c)
        return ctor.apply(c)
    }
}
