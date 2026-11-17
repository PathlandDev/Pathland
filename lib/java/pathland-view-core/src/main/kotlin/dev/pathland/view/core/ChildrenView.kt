package dev.pathland.view.core

/**
 * A content-bearing view: a container or control whose content is supplied as
 * children.
 *
 * The DSL's builder scopes collect and register the children; content-bearing
 * controls may wrap them in their style's layout.
 */
interface ChildrenView : View {
    /** Replace this view's children. */
    fun setChildren(children: MutableList<View>)
}
