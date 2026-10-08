package io.github.phompang.fermata.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.phompang.fermata.Interaction
import io.github.phompang.fermata.InteractionHost

/**
 * Renders the Interaction currently pending on [host], if any, by handing it to [content].
 *
 * Only Interactions of type [I] are rendered; anything else pending on the same host is left
 * alone, so several handlers with disjoint hierarchies can share one host and each own its
 * dialogs. An Interaction no handler covers is not an error here: it stays pending and the
 * asking operation waits, which is visible in tests through `pending` and in the UI as a
 * missing dialog.
 *
 * The pending state is read with `collectAsState()` rather than `collectAsStateWithLifecycle()`
 * on purpose: this module depends on the Compose runtime only, so it stays usable from any
 * Compose host without pulling in `lifecycle-runtime-compose`. The cost is that the collector
 * keeps running while the hosting lifecycle is stopped. The flow is a `StateFlow` holding at
 * most one small handle, so that costs nothing measurable, and a dialog rendered from state has
 * nothing to do while the screen is not visible anyway.
 */
@Composable
inline fun <reified I : Interaction<*>> InteractionHandler(
	host: InteractionHost,
	content: @Composable (I) -> Unit,
) {
	val pending by host.pending.collectAsState()
	val typed = pending?.interaction as? I ?: return
	content(typed)
}
