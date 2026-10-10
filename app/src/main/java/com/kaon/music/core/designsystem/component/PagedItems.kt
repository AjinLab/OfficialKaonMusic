package com.kaon.music.core.designsystem.component

import androidx.compose.foundation.lazy.LazyListScope
import androidx.paging.compose.LazyPagingItems

/**
 * Emits paged items into a [LazyListScope] with stable keys.
 *
 * Wrapping `androidx.paging.compose.items` here rather than calling it per screen keeps two
 * properties in one place:
 *  - the key is the domain id, so a page load or a favorite toggle does not re-key surviving rows
 *    and force them to recompose,
 *  - a placeholder slot (`null` item) renders nothing rather than crashing, which is what
 *    `enablePlaceholders = true` requires of the consumer.
 */
inline fun <T : Any> LazyListScope.pagedItems(
    items: LazyPagingItems<T>,
    crossinline key: (T) -> Any,
    crossinline itemContent: @androidx.compose.runtime.Composable (T) -> Unit
) {
    items(
        count = items.itemCount,
        key = { index -> items.peek(index)?.let(key) ?: "placeholder_$index" }
    ) { index ->
        items[index]?.let { itemContent(it) }
    }
}
