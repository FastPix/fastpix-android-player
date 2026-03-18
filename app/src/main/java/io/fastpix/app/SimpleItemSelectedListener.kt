package io.fastpix.app

import android.view.View
import android.widget.AdapterView

internal class SimpleItemSelectedListener(
    private val onSelected: (String) -> Unit
) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        val value = parent?.getItemAtPosition(position)?.toString().orEmpty()
        onSelected(value)
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}

