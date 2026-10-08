package io.github.phompang.fermata.sample

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.phompang.fermata.sample.databinding.ActivityXmlBillBinding
import io.github.phompang.fermata.sample.databinding.ItemBillBinding
import kotlinx.coroutines.launch

abstract class ViewBillActivity : FragmentActivity() {
	protected val viewModel: BillViewModel by viewModels()
	private lateinit var binding: ActivityXmlBillBinding

	protected abstract fun bindInteractionDialogs()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityXmlBillBinding.inflate(layoutInflater)
		setContentView(binding.root)

		binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
			val mode = if (checkedId == binding.modeUseCase.id) AskingMode.USE_CASE else AskingMode.VIEW_MODEL
			viewModel.onModeSelected(mode)
		}
		binding.reset.setOnClickListener { viewModel.onResetClicked() }

		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.uiState.collect(::render)
			}
		}
		bindInteractionDialogs()
	}

	private fun render(state: BillUiState) {
		val checkedId = if (state.mode == AskingMode.USE_CASE) binding.modeUseCase.id else binding.modeViewModel.id
		if (binding.modeGroup.checkedRadioButtonId != checkedId) binding.modeGroup.check(checkedId)

		binding.items.removeAllViews()
		val inflater = LayoutInflater.from(this)
		state.items.forEach { item ->
			val row = ItemBillBinding.inflate(inflater, binding.items, true)
			row.label.text = item.promoCode?.let { getString(R.string.item_with_code, item.name, it) } ?: item.name
			row.remove.setOnClickListener { viewModel.onRemoveClicked(item.id) }
		}

		binding.lastOutcome.text = state.lastOutcome?.let { getString(R.string.last_outcome, it) }
	}
}
