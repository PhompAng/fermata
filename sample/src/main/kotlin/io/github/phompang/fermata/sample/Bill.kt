package io.github.phompang.fermata.sample

data class BillItem(val id: Int, val name: String, val promoCode: String?)

class BillRepository {
	private var items = seed()
	private var releaseAttempts = 0

	fun items(): List<BillItem> = items

	fun codeReleasedBy(itemId: Int): String? {
		val item = items.firstOrNull { it.id == itemId } ?: return null
		val code = item.promoCode ?: return null
		val othersShareCode = items.any { it.id != itemId && it.promoCode == code }
		return if (othersShareCode) null else code
	}

	fun remove(itemId: Int) {
		items = items.filterNot { it.id == itemId }
	}

	fun releaseCode(code: String) {
		releaseAttempts += 1
		if (releaseAttempts % 2 == 1) throw IllegalStateException("Promotion service timed out releasing $code")
	}

	fun reset() {
		items = seed()
		releaseAttempts = 0
	}

	private fun seed() =
		listOf(
			BillItem(id = 1, name = "Latte", promoCode = "PROMO-A"),
			BillItem(id = 2, name = "Croissant", promoCode = null),
			BillItem(id = 3, name = "Cake", promoCode = "PROMO-B"),
			BillItem(id = 4, name = "Tea", promoCode = "PROMO-B"),
		)
}
