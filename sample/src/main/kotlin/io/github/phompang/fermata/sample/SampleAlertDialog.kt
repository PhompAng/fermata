package io.github.phompang.fermata.sample

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class SampleAlertDialog : DialogFragment() {
	interface OnDialogListener {
		fun onPositiveClick(requestCode: Int)

		fun onNegativeClick(requestCode: Int)

		fun onCancel(requestCode: Int)
	}

	companion object {
		private const val EXTRA_TITLE = "EXTRA_TITLE"
		private const val EXTRA_DESCRIPTION = "EXTRA_DESCRIPTION"
		private const val EXTRA_POSITIVE_TEXT = "EXTRA_POSITIVE_TEXT"
		private const val EXTRA_NEGATIVE_TEXT = "EXTRA_NEGATIVE_TEXT"
		private const val EXTRA_REQUEST_CODE = "EXTRA_REQUEST_CODE"

		fun newInstance(
			title: String,
			description: String,
			positiveText: String,
			negativeText: String,
			requestCode: Int,
		): SampleAlertDialog =
			SampleAlertDialog().apply {
				arguments =
					Bundle().apply {
						putString(EXTRA_TITLE, title)
						putString(EXTRA_DESCRIPTION, description)
						putString(EXTRA_POSITIVE_TEXT, positiveText)
						putString(EXTRA_NEGATIVE_TEXT, negativeText)
						putInt(EXTRA_REQUEST_CODE, requestCode)
					}
			}
	}

	val requestCode: Int
		get() = requireArguments().getInt(EXTRA_REQUEST_CODE)

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val args = requireArguments()
		return AlertDialog.Builder(requireContext())
			.setTitle(args.getString(EXTRA_TITLE))
			.setMessage(args.getString(EXTRA_DESCRIPTION))
			.setPositiveButton(args.getString(EXTRA_POSITIVE_TEXT)) { _, _ -> listener()?.onPositiveClick(requestCode) }
			.setNegativeButton(args.getString(EXTRA_NEGATIVE_TEXT)) { _, _ -> listener()?.onNegativeClick(requestCode) }
			.create()
	}

	override fun onCancel(dialog: DialogInterface) {
		listener()?.onCancel(requestCode)
	}

	private fun listener(): OnDialogListener? = parentFragment as? OnDialogListener ?: activity as? OnDialogListener
}
