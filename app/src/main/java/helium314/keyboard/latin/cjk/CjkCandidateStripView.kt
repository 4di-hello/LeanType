// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.cjk

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import helium314.keyboard.latin.R

/**
 * Horizontally scrollable candidate bar for CJK input.
 * Displays pre-edit composition (e.g. [ni'hao]), numbered candidate chips,
 * and pagination stepping controls. Swapped in place of the standard Latin suggestion strip.
 */
class CjkCandidateStripView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val preeditView: TextView
    private val dividerView: View
    private val scrollView: HorizontalScrollView
    private val candidatesContainer: LinearLayout
    private val prevButton: ImageButton
    private val nextButton: ImageButton

    var onCandidateClickListener: ((index: Int) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.cjk_candidate_strip_view, this, true)

        preeditView = findViewById(R.id.cjk_strip_preedit)
        dividerView = findViewById(R.id.cjk_strip_divider)
        scrollView = findViewById(R.id.cjk_strip_scroll)
        candidatesContainer = findViewById(R.id.cjk_strip_candidates_container)
        prevButton = findViewById(R.id.cjk_strip_prev_page)
        nextButton = findViewById(R.id.cjk_strip_next_page)

        prevButton.setOnClickListener {
            scrollView.smoothScrollBy(-scrollView.width / 2, 0)
        }
        nextButton.setOnClickListener {
            scrollView.smoothScrollBy(scrollView.width / 2, 0)
        }
    }

    /**
     * Updates the strip with current candidates and pre-edit buffer.
     */
    fun update(candidates: List<CjkCandidate>, selectedIndex: Int, preedit: String) {
        // 1. Update Pre-edit string
        if (preedit.isNotEmpty()) {
            preeditView.text = "[$preedit]"
            preeditView.visibility = View.VISIBLE
            dividerView.visibility = View.VISIBLE
        } else {
            preeditView.visibility = View.GONE
            dividerView.visibility = View.GONE
        }

        // 2. Populate Candidates
        candidatesContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)

        candidates.forEachIndexed { index, candidate ->
            val itemView = inflater.inflate(R.layout.cjk_candidate_item, candidatesContainer, false)
            val indexView = itemView.findViewById<TextView>(R.id.cjk_candidate_index)
            val textView = itemView.findViewById<TextView>(R.id.cjk_candidate_text)

            indexView.text = "${index + 1}."
            textView.text = candidate.text

            if (index == selectedIndex) {
                textView.paint.isFakeBoldText = true
            }

            itemView.setOnClickListener {
                onCandidateClickListener?.invoke(index)
            }

            candidatesContainer.addView(itemView)
        }

        // 3. Show pagination controls if candidate count exceeds single screen width
        val hasCandidates = candidates.isNotEmpty()
        prevButton.visibility = if (hasCandidates && candidates.size > 5) View.VISIBLE else View.GONE
        nextButton.visibility = if (hasCandidates && candidates.size > 5) View.VISIBLE else View.GONE

        // Scroll back to start on fresh candidates
        scrollView.scrollTo(0, 0)
    }

    /**
     * Clears candidate list and pre-edit text from the strip.
     */
    fun clear() {
        preeditView.text = ""
        preeditView.visibility = View.GONE
        dividerView.visibility = View.GONE
        candidatesContainer.removeAllViews()
        prevButton.visibility = View.GONE
        nextButton.visibility = View.GONE
    }
}
