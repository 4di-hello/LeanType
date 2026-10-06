// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.MainKeyboardView
import helium314.keyboard.keyboard.PointerTracker
import helium314.keyboard.keyboard.internal.KeyDrawParams
import helium314.keyboard.keyboard.internal.KeyVisualAttributes
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.isRepeatableToolbarKey
import helium314.keyboard.latin.utils.RepeatableKeyTouchListener
import helium314.keyboard.latin.utils.getCodeForToolbarKey
import helium314.keyboard.latin.utils.getCodeForToolbarKeyLongClick
import helium314.keyboard.latin.utils.getEnabledClipboardToolbarKeys
import helium314.keyboard.latin.utils.dpToPx
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.setToolbarButtonsActivatedStateOnPrefChange

@SuppressLint("CustomViewStyleable")
class ClipboardHistoryView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyle: Int = R.attr.clipboardHistoryViewStyle
) : LinearLayout(context, attrs, defStyle), View.OnClickListener,
    ClipboardDao.Listener, OnKeyEventListener, KeyboardActionListener,
    View.OnLongClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val clipboardLayoutParams = ClipboardLayoutParams(context)
    private val pinIconId: Int
    private val keyBackgroundId: Int

    private lateinit var clipboardRecyclerView: ClipboardHistoryRecyclerView
    private lateinit var placeholderView: TextView
    private val toolbarKeys = mutableListOf<ImageButton>()
    private lateinit var clipboardAdapter: ClipboardAdapter

    lateinit var keyboardActionListener: KeyboardActionListener
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager

    init {
        val clipboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.ClipboardHistoryView, defStyle, R.style.ClipboardHistoryView)
        pinIconId = clipboardViewAttr.getResourceId(R.styleable.ClipboardHistoryView_iconPinnedClip, 0)
        clipboardViewAttr.recycle()
        @SuppressLint("UseKtx") // suggestion does not work
        val keyboardViewAttr = context.obtainStyledAttributes(attrs, R.styleable.KeyboardView, defStyle, R.style.KeyboardView)
        keyBackgroundId = keyboardViewAttr.getResourceId(R.styleable.KeyboardView_keyBackground, 0)
        keyboardViewAttr.recycle()
        if (Settings.getValues().mSecondaryStripVisible) {
            getEnabledClipboardToolbarKeys(context.prefs())
                .forEach { toolbarKeys.add(createToolbarKey(context, it)) }
        }
        fitsSystemWindows = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val res = context.resources
        val isFloating = ResourceUtils.getFloatingKeyboardWidth() > 0 ||
                (helium314.keyboard.latin.LatinIME.getInstance()?.floatingKeyboardManager?.let {
                    it.isFloating || (Settings.getValues().mRememberFloatingKeyboard && it.wasFloatingLastTime())
                } == true)
        val padH = if (isFloating) 0 else (paddingLeft + paddingRight)
        val padV = if (isFloating) 0 else (paddingTop + paddingBottom)
        // The main keyboard expands to the entire this {@link KeyboardView}.
        val width = ResourceUtils.getKeyboardWidth(context, Settings.getValues()) + padH
        val height = ResourceUtils.getSecondaryKeyboardHeight(res, Settings.getValues()) + padV
        setMeasuredDimension(width, height)
    }

    private lateinit var searchBar: android.widget.EditText
    private lateinit var clearSearch: android.widget.ImageButton
    private lateinit var backSearch: android.widget.ImageButton
    private lateinit var searchOverlay: android.view.View
    private lateinit var emptyViewIcon: android.widget.ImageView
    private lateinit var emptyViewText: android.widget.TextView
    private lateinit var emptyViewContainer: View
    private lateinit var listContainer: View

    private var actionPillOverlay: View? = null
    private var actionPill: View? = null
    private var actionPinButton: ImageButton? = null
    private var actionEditButton: ImageButton? = null
    private var actionDeleteButton: ImageButton? = null
    private var actionSelectButton: ImageButton? = null

    private var confirmationBar: View? = null
    private val confirmationDismissRunnable = Runnable { dismissConfirmationBar() }
    private val confirmationHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var editorInfo: EditorInfo? = null
    // We already have keyboardActionListener property

    private val searchWatcher = object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            val query = s?.toString() ?: ""
            clearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            clipboardAdapter.filter(query)
            // Empty view logic likely needs adjustment for "search mode" vs "list mode"? 
            // Actually, if we filter, the list updates. 
            // In Search Mode, do we see the list? 
            // "Type query -> Hit Enter -> View returns to filtered list"
            // So while typing, maybe we DON'T see list?
            // "redirected to keyboard key page ... indicator showing we are typing"
            // Let's assume while typing, we just see the input. 
            // BUT live filtering is nice. I will keep list visible if overlay allows (it sits on top?).
            // If overlay covers everything, then we don't see it.
            // Let's follow "redirected to keyboard key page" -> Overlay covers list.
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initialize() { // needs to be delayed for access to ClipboardStrip, which is not a child of this view
        if (this::clipboardAdapter.isInitialized) return
        val colors = Settings.getValues().mColors
        clipboardAdapter = ClipboardAdapter(clipboardLayoutParams, this).apply {
            itemBackgroundId = keyBackgroundId
            pinnedIconResId = pinIconId
            onClipLongClickListener = { entry, anchor -> showClipActionPill(entry, anchor) }
            onSelectionChangedListener = { selectedCount, totalCount ->
                updateSelectionDisplay(selectedCount, totalCount)
            }
        }
        
        // Search & Empty View init
        searchOverlay = findViewById(R.id.clipboard_search_overlay)
        searchBar = findViewById(R.id.clipboard_search_bar)
        clearSearch = findViewById(R.id.clipboard_clear_search)
        backSearch = findViewById(R.id.clipboard_search_back)
        emptyViewContainer = findViewById(R.id.clipboard_empty_view)
        emptyViewIcon = findViewById(R.id.clipboard_empty_icon)
        emptyViewText = findViewById(R.id.clipboard_empty_text)
        
        // Locate the list container if possible, or just the RecyclerView
        // Our XML has FrameLayout around list/empty. 
        // We might want to toggle visibility of that FrameLayout vs Overlay?
        // Let's assume RecyclerView is enough if Overlay is "match_parent" and on top.

        searchBar.addTextChangedListener(searchWatcher)
        clearSearch.setOnClickListener { searchBar.setText("") }
        backSearch.setOnClickListener { stopSearchMode() }
        
        // Make sure Enter key submits search
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                stopSearchMode()
                return@setOnEditorActionListener true
            }
            false
        }
        
        // Coloring
        colors.setBackground(searchOverlay, ColorType.MAIN_BACKGROUND) // Overlay background
        searchBar.setTextColor(colors.get(ColorType.KEY_TEXT))
        val hintColor = colors.get(ColorType.KEY_TEXT)
        searchBar.setHintTextColor((hintColor and 0x00FFFFFF) or 0x80000000.toInt()) // semi-transparent
        colors.setColor(clearSearch, ColorType.KEY_ICON)
        colors.setColor(backSearch, ColorType.KEY_ICON)
        // Tint empty icon
        val iconColor = colors.get(ColorType.KEY_ICON)
        emptyViewIcon.setColorFilter(iconColor)
        emptyViewText.setTextColor(iconColor)

        // removed placeholderView logic as it was unused/confusing

        clipboardRecyclerView = findViewById<ClipboardHistoryRecyclerView>(R.id.clipboard_list).apply {
            val colCount = resources.getInteger(R.integer.config_clipboard_keyboard_col_count)
            layoutManager = StaggeredGridLayoutManager(colCount, StaggeredGridLayoutManager.VERTICAL)
            @Suppress("deprecation") // "no cache" should be fine according to warning in https://developer.android.com/reference/android/view/ViewGroup#setPersistentDrawingCache(int)
            persistentDrawingCache = PERSISTENT_NO_CACHE
            clipboardLayoutParams.setListProperties(this)
            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                    if (newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
                        dismissActionPill()
                    }
                }
            })
        }

        confirmationBar = findViewById(R.id.clipboard_confirmation_bar)
        confirmationBar?.let { bar ->
            try {
                colors.setBackground(bar, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
                bar.findViewById<TextView>(R.id.clipboard_confirmation_text)?.setTextColor(colors.get(ColorType.KEY_TEXT))
                bar.findViewById<TextView>(R.id.clipboard_confirmation_button)?.setTextColor(colors.get(ColorType.KEY_TEXT))
            } catch (_: Exception) {}
            bar.findViewById<View>(R.id.clipboard_confirmation_button)?.setOnClickListener {
                clipboardHistoryManager.clearHistory()
                dismissConfirmationBar()
            }
        }

        actionPillOverlay = findViewById(R.id.clipboard_action_pill_overlay)
        actionPill = findViewById(R.id.clipboard_action_pill)
        actionPinButton = findViewById(R.id.clip_action_pin)
        actionEditButton = findViewById(R.id.clip_action_edit)
        actionDeleteButton = findViewById(R.id.clip_action_delete)
        actionSelectButton = findViewById(R.id.clip_action_select)
        actionPillOverlay?.setOnClickListener { dismissActionPill() }

        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        val clipboardStripScrollView = KeyboardSwitcher.getInstance().clipboardStripScrollView
        if (clipboardStripScrollView != null) {
            colors.setBackground(clipboardStripScrollView, ColorType.STRIP_BACKGROUND)
        }
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach {
            clipboardStrip.addView(it)
            val tag = it.tag
            if (tag is ToolbarKey && isRepeatableToolbarKey(tag)) {
                it.setOnTouchListener(RepeatableKeyTouchListener { repeatCount ->
                    if (repeatCount == 0 || repeatCount % 4 == 0) {
                        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
                    }
                    val code = getCodeForToolbarKey(tag)
                    if (code != KeyCode.UNSPECIFIED) {
                        keyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, repeatCount > 0)
                    }
                })
            } else {
                it.setOnClickListener(this@ClipboardHistoryView)
                it.setOnLongClickListener(this@ClipboardHistoryView)
            }
            colors.setColor(it, ColorType.TOOL_BAR_KEY)
            it.setBackgroundResource(R.drawable.toolbar_key_background)
            colors.setColor(it.background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
        }
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }
    }

    private lateinit var searchBarTextView: TextView
    private var searchQuery = StringBuilder()
    private var backButton: ImageButton? = null
    
    // We reuse searchWatcher logic but applied manually or to a hidden text view if needed.
    // Actually we just filter manually now.
    private var searchCursorPos = 0

    private fun configureInlineTextView(textView: TextView) {
        textView.maxLines = 1
        textView.setHorizontallyScrolling(true)
        textView.isHorizontalScrollBarEnabled = false
        textView.overScrollMode = View.OVER_SCROLL_ALWAYS
    }

    private fun ensureCursorVisible(textView: TextView, cursorPos: Int) {
        if (textView.layout == null) {
            textView.post { ensureCursorVisible(textView, cursorPos) }
            return
        }
        val safePos = cursorPos.coerceIn(0, textView.text.length)
        textView.bringPointIntoView(safePos)
    }
    
    private fun startSearchMode() {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        clipboardStrip.removeAllViews()
        
        searchBarTextView = TextView(context).apply {
             layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
             gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
             textSize = 16f
             setTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT))
             hint = context.getString(R.string.clipboard_search_hint)
             setHintTextColor(Settings.getValues().mColors.get(ColorType.KEY_TEXT) and 0x00FFFFFF or 0x80000000.toInt())
             setPadding(32, 0, 0, 0)
        }
        configureInlineTextView(searchBarTextView)
        clipboardStrip.addView(searchBarTextView)
        
        backButton = ImageButton(context).apply {
             layoutParams = LinearLayout.LayoutParams(
                 resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width), 
                 LinearLayout.LayoutParams.MATCH_PARENT
             )
             setImageResource(R.drawable.ic_close)
             setBackgroundResource(R.drawable.toolbar_key_background)
             setColorFilter(Settings.getValues().mColors.get(ColorType.KEY_ICON))
             Settings.getValues().mColors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
             setOnClickListener { stopSearchMode() }
        }
        clipboardStrip.addView(backButton)
        
        searchQuery.clear()
        searchCursorPos = 0
        updateSearchDisplay()

        setBottomRowLayout(KeyboardId.ELEMENT_ALPHABET)
        
        clipboardRecyclerView.visibility = View.GONE
        emptyViewContainer.visibility = View.GONE
        updateClipboardGestureSuppression()
    }

    private fun stopSearchMode() {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach { clipboardStrip.addView(it) }
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }
        
        if (searchQuery.isNotEmpty()) {
             clipboardAdapter.filter(searchQuery.toString())
        } else {
             clipboardAdapter.filter("")
        }
        
        setBottomRowLayout(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)
        
        clipboardRecyclerView.visibility = View.VISIBLE
        updateEmptyView(clipboardAdapter.isFiltering)
        updateClipboardGestureSuppression()
    }

    private fun updateEmptyView(isSearch: Boolean) {
        val isEmpty = clipboardAdapter.itemCount == 0
        emptyViewContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
        if (isEmpty) {
            emptyViewText.setText(if (isSearch) R.string.clipboard_no_search_results else R.string.clipboard_empty_text)
        }
    }

    private var editEntry: ClipboardHistoryEntry? = null
    private var editText = StringBuilder()
    private var editCursorPos = 0
    private var deleteSwipeStartPos = -1
    private var currentDeleteSwipePos = -1
    private lateinit var editTextView: TextView

    val inEditMode: Boolean
        get() = editEntry != null

    fun startEditMode(entry: ClipboardHistoryEntry) {
        clipboardRecyclerView.dismissUndoBar()
        dismissConfirmationBar()
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode) {
            stopSearchMode()
        }

        editEntry = entry
        editText = StringBuilder(entry.text)
        editCursorPos = editText.length

        clipboardStrip.removeAllViews()

        val colors = Settings.getValues().mColors
        val btnWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)

        editTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 16f
            setTextColor(colors.get(ColorType.KEY_TEXT))
            setPadding(32, 0, 0, 0)
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    val layout = layout
                    if (layout != null) {
                        val x = event.x - totalPaddingLeft + scrollX
                        val y = event.y - totalPaddingTop + scrollY
                        val line = layout.getLineForVertical(y.toInt().coerceIn(0, (layout.height - 1).coerceAtLeast(0)))
                        var offset = layout.getOffsetForHorizontal(line, x)
                        if (offset > editCursorPos) {
                            offset = (offset - 1).coerceAtLeast(0)
                        }
                        editCursorPos = offset.coerceIn(0, editText.length)
                        updateEditDisplay()
                    }
                }
                true
            }
        }
        configureInlineTextView(editTextView)
        clipboardStrip.addView(editTextView)

        val saveButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_setup_check)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            setOnClickListener { stopEditMode(save = true) }
        }
        clipboardStrip.addView(saveButton)

        val cancelButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            setOnClickListener { stopEditMode(save = false) }
        }
        clipboardStrip.addView(cancelButton)

        clipboardRecyclerView.visibility = View.GONE
        emptyViewContainer.visibility = View.GONE

        updateEditDisplay()

        setBottomRowLayout(KeyboardId.ELEMENT_ALPHABET)
        updateClipboardGestureSuppression()
    }

    private fun stopEditMode(save: Boolean) {
        val entry = editEntry ?: return

        if (save) {
            val newText = editText.toString().trim()
            if (newText.isNotEmpty() && newText != entry.text) {
                clipboardHistoryManager.updateClipText(entry.id, newText)
            }
        }

        editEntry = null
        deleteSwipeStartPos = -1
        currentDeleteSwipePos = -1

        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach { clipboardStrip.addView(it) }
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }

        setBottomRowLayout(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)

        clipboardRecyclerView.visibility = View.VISIBLE
        updateEmptyView(clipboardAdapter.isFiltering)
        updateClipboardGestureSuppression()
    }

    private fun updateEditDisplay() {
        updateEditDisplayWithSelection(-1, -1)
    }

    private fun updateEditDisplayWithSelection(selStart: Int, selEnd: Int) {
        if (!this::editTextView.isInitialized) return
        val colors = Settings.getValues().mColors
        val textColor = colors.get(ColorType.KEY_TEXT)

        if (selStart >= 0 && selEnd > selStart && selEnd <= editText.length) {
            val sb = android.text.SpannableStringBuilder(editText)
            val highlightColor = (textColor and 0x00FFFFFF) or 0x50000000
            sb.setSpan(
                android.text.style.BackgroundColorSpan(highlightColor),
                selStart,
                selEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            editTextView.text = sb
            ensureCursorVisible(editTextView, selEnd)
        } else {
            val sb = android.text.SpannableStringBuilder(editText)
            val pos = editCursorPos.coerceIn(0, sb.length)
            sb.insert(pos, "|")
            sb.setSpan(
                android.text.style.ForegroundColorSpan(textColor),
                pos,
                pos + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            editTextView.text = sb
            ensureCursorVisible(editTextView, pos + 1)
        }
    }

    private fun updateSearchDisplay() {
        updateSearchDisplayWithSelection(-1, -1)
    }

    private fun updateSearchDisplayWithSelection(selStart: Int, selEnd: Int) {
        if (!this::searchBarTextView.isInitialized) return
        val colors = Settings.getValues().mColors
        val textColor = colors.get(ColorType.KEY_TEXT)

        if (selStart >= 0 && selEnd > selStart && selEnd <= searchQuery.length) {
            val sb = android.text.SpannableStringBuilder(searchQuery)
            val highlightColor = (textColor and 0x00FFFFFF) or 0x50000000
            sb.setSpan(
                android.text.style.BackgroundColorSpan(highlightColor),
                selStart,
                selEnd,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            searchBarTextView.text = sb
            ensureCursorVisible(searchBarTextView, selEnd)
        } else {
            val sb = android.text.SpannableStringBuilder(searchQuery)
            val pos = searchCursorPos.coerceIn(0, sb.length)
            sb.insert(pos, "|")
            sb.setSpan(
                android.text.style.ForegroundColorSpan(textColor),
                pos,
                pos + 1,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            searchBarTextView.text = sb
            ensureCursorVisible(searchBarTextView, pos + 1)
        }
    }

    private var selectionCountTextView: TextView? = null
    private var selectionSelectAllButton: ImageButton? = null
    private var selectionPinButton: ImageButton? = null
    private var selectionDeleteButton: ImageButton? = null

    val inSelectionMode: Boolean
        get() = this::clipboardAdapter.isInitialized && clipboardAdapter.isSelectionMode

    fun startSelectionMode(initialSelectedId: Long? = null) {
        if (!this::clipboardAdapter.isInitialized) return
        clipboardRecyclerView.dismissUndoBar()
        dismissConfirmationBar()
        dismissActionPill()
        if (inEditMode) {
            stopEditMode(save = false)
        }
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode) {
            stopSearchMode()
        }

        clipboardStrip.removeAllViews()

        val colors = Settings.getValues().mColors
        val btnWidth = resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width)

        val closeButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_close)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            contentDescription = context.getString(R.string.voice_action_cancel)
            setOnClickListener { stopSelectionMode() }
        }
        clipboardStrip.addView(closeButton)

        val countTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 16f
            setTextColor(colors.get(ColorType.KEY_TEXT))
            setPadding(16.dpToPx(resources), 0, 0, 0)
        }
        configureInlineTextView(countTextView)
        selectionCountTextView = countTextView
        clipboardStrip.addView(countTextView)

        val selectAllButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_select_all_rounded)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            contentDescription = context.getString(R.string.clipboard_action_select_all)
            setOnClickListener {
                val total = clipboardAdapter.getAllClipEntries().size
                if (clipboardAdapter.selectedEntryIds.size == total && total > 0) {
                    clipboardAdapter.deselectAll()
                } else {
                    clipboardAdapter.selectAll()
                }
            }
        }
        selectionSelectAllButton = selectAllButton
        clipboardStrip.addView(selectAllButton)

        val pinButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_clipboard_pin_rounded)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            contentDescription = context.getString(R.string.clipboard_action_pin)
            setOnClickListener {
                val selected = clipboardAdapter.getSelectedEntries()
                if (selected.isNotEmpty()) {
                    val allPinned = selected.all { it.isPinned }
                    clipboardHistoryManager.setClipsPinned(selected.map { it.id }, !allPinned)
                    clipboardAdapter.refresh()
                    updateSelectionDisplay()
                }
            }
        }
        selectionPinButton = pinButton
        clipboardStrip.addView(pinButton)

        val deleteButton = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(btnWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            setImageResource(R.drawable.ic_bin_rounded)
            setBackgroundResource(R.drawable.toolbar_key_background)
            setColorFilter(colors.get(ColorType.KEY_ICON))
            colors.setColor(background, ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND)
            contentDescription = context.getString(R.string.clipboard_action_delete)
            setOnClickListener {
                val selected = clipboardAdapter.getSelectedEntries()
                if (selected.isNotEmpty()) {
                    val deletedList = clipboardHistoryManager.deleteEntries(selected.map { it.id })
                    stopSelectionMode()
                    if (deletedList.isNotEmpty()) {
                        clipboardAdapter.refresh()
                        clipboardRecyclerView.showUndoBar(deletedList)
                    }
                }
            }
        }
        selectionDeleteButton = deleteButton
        clipboardStrip.addView(deleteButton)

        clipboardAdapter.setSelectionMode(true)
        if (initialSelectedId != null) {
            clipboardAdapter.toggleSelection(initialSelectedId)
        } else {
            updateSelectionDisplay()
        }
    }

    fun stopSelectionMode() {
        if (!this::clipboardAdapter.isInitialized) return
        dismissActionPill()
        clipboardAdapter.setSelectionMode(false)
        selectionCountTextView = null
        selectionSelectAllButton = null
        selectionPinButton = null
        selectionDeleteButton = null

        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        clipboardStrip.removeAllViews()
        toolbarKeys.forEach { clipboardStrip.addView(it) }
        applyClipboardToolbarKeyLayoutParams()
        clipboardStrip.post { applyClipboardToolbarKeyLayoutParams() }
        updateEmptyView(clipboardAdapter.isFiltering)
    }

    fun updateSelectionDisplay(
        selectedCount: Int = if (this::clipboardAdapter.isInitialized) clipboardAdapter.selectedEntryIds.size else 0,
        totalCount: Int = if (this::clipboardAdapter.isInitialized) clipboardAdapter.getAllClipEntries().size else 0
    ) {
        val countView = selectionCountTextView ?: return
        countView.text = if (selectedCount == 0) {
            context.getString(R.string.clipboard_select_items)
        } else {
            context.getString(R.string.clipboard_selected_count, selectedCount)
        }

        selectionSelectAllButton?.let { btn ->
            val allSelected = selectedCount == totalCount && totalCount > 0
            btn.contentDescription = context.getString(
                if (allSelected) R.string.clipboard_action_deselect_all else R.string.clipboard_action_select_all
            )
        }

        val hasSelection = selectedCount > 0
        selectionPinButton?.let { btn ->
            btn.isEnabled = hasSelection
            btn.alpha = if (hasSelection) 1.0f else 0.4f
            val selected = clipboardAdapter.getSelectedEntries()
            val allPinned = selected.isNotEmpty() && selected.all { it.isPinned }
            btn.contentDescription = context.getString(
                if (allPinned) R.string.clipboard_action_unpin else R.string.clipboard_action_pin
            )
        }

        selectionDeleteButton?.let { btn ->
            btn.isEnabled = hasSelection
            btn.alpha = if (hasSelection) 1.0f else 0.4f
        }
    }

    fun dismissActionPill(): Boolean {
        val overlay = actionPillOverlay ?: return false
        if (overlay.visibility == View.VISIBLE) {
            overlay.visibility = View.GONE
            return true
        }
        return false
    }

    private fun showClipActionPill(entry: ClipboardHistoryEntry, anchorView: View): Boolean {
        val overlay = actionPillOverlay ?: return false
        val pill = actionPill ?: return false
        val pinBtn = actionPinButton ?: return false
        val editBtn = actionEditButton ?: return false
        val deleteBtn = actionDeleteButton ?: return false
        val selectBtn = actionSelectButton ?: return false

        val colors = Settings.getValues().mColors

        // Pill background
        val pillBg = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.clipboard_action_pill_background)?.mutate()
        if (pillBg != null) {
            colors.setColor(pillBg, ColorType.KEY_PREVIEW_BACKGROUND)
            pill.background = pillBg
        }

        // Configure Pin button
        val pinIconRes = if (pinIconId != 0) pinIconId else R.drawable.ic_clipboard_pin_rounded
        pinBtn.setImageResource(pinIconRes)
        if (entry.isPinned) {
            pinBtn.contentDescription = context.getString(R.string.clipboard_action_unpin)
            colors.setColor(pinBtn, ColorType.CLIPBOARD_PIN)
        } else {
            pinBtn.contentDescription = context.getString(R.string.clipboard_action_pin)
            colors.setColor(pinBtn, ColorType.KEY_ICON)
        }
        pinBtn.setOnClickListener {
            dismissActionPill()
            clipboardHistoryManager.toggleClipPinned(entry.id)
        }

        // Configure Edit button
        if (entry.imageUri == null && !entry.text.isNullOrEmpty()) {
            editBtn.visibility = View.VISIBLE
            colors.setColor(editBtn, ColorType.KEY_ICON)
            editBtn.setOnClickListener {
                dismissActionPill()
                startEditMode(entry)
            }
        } else {
            editBtn.visibility = View.GONE
        }

        // Configure Delete button
        colors.setColor(deleteBtn, ColorType.REMOVE_SUGGESTION_ICON)
        deleteBtn.setOnClickListener {
            dismissActionPill()
            deleteSingleEntry(entry)
        }

        // Configure Select button
        colors.setColor(selectBtn, ColorType.KEY_ICON)
        selectBtn.setOnClickListener {
            dismissActionPill()
            startSelectionMode(initialSelectedId = entry.id)
        }

        overlay.visibility = View.VISIBLE

        // Measure pill to calculate target position
        pill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pillWidth = pill.measuredWidth
        val pillHeight = pill.measuredHeight

        val anchorLoc = IntArray(2)
        anchorView.getLocationInWindow(anchorLoc)
        val overlayLoc = IntArray(2)
        overlay.getLocationInWindow(overlayLoc)

        val relX = anchorLoc[0] - overlayLoc[0]
        val relY = anchorLoc[1] - overlayLoc[1]

        val containerWidth = overlay.width.takeIf { it > 0 } ?: measuredWidth
        val containerHeight = overlay.height.takeIf { it > 0 } ?: measuredHeight

        val margin = 8.dpToPx(resources)
        val targetX = (relX + (anchorView.width - pillWidth) / 2).coerceIn(margin, (containerWidth - pillWidth - margin).coerceAtLeast(0))

        val gap = 6.dpToPx(resources)
        val preferredY = relY - pillHeight - gap
        val targetY = if (preferredY >= 4.dpToPx(resources)) {
            preferredY
        } else {
            (relY + anchorView.height + gap).coerceAtMost(containerHeight - pillHeight - 4.dpToPx(resources))
        }

        pill.translationX = targetX.toFloat()
        pill.translationY = targetY.toFloat()

        pill.alpha = 0f
        pill.scaleX = 0.85f
        pill.scaleY = 0.85f
        pill.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(120)
            ?.start()

        return true
    }

    private fun deleteSingleEntry(entry: ClipboardHistoryEntry) {
        clipboardRecyclerView.dismissUndoBar()
        dismissConfirmationBar()
        val deleted = clipboardHistoryManager.deleteEntryById(entry.id)
        if (deleted != null) {
            clipboardAdapter.refresh()
            clipboardRecyclerView.showUndoBar(listOf(deleted))
        }
    }

    private val currentBottomRowLayout: Int
        get() {
            val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard) ?: return KeyboardId.ELEMENT_ALPHABET
            return keyboardView.keyboard?.mId?.mElementId ?: KeyboardId.ELEMENT_ALPHABET
        }

    private fun isLayoutSwitchCode(code: Int): Boolean {
        return code == KeyCode.SYMBOL || code == KeyCode.SYMBOL_ALPHA || code == KeyCode.ALPHA
                || code == KeyCode.SHIFT || code == KeyCode.CAPS_LOCK
    }

    private fun handleLayoutSwitchInEditOrSearch(primaryCode: Int): Boolean {
        if (primaryCode == KeyCode.SYMBOL || primaryCode == KeyCode.SYMBOL_ALPHA || primaryCode == KeyCode.ALPHA) {
            val isOnSymbols = currentBottomRowLayout == KeyboardId.ELEMENT_SYMBOLS
                    || currentBottomRowLayout == KeyboardId.ELEMENT_SYMBOLS_SHIFTED
            val targetId = if (isOnSymbols) KeyboardId.ELEMENT_ALPHABET else KeyboardId.ELEMENT_SYMBOLS
            setBottomRowLayout(targetId)
            return true
        }
        if (primaryCode == KeyCode.SHIFT || primaryCode == KeyCode.CAPS_LOCK) {
            val targetId = when (currentBottomRowLayout) {
                KeyboardId.ELEMENT_SYMBOLS -> KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> KeyboardId.ELEMENT_SYMBOLS
                KeyboardId.ELEMENT_ALPHABET -> KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED
                KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED,
                KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
                KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED -> KeyboardId.ELEMENT_ALPHABET
                else -> KeyboardId.ELEMENT_ALPHABET
            }
            setBottomRowLayout(targetId)
            return true
        }
        return false
    }

    override fun onCodeInput(primaryCode: Int, x: Int, y: Int, isKeyRepeat: Boolean) {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip

        if (inSelectionMode && primaryCode == KeyCode.DELETE) {
            val selected = clipboardAdapter.getSelectedEntries()
            if (selected.isNotEmpty()) {
                val deletedList = clipboardHistoryManager.deleteEntries(selected.map { it.id })
                stopSelectionMode()
                if (deletedList.isNotEmpty()) {
                    clipboardAdapter.refresh()
                    clipboardRecyclerView.showUndoBar(deletedList)
                }
                return
            }
        }
        
        if (inEditMode) {
            if (handleLayoutSwitchInEditOrSearch(primaryCode)) {
                return
            }

            val char = if (primaryCode > 0) primaryCode.toChar() else null

            if (primaryCode == KeyCode.DELETE) {
                if (editCursorPos > 0) {
                    editText.deleteCharAt(editCursorPos - 1)
                    editCursorPos--
                    updateEditDisplay()
                }
            } else if (primaryCode == Constants.CODE_ENTER) {
                editText.insert(editCursorPos, "\n")
                editCursorPos++
                updateEditDisplay()
            } else if (primaryCode == Constants.CODE_SPACE) {
                editText.insert(editCursorPos, " ")
                editCursorPos++
                updateEditDisplay()
            } else if (primaryCode == KeyCode.ARROW_LEFT) {
                if (editCursorPos > 0) {
                    editCursorPos--
                    updateEditDisplay()
                }
            } else if (primaryCode == KeyCode.ARROW_RIGHT) {
                if (editCursorPos < editText.length) {
                    editCursorPos++
                    updateEditDisplay()
                }
            } else if (char != null) {
                editText.insert(editCursorPos, char.toString())
                editCursorPos++
                updateEditDisplay()
            }
            return
        }

        if (inSearchMode) {
            if (handleLayoutSwitchInEditOrSearch(primaryCode)) {
                return
            }

            val char = if (primaryCode > 0) primaryCode.toChar() else null
            
            if (primaryCode == KeyCode.DELETE) {
                if (searchCursorPos > 0) {
                    searchQuery.deleteCharAt(searchCursorPos - 1)
                    searchCursorPos--
                    updateSearchDisplay()
                    clipboardAdapter.filter(searchQuery.toString()) 
                }
            } else if (primaryCode == Constants.CODE_ENTER) {
                stopSearchMode()
            } else if (primaryCode == Constants.CODE_SPACE) {
                searchQuery.insert(searchCursorPos, " ")
                searchCursorPos++
                updateSearchDisplay()
                clipboardAdapter.filter(searchQuery.toString())
            } else if (primaryCode == KeyCode.ARROW_LEFT) {
                if (searchCursorPos > 0) {
                    searchCursorPos--
                    updateSearchDisplay()
                }
            } else if (primaryCode == KeyCode.ARROW_RIGHT) {
                if (searchCursorPos < searchQuery.length) {
                    searchCursorPos++
                    updateSearchDisplay()
                }
            } else if (char != null) {
                searchQuery.insert(searchCursorPos, char.toString())
                searchCursorPos++
                updateSearchDisplay()
                clipboardAdapter.filter(searchQuery.toString())
            } else {
                 stopSearchMode()
                 keyboardActionListener.onCodeInput(primaryCode, x, y, isKeyRepeat)
            }
            return 
        }
        
        keyboardActionListener.onCodeInput(primaryCode, x, y, isKeyRepeat)
    }
    
    override fun onTextInput(text: String?) {
         if (text == null) return
         if (inEditMode) {
              editText.insert(editCursorPos, text)
              editCursorPos += text.length
              updateEditDisplay()
              return
         }

         val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
         val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
         
         if (inSearchMode) {
             searchQuery.insert(searchCursorPos, text)
             searchCursorPos += text.length
             updateSearchDisplay()
             clipboardAdapter.filter(searchQuery.toString())
             updateEmptyView(true)
             return
         }
         
         keyboardActionListener.onTextInput(text)
    }

    override fun onImageSelected(imageUri: String) {
         keyboardActionListener.onImageSelected(imageUri)
    }

    override fun onPressKey(primaryCode: Int, repeatCount: Int, isSinglePointer: Boolean, hapticEvent: HapticEvent) {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if ((inEditMode || inSearchMode) && isLayoutSwitchCode(primaryCode)) return
        keyboardActionListener.onPressKey(primaryCode, repeatCount, isSinglePointer, hapticEvent)
    }
    override fun onReleaseKey(primaryCode: Int, withSliding: Boolean) {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if ((inEditMode || inSearchMode) && isLayoutSwitchCode(primaryCode)) return
        keyboardActionListener.onReleaseKey(primaryCode, withSliding)
    }
    override fun onLongPressKey(primaryCode: Int) {
        keyboardActionListener.onLongPressKey(primaryCode)
    }
    override fun onKeyDown(keyCode: Int, keyEvent: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (dismissActionPill()) return true
            if (inSelectionMode) {
                stopSelectionMode()
                return true
            }
        }
        return keyboardActionListener.onKeyDown(keyCode, keyEvent)
    }
    override fun onKeyUp(keyCode: Int, keyEvent: android.view.KeyEvent): Boolean {
        return keyboardActionListener.onKeyUp(keyCode, keyEvent)
    }
    override fun onStartBatchInput() { keyboardActionListener.onStartBatchInput() }
    override fun onUpdateBatchInput(p: helium314.keyboard.latin.common.InputPointers) { keyboardActionListener.onUpdateBatchInput(p) }
    override fun onEndBatchInput(p: helium314.keyboard.latin.common.InputPointers) { keyboardActionListener.onEndBatchInput(p) }
    override fun onCancelBatchInput() { keyboardActionListener.onCancelBatchInput() }
    override fun onCancelInput() { keyboardActionListener.onCancelInput() }
    override fun onFinishSlidingInput() { keyboardActionListener.onFinishSlidingInput() }
    override fun onCustomRequest(requestCode: Int): Boolean { return keyboardActionListener.onCustomRequest(requestCode) }
    override fun onHorizontalSpaceSwipe(steps: Int): Boolean {
        if (inEditMode) {
            val newPos = (editCursorPos + steps).coerceIn(0, editText.length)
            if (newPos != editCursorPos) {
                editCursorPos = newPos
                updateEditDisplay()
            }
            return true
        }
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode) {
            val newPos = (searchCursorPos + steps).coerceIn(0, searchQuery.length)
            if (newPos != searchCursorPos) {
                searchCursorPos = newPos
                updateSearchDisplay()
            }
            return true
        }
        return keyboardActionListener.onHorizontalSpaceSwipe(steps)
    }
    override fun onVerticalSpaceSwipe(steps: Int, action: Int): Boolean { return keyboardActionListener.onVerticalSpaceSwipe(steps, action) }
    override fun onEndSpaceSwipe() { keyboardActionListener.onEndSpaceSwipe() }
    override fun toggleNumpad(w: Boolean, f: Boolean): Boolean { return keyboardActionListener.toggleNumpad(w, f) }
    override fun onMoveDeletePointer(steps: Int) {
        if (inEditMode) {
            if (deleteSwipeStartPos == -1) {
                deleteSwipeStartPos = editCursorPos
                currentDeleteSwipePos = editCursorPos
            }
            currentDeleteSwipePos = (currentDeleteSwipePos + steps).coerceIn(0, editText.length)
            updateEditDisplayWithSelection(
                minOf(deleteSwipeStartPos, currentDeleteSwipePos),
                maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
            )
            return
        }
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode) {
            if (deleteSwipeStartPos == -1) {
                deleteSwipeStartPos = searchCursorPos
                currentDeleteSwipePos = searchCursorPos
            }
            currentDeleteSwipePos = (currentDeleteSwipePos + steps).coerceIn(0, searchQuery.length)
            updateSearchDisplayWithSelection(
                minOf(deleteSwipeStartPos, currentDeleteSwipePos),
                maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
            )
            return
        }
        keyboardActionListener.onMoveDeletePointer(steps)
    }
    override fun onUpWithDeletePointerActive() {
        if (inEditMode) {
            if (deleteSwipeStartPos != -1 && currentDeleteSwipePos != -1) {
                val start = minOf(deleteSwipeStartPos, currentDeleteSwipePos)
                val end = maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
                if (start < end) {
                    editText.delete(start, end)
                    editCursorPos = start
                }
            }
            deleteSwipeStartPos = -1
            currentDeleteSwipePos = -1
            updateEditDisplay()
            return
        }
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode) {
            if (deleteSwipeStartPos != -1 && currentDeleteSwipePos != -1) {
                val start = minOf(deleteSwipeStartPos, currentDeleteSwipePos)
                val end = maxOf(deleteSwipeStartPos, currentDeleteSwipePos)
                if (start < end) {
                    searchQuery.delete(start, end)
                    searchCursorPos = start
                }
            }
            deleteSwipeStartPos = -1
            currentDeleteSwipePos = -1
            updateSearchDisplay()
            clipboardAdapter.filter(searchQuery.toString())
            return
        }
        keyboardActionListener.onUpWithDeletePointerActive()
    }
    override fun resetMetaState() { keyboardActionListener.resetMetaState() }
    
    private fun setupClipKey(params: KeyDrawParams) {
        clipboardAdapter.apply {
            itemBackgroundId = keyBackgroundId
            itemTypeFace = params.mTypeface
            itemTextColor = params.mTextColor
            itemTextSize = params.mLabelSize.toFloat()
        }
    }

    private fun setupToolbarKeys() {
        applyClipboardToolbarKeyLayoutParams()
        KeyboardSwitcher.getInstance().clipboardStrip?.post { applyClipboardToolbarKeyLayoutParams() }
    }

    private fun setupBottomRowKeyboard(editorInfo: EditorInfo, listener: KeyboardActionListener) {
        // Initial setup only
        this.editorInfo = editorInfo
        this.keyboardActionListener = listener
        setBottomRowLayout(KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW)
    }

    private fun setBottomRowLayout(elementId: Int) {
        val editorInfo = this.editorInfo ?: return
        val keyboardView = findViewById<MainKeyboardView>(R.id.bottom_row_keyboard)
        keyboardView.setKeyPreviewPopupEnabled(Settings.getValues().mKeyPreviewPopupOn)
        keyboardView.setKeyboardActionListener(this)  // Set 'this' as listener to intercept
        PointerTracker.switchTo(keyboardView)
        val kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(context, editorInfo)
        val keyboard = kls.getKeyboard(elementId)
        keyboardView.setKeyboard(keyboard)
    }

    fun setHardwareAcceleratedDrawingEnabled(enabled: Boolean) {
        if (!enabled) return
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun startClipboardHistory(
            historyManager: ClipboardHistoryManager,
            keyVisualAttr: KeyVisualAttributes?,
            editorInfo: EditorInfo,
            keyboardActionListener: KeyboardActionListener
    ) {
        clipboardHistoryManager = historyManager
        initialize()
        setupToolbarKeys()
        historyManager.prepareClipboardHistory()
        historyManager.sortHistoryEntries()
        historyManager.setHistoryChangeListener(this)
        clipboardAdapter.clipboardHistoryManager = historyManager
        
        // Clear search on start
        searchQuery.clear() // Explicitly clear query builder
        searchBar.setText("")
        searchBar.clearFocus() // ensure focus is lost
        clipboardAdapter.filter("")
        updateEmptyView(false)
        stopSearchMode()

        val params = KeyDrawParams()
        params.updateParams(clipboardLayoutParams.bottomRowKeyboardHeight, keyVisualAttr)
        val settings = Settings.getInstance()
        settings.getCustomTypeface()?.let { params.mTypeface = it }
        setupClipKey(params)
        setupBottomRowKeyboard(editorInfo, keyboardActionListener)

        // Typeface for search and empty text
        params.mTypeface?.let { tf ->
            searchBar.typeface = tf
            emptyViewText.typeface = tf
        }
        searchBar.setTextColor(params.mTextColor)

        val keyboardWidth = ResourceUtils.getKeyboardWidth(context, settings.current)
        val keyboardAttr = context.obtainStyledAttributes(
            null, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard)
        val leftPadding = (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardLeftPadding,
            keyboardWidth, keyboardWidth, 0f)
                * settings.current.mSidePaddingScale).toInt()
        val rightPadding =  (keyboardAttr.getFraction(R.styleable.Keyboard_keyboardRightPadding,
            keyboardWidth, keyboardWidth, 0f)
                * settings.current.mSidePaddingScale).toInt()
        keyboardAttr.recycle()

        clipboardRecyclerView.apply {
            adapter = clipboardAdapter
            layoutParams.width = keyboardWidth
            setPadding(leftPadding, paddingTop, rightPadding, paddingBottom)
        }
        
        // absurd workaround so Android sets the correct color from stateList (depending on "activated")
        toolbarKeys.forEach { it.isEnabled = false; it.isEnabled = true }
    }

    override fun onKeyDown(clipId: Long) {
        keyboardActionListener.onPressKey(KeyCode.NOT_SPECIFIED, 0, true, HapticEvent.KEY_PRESS)
    }

    override fun onKeyUp(clipId: Long) {
        val clipContent = clipboardHistoryManager.getHistoryEntryContent(clipId) ?: return
        if (clipContent.imageUri != null) {
            keyboardActionListener.onImageSelected(clipContent.imageUri)
        } else {
            keyboardActionListener.onTextInput(clipContent.text)
        }
        keyboardActionListener.onReleaseKey(KeyCode.NOT_SPECIFIED, false)
        if (Settings.getValues().mAlphaAfterClipHistoryEntry)
            keyboardActionListener.onCodeInput(KeyCode.ALPHA, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
    }
 


    fun stopClipboardHistory() {
        if (!this::clipboardAdapter.isInitialized) return

        // Stop edit mode if active
        if (inEditMode && isAttachedToWindow) {
            stopEditMode(save = false)
        }
        
        // Also ensure search mode is stopped if we explicitly leave clipboard history
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inSearchMode && isAttachedToWindow) {
            stopSearchMode()
        }

        if (inSelectionMode && isAttachedToWindow) {
            stopSelectionMode()
        }

        // Dismiss any active undo bar
        clipboardRecyclerView.dismissUndoBar()
        dismissConfirmationBar()
        
        clipboardRecyclerView.adapter = null
        clipboardHistoryManager.setHistoryChangeListener(null)
        clipboardAdapter.clipboardHistoryManager = null

        PointerTracker.setClipboardInlineInputActive(false)
    }

    fun showClearAllConfirmationBar() {
        clipboardRecyclerView.dismissUndoBar()
        val bar = confirmationBar ?: return
        confirmationHandler.removeCallbacks(confirmationDismissRunnable)
        bar.visibility = View.VISIBLE
        confirmationHandler.postDelayed(confirmationDismissRunnable, 5000)
    }

    fun dismissConfirmationBar() {
        confirmationHandler.removeCallbacks(confirmationDismissRunnable)
        confirmationBar?.visibility = View.GONE
    }

    override fun onClick(view: View) {
        val tag = view.tag
        if (tag is ToolbarKey) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_PRESS)
            val code = getCodeForToolbarKey(tag)
            if (code == KeyCode.CLIPBOARD_SEARCH) {
                 startSearchMode()
                 return
            }
            if (code == KeyCode.CLIPBOARD_CLEAR_HISTORY) {
                showClearAllConfirmationBar()
                return
            }
            if (code != KeyCode.UNSPECIFIED) {
                keyboardActionListener.onCodeInput(code, Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, false)
                return
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        val tag = view.tag
        if (tag is ToolbarKey) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this, HapticEvent.KEY_LONG_PRESS)
            val code = getCodeForToolbarKey(tag)
            if (code == KeyCode.CLIPBOARD_CLEAR_HISTORY) {
                dismissConfirmationBar()
                clipboardHistoryManager.clearHistory()
                return true
            }
            val longClickCode = getCodeForToolbarKeyLongClick(tag)
            if (longClickCode != KeyCode.UNSPECIFIED) {
                keyboardActionListener.onCodeInput(
                    longClickCode,
                    Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE,
                    false
                )
            }
            return true
        }
        return false
    }



    override fun onClipInserted(position: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
            if (!clipboardAdapter.isFiltering) {
                 clipboardRecyclerView.smoothScrollToPosition(0)
            }
            updateEmptyView(clipboardAdapter.isFiltering)
        }
    }

    override fun onClipsRemoved(position: Int, count: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
            updateEmptyView(clipboardAdapter.isFiltering)
        }
    }

    override fun onClipMoved(oldPosition: Int, newPosition: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
        }
    }

    override fun onClipChanged(position: Int) {
        confirmationHandler.post {
            clipboardAdapter.refresh()
        }
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        setToolbarButtonsActivatedStateOnPrefChange(KeyboardSwitcher.getInstance().clipboardStrip ?: return, key)

        if (key == Settings.PREF_AUTO_SPAN_TOOLBAR_KEYS || key == Settings.PREF_TOOLBAR_KEYS_ALIGNMENT || key == Settings.PREF_CLIPBOARD_KEYS_ALIGNMENT || key == Settings.PREF_CLIPBOARD_TOOLBAR_KEYS) {
            applyClipboardToolbarKeyLayoutParams()
            KeyboardSwitcher.getInstance().clipboardStrip?.post { applyClipboardToolbarKeyLayoutParams() }
        }

        if (::clipboardHistoryManager.isInitialized && 
            (key == Settings.PREF_CLIPBOARD_HISTORY_PINNED_FIRST || key == Settings.PREF_CLIPBOARD_FOLD_PINNED)) {
            // Ensure settings are reloaded first
            Settings.getInstance().onSharedPreferenceChanged(prefs, key)
            clipboardHistoryManager.sortHistoryEntries()
            confirmationHandler.post {
                clipboardAdapter.refresh()
            }
        }
    }

    private fun applyClipboardToolbarKeyLayoutParams() {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip ?: return
        val count = clipboardStrip.childCount
        if (count == 0) return

        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        if (inEditMode || inSearchMode || inSelectionMode) return

        val singleKeyWidth = kotlin.math.min(
            context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
            context.resources.getDimensionPixelSize(R.dimen.config_suggestions_strip_height)
        )

        val visibleCount = (0 until count).count {
            val child = clipboardStrip.getChildAt(it)
            child != null && child.visibility != View.GONE
        }
        if (visibleCount == 0) return

        val keyboardWidth = ResourceUtils.getKeyboardWidth(context, Settings.getValues())
        val parentView = (clipboardStrip.parent as? View)
        val containerWidth = parentView?.width?.takeIf { it > 0 }
            ?: parentView?.measuredWidth?.takeIf { it > 0 }
            ?: keyboardWidth

        val isAutoSpan = Settings.getValues().mAutoSpanToolbarKeys
        val minSpannedKeyWidth = (singleKeyWidth * 1.25f).toInt()
        val canSpan = containerWidth > 0 && (containerWidth / visibleCount >= minSpannedKeyWidth)
        val useEqualSpacing = isAutoSpan && canSpan

        val alignmentGravity = when (Settings.getValues().mToolbarKeysAlignment) {
            "left" -> Gravity.START or Gravity.CENTER_VERTICAL
            "center" -> Gravity.CENTER
            else -> Gravity.END or Gravity.CENTER_VERTICAL
        }
        clipboardStrip.gravity = if (useEqualSpacing) Gravity.NO_GRAVITY else alignmentGravity

        val toolbarKeyLayoutParams = LinearLayout.LayoutParams(
            singleKeyWidth,
            singleKeyWidth
        ).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        val spannedLayoutParams = LinearLayout.LayoutParams(0, singleKeyWidth, 1f).apply {
            gravity = Gravity.CENTER_VERTICAL
        }

        for (i in 0 until count) {
            val child = clipboardStrip.getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            child.layoutParams = if (useEqualSpacing) {
                spannedLayoutParams
            } else {
                toolbarKeyLayoutParams
            }
        }
    }

    private fun updateClipboardGestureSuppression() {
        val clipboardStrip = KeyboardSwitcher.getInstance().clipboardStrip
        val inSearchMode = this::searchBarTextView.isInitialized && searchBarTextView.parent == clipboardStrip
        val active = inEditMode || inSearchMode
        PointerTracker.setClipboardInlineInputActive(active)
    }

    override fun onDetachedFromWindow() {
        dismissActionPill()
        PointerTracker.setClipboardInlineInputActive(false)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!isShown) {
            dismissActionPill()
            PointerTracker.setClipboardInlineInputActive(false)
        } else {
            updateClipboardGestureSuppression()
        }
    }
}
