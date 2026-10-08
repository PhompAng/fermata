package io.github.phompang.fermata

import kotlinx.coroutines.CancellationException

class InteractionDismissedException(val interaction: Interaction<*>) :
	CancellationException("Interaction was dismissed without an answer: ${interaction::class.qualifiedName}")
