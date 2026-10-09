package com.pathland.view;

import java.util.List;

/**
 * A **content-bearing** view: a container (stack/grid/scroll) or a control whose
 * content is supplied as children (spec DSL.md §2). Content is set by
 * {@link ViewBuilder#children(View...)}.
 */
public interface ChildrenView extends View {

    /** Replace this view's children. */
    void setChildren(List<View> children);
}
