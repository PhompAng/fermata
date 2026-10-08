package io.github.phompang.fermata.test

import io.github.phompang.fermata.Interaction
import io.github.phompang.fermata.InteractionDismissedException
import io.github.phompang.fermata.InteractionPort
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

class UnscriptedInteractionException(val interaction: Interaction<*>) :
	AssertionError("No scripted answer left for $interaction (${interaction::class.qualifiedName})")

class ScriptedInteractionPort(script: Script.() -> Unit) : InteractionPort {
	class Script internal constructor() {
		internal val handlers = LinkedHashMap<KClass<out Interaction<*>>, (Interaction<*>) -> Any?>()

		fun <R, I : Interaction<R>> on(type: KClass<I>, answer: (I) -> R) {
			@Suppress("UNCHECKED_CAST")
			handlers[type] = { interaction -> answer(interaction as I) }
		}

		fun <R, I : Interaction<R>> answersInOrder(type: KClass<I>, answers: List<R>) {
			val remaining = ArrayDeque(answers)
			handlers[type] = { interaction ->
				if (remaining.isEmpty()) throw UnscriptedInteractionException(interaction)
				remaining.removeFirst()
			}
		}

		fun <I : Interaction<*>> dismiss(type: KClass<I>) {
			handlers[type] = { interaction -> throw InteractionDismissedException(interaction) }
		}
	}

	private val handlers = Script().apply(script).handlers
	private val _asked = ArrayList<Interaction<*>>()

	val asked: List<Interaction<*>>
		get() = _asked.toList()

	override suspend fun <R> ask(interaction: Interaction<R>): R {
		_asked += interaction
		val handler = mostSpecificHandlerFor(interaction) ?: throw UnscriptedInteractionException(interaction)
		@Suppress("UNCHECKED_CAST")
		return handler(interaction) as R
	}

	private fun mostSpecificHandlerFor(interaction: Interaction<*>): ((Interaction<*>) -> Any?)? {
		val matching = handlers.keys.filter { it.isInstance(interaction) }
		val mostSpecific =
			matching.firstOrNull { candidate -> matching.none { other -> other != candidate && other.isSubclassOf(candidate) } }
		return mostSpecific?.let(handlers::get)
	}
}
