package com.fintech.qa.core.base;

/**
 * Cardinal swipe directions used by {@link BasePage#swipe(SwipeDirection)}.
 *
 * <p>The direction describes the motion of the user's finger on the screen
 * (i.e. {@link #UP} drags content upward, revealing content below).</p>
 */
public enum SwipeDirection {

    /** Drag from the lower part of the screen towards the top (scrolls content down/up reveal below). */
    UP,

    /** Drag from the upper part of the screen towards the bottom. */
    DOWN,

    /** Drag from the right edge towards the left. */
    LEFT,

    /** Drag from the left edge towards the right. */
    RIGHT
}
