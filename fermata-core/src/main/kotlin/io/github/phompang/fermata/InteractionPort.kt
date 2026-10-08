package io.github.phompang.fermata

interface InteractionPort {
	suspend fun <R> ask(interaction: Interaction<R>): R
}

suspend fun <R> InteractionPort.askOrNull(interaction: Interaction<R>): R? =
	try {
		ask(interaction)
	} catch (_: InteractionDismissedException) {
		null
	}
