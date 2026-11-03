package dev.pathland.view.core

/** A content-bearing view: a container or control whose content is supplied as children. */
interface ChildrenView : View {
    /** Replace this view's children. */
    fun setChildren(children: MutableList<View>)
}
