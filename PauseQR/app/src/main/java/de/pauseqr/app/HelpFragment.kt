package de.pauseqr.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/** Hilfe & FAQ: statische Liste häufiger Fragen. */
class HelpFragment : Fragment(R.layout.fragment_help) {

    private data class Faq(val question: Int, val answer: Int)

    private val faqs = listOf(
        Faq(R.string.faq_q1, R.string.faq_a1),
        Faq(R.string.faq_q2, R.string.faq_a2),
        Faq(R.string.faq_q3, R.string.faq_a3),
        Faq(R.string.faq_q4, R.string.faq_a4),
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = view.findViewById<LinearLayout>(R.id.faqContainer)
        val inflater = layoutInflater
        faqs.forEach { faq ->
            val item = inflater.inflate(R.layout.item_faq, container, false)
            item.findViewById<TextView>(R.id.tvQuestion).setText(faq.question)
            item.findViewById<TextView>(R.id.tvAnswer).setText(faq.answer)
            container.addView(item)
        }
    }
}
