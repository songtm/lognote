package com.blogspot.cdcsutils.lognote

import com.blogspot.cdcsutils.lognote.FormatManager.Companion.SEPARATOR_DELIMITER
import java.awt.Color
import java.io.*
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel


class LogTableModelEvent(source:LogTableModel, change:Int, removedCount:Int) {
    val mSource = source
    val mDataChange = change
//    val mFlags = flag
    val mRemovedCount = removedCount
    companion object {
        const val EVENT_ADDED = 0
        const val EVENT_REMOVED = 1
        const val EVENT_FILTERED = 2
        const val EVENT_CHANGED = 3
        const val EVENT_CLEARED = 4

        const val FLAG_FIRST_REMOVED = 1
    }
}

interface LogTableModelListener {
    fun tableChanged(event:LogTableModelEvent?)
}

open class LogTableModel(mainUI: MainUI, baseModel: LogTableModel?) : AbstractTableModel() {
    companion object {
        var IsColorTagRegex = false
        var WaitTimeForDoubleClick = System.currentTimeMillis()
        internal const val COLUMN_NUM = 0
        internal const val COLUMN_PROCESS_NAME = 1
        internal const val COLUMN_LOG_START = 2
        private const val COLUMN_COUNT = 3
        const val LEVEL_NONE = FormatManager.LEVEL_NONE
        const val LEVEL_VERBOSE = FormatManager.LEVEL_VERBOSE
        const val LEVEL_DEBUG = FormatManager.LEVEL_DEBUG
        const val LEVEL_INFO = FormatManager.LEVEL_INFO
        const val LEVEL_WARNING = FormatManager.LEVEL_WARNING
        const val LEVEL_ERROR = FormatManager.LEVEL_ERROR
        const val LEVEL_FATAL = FormatManager.LEVEL_FATAL

        const val SHOW_PROCESS_NONE = 0
        const val SHOW_PROCESS_SHOW = 1
        const val SHOW_PROCESS_SHOW_WITH_BGCOLOR = 2

        var TypeShowProcessName = SHOW_PROCESS_SHOW_WITH_BGCOLOR
    }

    private val mAgingTestManager = AgingTestManager.getInstance()

    data class FilteredColor(val mColor: String, val mPattern: Pattern?)

    inner class FilterTokenManager() {
        fun set(idx: Int, value: String) {
            if (mFilterTokens[idx] != value) {
                mIsFilterUpdated = true
                mFilterTokens[idx] = value
            }
            mMainUI.mTokenCombo[idx].mErrorMsg = ""
            val patterns = parsePattern(value, false)
            mFilterShowTokens[idx] = patterns[0]
            mFilterHideTokens[idx] = patterns[1]
            mPatternShowTokens[idx] = compilePattern(mFilterShowTokens[idx], mPatternCase, mPatternShowTokens[idx], mMainUI.mTokenCombo[idx])
            mBaseModel?.mPatternShowTokens?.set(idx, mPatternShowTokens[idx])
            mPatternHideTokens[idx] = compilePattern(mFilterHideTokens[idx], mPatternCase, mPatternHideTokens[idx], mMainUI.mTokenCombo[idx])
        }
    }

    private var mPatternFindLog: Pattern = Pattern.compile("", Pattern.CASE_INSENSITIVE)
    private var mMatcherFindLog: Matcher = mPatternFindLog.matcher("")
    private var mNormalFindLogSplit: List<String>? = null

    private var mTableColor: ColorManager.TableColor
    private val mColumnNames = arrayOf("Line", "Process", "Log")
    var mLogItems:MutableList<LogItem> = mutableListOf()
    private var mBaseModel:LogTableModel? = baseModel
    var mLogFile:File? = null
    private val mLogCmdManager = LogCmdManager.getInstance()
    private val mBookmarkManager = BookmarkManager.getInstance()
    private val mFormatManager = FormatManager.getInstance()
    protected var mTokenFilters = mFormatManager.mCurrFormat.mTokenFilters
    protected var mSortedTokenFilters = mFormatManager.mCurrFormat.mSortedTokenFilters
        set(value) {
            if (!field.contentEquals(value)) {
                field = value
            }

            mSortedTokensIdxs = mFormatManager.mCurrFormat.mSortedTokensIdxs

            mTokenNthMax = mLevelIdx
            for (token in value) {
                if (token.mPosition > mTokenNthMax) {
                    mTokenNthMax = token.mPosition
                }
            }
            mEmptyTokenFilters = Array(value.size) { "" }
        }

    private var mSortedTokensIdxs = mFormatManager.mCurrFormat.mSortedTokensIdxs
    private var mTokenNthMax = 0
    protected var mEmptyTokenFilters = arrayOf("")
    protected var mLevelIdx = mFormatManager.mCurrFormat.mLevelPosition
    protected var mSeparatorList: List<String>? = null
    protected var mSeparator = mFormatManager.mCurrFormat.mSeparator
    protected var mTokenCount = mFormatManager.mCurrFormat.mTokenCount

    private val mEventListeners = ArrayList<LogTableModelListener>()
    protected val mFilteredFGMap = mutableMapOf<String, FilteredColor>()
    protected val mFilteredBGMap = mutableMapOf<String, FilteredColor>()

    private var mIsFilterUpdated = true

    var mSelectionChanged = false

    private val mMainUI = mainUI

    var mFilterLevel = LEVEL_VERBOSE
        set(value) {
            if (field != value) {
                mIsFilterUpdated = true
            }
            field = if (mSeparator.isEmpty()) {
                LEVEL_NONE
            } else {
                value
            }
        }

    var mFilterLog: String = ""
        set(value) {
            if (field != value) {
                mIsFilterUpdated = true
                field = value
            }
            mMainUI.mShowLogCombo.mErrorMsg = ""
            val patterns = parsePattern(value, true)
            mFilterShowLog = patterns[0]
            mFilterHideLog = patterns[1]

            if (mBaseModel != null) {
                mBaseModel!!.mFilterLog = value
            }
        }

    private var mFilterShowLog: String = ""
        set(value) {
            field = value
            mPatternShowLog = compilePattern(value, mPatternCase, mPatternShowLog, mMainUI.mShowLogCombo)
        }

    private var mFilterHideLog: String = ""
        set(value) {
            field = value
            mPatternHideLog = compilePattern(value, mPatternCase, mPatternHideLog, mMainUI.mShowLogCombo)
        }

    var mFilterHighlightLog: String = ""
        set(value) {
            val patterns = parsePattern(value, false)
            if (field != patterns[0]) {
                mIsFilterUpdated = true
                field = patterns[0]
            }
        }

    private fun updateFilterFindLog(field: String) {
        var normalFindLog = ""
        val findLogSplit = field.split("|")
        mRegexFindLog = ""

        for (logUnit in findLogSplit) {
            val hasIt: Boolean = logUnit.chars().anyMatch { c -> "\\.[]{}()*+?^$|".indexOf(c.toChar()) >= 0 }
            if (hasIt) {
                if (mRegexFindLog.isEmpty()) {
                    mRegexFindLog = logUnit
                }
                else {
                    mRegexFindLog += "|$logUnit"
                }
            }
            else {
                if (normalFindLog.isEmpty()) {
                    normalFindLog = logUnit
                }
                else {
                    normalFindLog += "|$logUnit"
                }

                if (mFindPatternCase == Pattern.CASE_INSENSITIVE) {
                    normalFindLog = normalFindLog.uppercase()
                }
            }
        }

        mMainUI.mFindPanel.mFindCombo.mErrorMsg = ""
        mPatternFindLog = compilePattern(mRegexFindLog, mFindPatternCase, mPatternFindLog, mMainUI.mFindPanel.mFindCombo)
        mMatcherFindLog = mPatternFindLog.matcher("")

        mNormalFindLogSplit = normalFindLog.split("|")
    }

    var mFilterFindLog: String = ""
        set(value) {
            val patterns = parsePattern(value, false)
            if (field != patterns[0]) {
                mIsFilterUpdated = true
                field = patterns[0]

                updateFilterFindLog(field)
            }

            if (mBaseModel != null) {
                mBaseModel!!.mFilterFindLog = value
            }
        }

    private var mPatternTriggerLog: Pattern = Pattern.compile("", Pattern.CASE_INSENSITIVE)
    private var mTriggerPatternCase = Pattern.CASE_INSENSITIVE
    var mFilterTriggerLog: String = ""
        set(value) {
            field = value
            mPatternTriggerLog = compilePattern(value, mTriggerPatternCase, mPatternTriggerLog, null)
        }

    var mFilterTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { "" }
    var mFilterShowTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { "" }
    var mFilterHideTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { "" }
    var mPatternShowTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { Pattern.compile("", Pattern.CASE_INSENSITIVE) }
    var mPatternHideTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { Pattern.compile("", Pattern.CASE_INSENSITIVE) }
    var mBoldTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { false }
    var mBoldTokenEndIdx = -1
        set(value) {
            field = -1
            for(idx in 0 until FormatManager.MAX_TOKEN_FILTER_COUNT) {
                if (mBoldTokens[idx]) {
                    field = idx
                }
            }
        }
    internal var mSortedPidTokIdx = mFormatManager.mCurrFormat.mSortedPidTokIdx
    val mFilterTokenMgr = FilterTokenManager()

    private var mPatternCase = Pattern.CASE_INSENSITIVE
    var mMatchCase: Boolean = false
        set(value) {
            if (field != value) {
                mPatternCase = if (!value) {
                    Pattern.CASE_INSENSITIVE
                } else {
                    0
                }

                mMainUI.mShowLogCombo.mErrorMsg = ""
                mPatternShowLog = compilePattern(mFilterShowLog, mPatternCase, mPatternShowLog, mMainUI.mShowLogCombo)
                mPatternHideLog = compilePattern(mFilterHideLog, mPatternCase, mPatternHideLog, mMainUI.mShowLogCombo)
                for (idx in 0 until FormatManager.MAX_TOKEN_FILTER_COUNT) {
                    mMainUI.mTokenCombo[idx].mErrorMsg = ""
                    mPatternShowTokens[idx] = compilePattern(mFilterShowTokens[idx], mPatternCase, mPatternShowTokens[idx], mMainUI.mTokenCombo[idx])
                    mBaseModel?.mPatternShowTokens?.set(idx, mPatternShowTokens[idx])
                    mPatternHideTokens[idx] = compilePattern(mFilterHideTokens[idx], mPatternCase, mPatternHideTokens[idx], mMainUI.mTokenCombo[idx])
                }

                mIsFilterUpdated = true

                field = value
            }
        }

    private var mRegexFindLog = ""
    private var mFindPatternCase = Pattern.CASE_INSENSITIVE
    var mFindMatchCase: Boolean = false
        set(value) {
            if (field != value) {
                mFindPatternCase = if (!value) {
                    Pattern.CASE_INSENSITIVE
                } else {
                    0
                }

                mIsFilterUpdated = true

                field = value

                updateFilterFindLog(mFilterFindLog)

                if (mBaseModel != null) {
                    mBaseModel!!.mFindMatchCase = value
                }
            }
        }

    var mGoToLast = true
//        set(value) {
//            field = value
//            Exception().printStackTrace()
//            Utils.printlnLog("tid = " + Thread.currentThread().id)
//        }
    var mBookmarkMode = false
        set(value) {
            field = value
            if (value) {
                mFullMode = false
            }
            mIsFilterUpdated = true
        }

    var mFullMode = false
        set(value) {
            field = value
            if (value) {
                mBookmarkMode = false
            }
            mIsFilterUpdated = true
        }

    var mScrollback = 0
        set(value) {
            field = value
            if (SwingUtilities.isEventDispatchThread()) {
                mLogItems.clear()
                mLogItems = mutableListOf()
                mBaseModel!!.mLogItems.clear()
                mBaseModel!!.mLogItems = mutableListOf()
                mBaseModel!!.mBookmarkManager.clear()
                fireLogTableDataCleared()
                mBaseModel!!.fireLogTableDataCleared()
            } else {
                SwingUtilities.invokeAndWait {
                    mLogItems.clear()
                    mLogItems = mutableListOf()
                    mBaseModel!!.mLogItems.clear()
                    mBaseModel!!.mLogItems = mutableListOf()
                    mBaseModel!!.mBookmarkManager.clear()
                    fireLogTableDataCleared()
                    mBaseModel!!.fireLogTableDataCleared()
                }
            }
        }

    var mScrollbackSplitFile = false

    var mScrollbackKeep = false

    private var mPatternShowLog: Pattern = Pattern.compile("", Pattern.CASE_INSENSITIVE)
    private var mPatternHideLog: Pattern = Pattern.compile("", Pattern.CASE_INSENSITIVE)

    protected var mLevelMap: Map<String, Int>
    init {
        mLevelMap = mFormatManager.mCurrFormat.mLevels
        mTokenNthMax = mLevelIdx
        for (token in mSortedTokenFilters) {
            if (token.mPosition > mTokenNthMax) {
                mTokenNthMax = token.mPosition
            }
        }
        mEmptyTokenFilters = Array(mSortedTokenFilters.size) { "" }

        if (mSeparator.isEmpty()) {
            mFilterLevel = LEVEL_NONE
        }
        mSeparatorList = if (mSeparator.contains(SEPARATOR_DELIMITER)) {
            mSeparator.split(SEPARATOR_DELIMITER)
        }
        else {
            null
        }

        mBaseModel = baseModel
        loadItems(false)

        mTableColor = if (isFullDataModel()) {
            ColorManager.getInstance().mFullTableColor
        }
        else {
            ColorManager.getInstance().mFilterTableColor
        }

        val colorEventListener = object: ColorManager.ColorEventListener{
            override fun colorChanged(event: ColorManager.ColorEvent?) {
                parsePattern(mFilterLog, true) // update color
                mIsFilterUpdated = true
            }
        }

        ColorManager.getInstance().addColorEventListener(colorEventListener)
    }

//    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
//        return true
//    }

    fun isFullDataModel(): Boolean {
        if (mBaseModel == null) {
            return true
        }

        return false
    }

    private fun parsePattern(pattern: String, isUpdateColor: Boolean) : Array<String> {
        val patterns: Array<String> = Array(2) { "" }

        val strs = pattern.split("|")
        var prevPatternIdx = -1
        if (isUpdateColor) {
            mFilteredFGMap.clear()
            mFilteredBGMap.clear()
        }

        for (item in strs) {
            if (prevPatternIdx != -1) {
                patterns[prevPatternIdx] += "|"
                patterns[prevPatternIdx] += item
                if (item.substring(item.length - 1) != "\\") {
                    prevPatternIdx = -1
                }
                continue
            }

            if (item.isNotEmpty()) {
                if (item[0] != '-') {
                    if (patterns[0].isNotEmpty()) {
                        patterns[0] += "|"
                    }

                    if (2 < item.length && item[0] == '#' && item[1].isDigit()) {
                        val key = item.substring(2)
                        patterns[0] += key
                        if (isUpdateColor) {
                            var patt: Pattern? = null
                            val hasIt: Boolean = key.uppercase().chars().anyMatch { c -> "\\.[]{}()*+?^$|".indexOf(c.toChar()) >= 0 }
                            if (hasIt) {
                                patt = Pattern.compile(key.uppercase(), Pattern.CASE_INSENSITIVE)
                            }

                            mFilteredFGMap[key.uppercase()] = FilteredColor(mTableColor.mStrFilteredFGs[item[1].digitToInt()], patt)
                            mFilteredBGMap[key.uppercase()] = FilteredColor(mTableColor.mStrFilteredBGs[item[1].digitToInt()], patt)
                        }
                    }
                    else {
                        patterns[0] += item
                    }

                    if (item.substring(item.length - 1) == "\\") {
                        prevPatternIdx = 0
                    }
                } else {
                    if (patterns[1].isNotEmpty()) {
                        patterns[1] += "|"
                    }

                    if (3 < item.length && item[1] == '#' && item[2].isDigit()) {
                        patterns[1] += item.substring(3)
                    }
                    else {
                        patterns[1] += item.substring(1)
                    }

                    if (item.substring(item.length - 1) == "\\") {
                        prevPatternIdx = 1
                    }
                }
            }
        }

        return patterns
    }

    private fun compilePattern(regex: String, flags: Int, prevPattern: Pattern, comboBox: FilterComboBox?): Pattern {
        var pattern = prevPattern
        try {
            pattern = Pattern.compile(regex, flags)
        } catch(ex: java.util.regex.PatternSyntaxException) {
            ex.printStackTrace()
            comboBox?.mErrorMsg = ex.message.toString()
        }

        return pattern
    }

    private var mFilteredItemsThread:Thread? = null
    fun loadItems(isAppend: Boolean) {
        if (mBaseModel == null) {
            if (SwingUtilities.isEventDispatchThread()) {
                loadFile(isAppend)
            } else {
                SwingUtilities.invokeAndWait {
                    loadFile(isAppend)
                }
            }
        }
        else {
            mIsFilterUpdated = true

            if (mFilteredItemsThread == null) {
                mFilteredItemsThread = Thread {
                    run {
                        while (true) {
                            try {
                                if (mIsFilterUpdated) {
                                    mMainUI.markLine()
                                    makeFilteredItems(true)
                                }
                                Thread.sleep(100)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                mFilteredItemsThread?.start()
            }
        }
    }

    fun clearItems() {
        Utils.printlnLog("isEventDispatchThread = ${SwingUtilities.isEventDispatchThread()}")

        if (mBaseModel != null) {
            mBaseModel!!.mGoToLast = true
            mGoToLast = true
            mBaseModel!!.mLogItems.clear()
            mBaseModel!!.mLogItems = mutableListOf()
            mBaseModel!!.mBookmarkManager.clear()
            mLogItems.clear()
            mLogItems = mutableListOf()

            fireLogTableDataCleared()
            mBaseModel!!.fireLogTableDataCleared()

            mIsFilterUpdated = true
            System.gc()
        }
    }

    fun setLogFile(path: String) {
        mLogFile = File(path)
    }

    private fun loadFile(isAppend: Boolean) {
        if (mLogFile == null) {
            return
        }

        var num = 0
        if (isAppend) {
            if (mLogItems.size > 0) {
                val item = mLogItems.last()
                num = item.mNum.toInt()
                num++
                mLogItems.add(LogItem(num.toString(), "LogNote - APPEND LOG : $mLogFile", LEVEL_ERROR, mEmptyTokenFilters, null, null))
                num++
            }
        } else {
            mLogItems.clear()
            mLogItems = mutableListOf()
            mBookmarkManager.clear()
        }

        val bufferedReader = BufferedReader(FileReader(mLogFile!!))
        var line: String?

        line = bufferedReader.readLine()
        while (line != null) {
            mLogItems.add(makeLogItem(num, line))
            num++
            line = bufferedReader.readLine()
        }

        fireLogTableDataChanged()
    }

    private fun fireLogTableDataChanged(flags: Int) {
        fireLogTableDataChanged(LogTableModelEvent(this, LogTableModelEvent.EVENT_CHANGED, flags))
    }

    private fun fireLogTableDataChanged() {
        fireLogTableDataChanged(LogTableModelEvent(this, LogTableModelEvent.EVENT_CHANGED, 0))
    }

    private fun fireLogTableDataFiltered() {
        fireLogTableDataChanged(LogTableModelEvent(this, LogTableModelEvent.EVENT_FILTERED, 0))
    }

    private fun fireLogTableDataCleared() {
        fireLogTableDataChanged(LogTableModelEvent(this, LogTableModelEvent.EVENT_CLEARED, 0))
    }

    private fun fireLogTableDataChanged(event:LogTableModelEvent) {
        for (listener in mEventListeners) {
            listener.tableChanged(event)
        }
    }

    fun addLogTableModelListener(eventListener:LogTableModelListener) {
        mEventListeners.add(eventListener)
    }

    override fun getRowCount(): Int {
        return mLogItems.size
    }

    override fun getColumnCount(): Int {
        return COLUMN_COUNT
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        try {
            if (rowIndex >= 0 && mLogItems.size > rowIndex) {
                val logItem = mLogItems[rowIndex]
                when (columnIndex) {
                    COLUMN_NUM -> {
                        return logItem.mNum + " "
                    }
                    COLUMN_PROCESS_NAME -> {
                        if (TypeShowProcessName != SHOW_PROCESS_NONE) {
                            if (logItem.mProcessName == null) {
                                if ((mSortedPidTokIdx >= 0) && (logItem.mTokenFilterLogs.size > mSortedPidTokIdx)) {
                                    return if (logItem.mTokenFilterLogs[mSortedPidTokIdx] == "0") {
                                        "0"
                                    } else {
                                        ProcessList.getInstance()
                                            .getProcessName(logItem.mTokenFilterLogs[mSortedPidTokIdx]) ?: ""
                                    }
                                }
                            } else {
                                return logItem.mProcessName
                            }
                        }
                        else {
                            return ""
                        }

                    }
                    COLUMN_LOG_START -> {
                        return logItem.mLogLine
                    }
                }
            }
        } catch (e:ArrayIndexOutOfBoundsException) {
            e.printStackTrace()
        }

        return -1
    }

    override fun getColumnName(column: Int): String {
        return mColumnNames[column]
    }

    fun getFgColor(row: Int) : Color {
        return when (mLogItems[row].mLevel) {
            LEVEL_VERBOSE -> {
                mTableColor.mLogLevelVerbose
            }
            LEVEL_DEBUG -> {
                mTableColor.mLogLevelDebug
            }
            LEVEL_INFO -> {
                mTableColor.mLogLevelInfo
            }
            LEVEL_WARNING -> {
                mTableColor.mLogLevelWarning
            }
            LEVEL_ERROR -> {
                mTableColor.mLogLevelError
            }
            LEVEL_FATAL -> {
                mTableColor.mLogLevelFatal
            }
            else -> {
                mTableColor.mLogLevelNone
            }
        }
    }

    fun getFgStrColor(row: Int) : String {
        return when (mLogItems[row].mLevel) {
            LEVEL_VERBOSE -> {
                mTableColor.mStrLogLevelVerbose
            }
            LEVEL_DEBUG -> {
                mTableColor.mStrLogLevelDebug
            }
            LEVEL_INFO -> {
                mTableColor.mStrLogLevelInfo
            }
            LEVEL_WARNING -> {
                mTableColor.mStrLogLevelWarning
            }
            LEVEL_ERROR -> {
                mTableColor.mStrLogLevelError
            }
            LEVEL_FATAL -> {
                mTableColor.mStrLogLevelFatal
            }
            else -> mTableColor.mStrLogLevelNone
        }
    }

    private var mPatternPrintFind:Pattern? = null
    private var mPatternPrintHighlight:Pattern? = null
    protected var mPatternPrintFilter:Pattern? = null

    open fun getPatternPrintFilter(col: Int): Pattern? {
        if (col >= COLUMN_LOG_START) {
            return mPatternPrintFilter
        }
        return null
    }

    fun getPrintValue(value: String, row: Int, col: Int, isSelected: Boolean, htmlTag: Boolean) : String {
        var newValue = value
        if (newValue.indexOf("<") >= 0) {
            newValue = newValue.replace("<", "&lt;")
        }
        if (newValue.indexOf(">") >= 0) {
            newValue = newValue.replace(">", "&gt;")
        }

        val stringBuilder = StringBuilder(newValue)

        val findStarts: Queue<Int> = LinkedList()
        val findEnds: Queue<Int> = LinkedList()
        if (mPatternPrintFind != null) {
            val matcher = mPatternPrintFind!!.matcher(stringBuilder.toString())
            while (matcher.find()) {
                findStarts.add(matcher.start(0))
                findEnds.add(matcher.end(0))
            }
        }
        
        val highlightStarts: Queue<Int> = LinkedList()
        val highlightEnds: Queue<Int> = LinkedList()
        if (mPatternPrintHighlight != null) {
            val matcher = mPatternPrintHighlight!!.matcher(stringBuilder.toString())
            while (matcher.find()) {
                highlightStarts.add(matcher.start(0))
                highlightEnds.add(matcher.end(0))
            }
        }

        val filterStarts: Queue<Int> = LinkedList()
        val filterEnds: Queue<Int> = LinkedList()
        val patternPrintFilter = getPatternPrintFilter(col)
        if (patternPrintFilter != null) {
            val matcher = patternPrintFilter.matcher(stringBuilder.toString())
            while (matcher.find()) {
                filterStarts.add(matcher.start(0))
                filterEnds.add(matcher.end(0))
            }
        }

        val tokenStarts: Queue<Int> = LinkedList()
        val tokenEnds: Queue<Int> = LinkedList()
        val boldStartTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { -1 }
        val boldEndTokens = Array(FormatManager.MAX_TOKEN_FILTER_COUNT) { -1 }

        if (mBoldTokenEndIdx >= 0) {
            val textSplited = FormatManager.splitLog(stringBuilder.toString(), mTokenCount, mSeparator, mSeparatorList)
            var currPos = 0
            var tokenIdx = 0;
            if (textSplited.size > mTokenNthMax) {
                for ((splitIdx, item) in textSplited.withIndex()) {
                    while (tokenIdx <= mBoldTokenEndIdx) {
                        if (splitIdx < mSortedTokenFilters[tokenIdx].mPosition) {
                            break
                        }
                        else if (splitIdx == mSortedTokenFilters[tokenIdx].mPosition) {
                            if (mBoldTokens[tokenIdx]) {
                                boldStartTokens[tokenIdx] = newValue.indexOf(item, currPos)
                                boldEndTokens[tokenIdx] = boldStartTokens[tokenIdx] + item.length
                                tokenStarts.add(boldStartTokens[tokenIdx])
                                tokenEnds.add(boldEndTokens[tokenIdx])
                            }
                        }
                        tokenIdx++
                    }
                    currPos = newValue.indexOf(item, currPos) + item.length + 1
                }
            }
        }

        val starts = Stack<Int>()
        val ends = Stack<Int>()
        val fgColors = Stack<String>()
        val bgColors = Stack<String>()

        val STEP_SIZE = 4
        val STEP_FIND = 0
        val STEP_HIGHLIGHT = 1
        val STEP_FILTER = 2
        val STEP_TOKEN = 3
        val stepStarts: Array<Int> = Array(STEP_SIZE) { -1 }
        val stepEnds: Array<Int> = Array(STEP_SIZE) { -1 }
        val posStarts: Array<Queue<Int>> = arrayOf(findStarts, highlightStarts, filterStarts, tokenStarts)
        val posEnds: Array<Queue<Int>> = arrayOf(findEnds, highlightEnds, filterEnds, tokenEnds)

        var idx = 0
        var prevIdx = 0
        var keyFilterColor = ""
        while (idx < newValue.length) {
            prevIdx = idx
            for (currStep in STEP_FIND until STEP_SIZE) {
                while (stepEnds[currStep] <= idx) {
                    if (posStarts[currStep].size > 0) {
                        stepStarts[currStep] = posStarts[currStep].poll()
                        stepEnds[currStep] = posEnds[currStep].poll()
                        if (currStep == STEP_FILTER) {
                            keyFilterColor = newValue.substring(stepStarts[currStep], stepEnds[currStep]).uppercase()
                        }

                        if (idx in (stepStarts[currStep] + 1) until stepEnds[currStep]) {
                            stepStarts[currStep] = idx
                        }
                    } else {
                        stepStarts[currStep] = -1
                        stepEnds[currStep] = -1
                        break
                    }
                }
            }

            for (currStep in STEP_FIND until STEP_SIZE) {
                if (idx == stepStarts[currStep]) {
                    for (step in currStep + 1 until STEP_SIZE) {
                        if (stepEnds[currStep] in (stepStarts[step] + 1) until stepEnds[step]) {
                            stepStarts[step] = stepEnds[currStep]
                        }
                    }

                    var nextS = -1
                    var nextE = -1
                    for (step in STEP_FIND until currStep) {
                        if (stepStarts[step] in stepStarts[currStep] until stepEnds[currStep]) {
                            if (stepEnds[currStep] > stepEnds[step]) {
                                nextS = stepEnds[step]
                                nextE = stepEnds[currStep]
                            }
                            stepEnds[currStep] = stepStarts[step]
                        }
                    }

                    starts.push(stepStarts[currStep])
                    ends.push(stepEnds[currStep])

                    idx = stepEnds[currStep]

                    if (stepStarts[currStep] < nextS) {
                        stepStarts[currStep] = nextS
                    }
                    if (stepEnds[currStep] < nextE) {
                        stepEnds[currStep] = nextE
                    }

                    when (currStep) {
                        STEP_FIND -> {
                            fgColors.push(mTableColor.mStrFindFG)
                            bgColors.push(mTableColor.mStrFindBG)
                        }
                        STEP_HIGHLIGHT -> {
                            fgColors.push(mTableColor.mStrHighlightFG)
                            bgColors.push(mTableColor.mStrHighlightBG)
                        }
                        STEP_FILTER -> {
                            if (mFilteredFGMap[keyFilterColor] != null) {
                                fgColors.push(mFilteredFGMap[keyFilterColor]!!.mColor)
                                bgColors.push(mFilteredBGMap[keyFilterColor]!!.mColor)
                            } else if (IsColorTagRegex) {
                                var isFind = false
                                for (item in mFilteredFGMap.keys) {
                                    val pattern = mFilteredFGMap[item]?.mPattern
                                    if ((pattern != null) && pattern.matcher(keyFilterColor).find()) {
                                        fgColors.push(mFilteredFGMap[item]!!.mColor)
                                        bgColors.push(mFilteredBGMap[item]!!.mColor)
                                        isFind = true
                                        break
                                    }
                                }
                                if (!isFind) {
                                    fgColors.push(mTableColor.mStrFilteredFGs[0])
                                    bgColors.push(mTableColor.mStrFilteredBGs[0])
                                }
                            } else {
                                fgColors.push(mTableColor.mStrFilteredFGs[0])
                                bgColors.push(mTableColor.mStrFilteredBGs[0])
                            }
                        }
                        STEP_TOKEN -> {
                            when (stepStarts[currStep]) {
                                in boldStartTokens[0] until boldEndTokens[0] -> {
                                    fgColors.push(mTableColor.mStrToken0FG)
                                    bgColors.push(mTableColor.mStrLogBG)
                                }

                                in boldStartTokens[1] until boldEndTokens[1] -> {
                                    fgColors.push(mTableColor.mStrToken1FG)
                                    bgColors.push(mTableColor.mStrLogBG)
                                }

                                in boldStartTokens[2] until boldEndTokens[2] -> {
                                    fgColors.push(mTableColor.mStrToken2FG)
                                    bgColors.push(mTableColor.mStrLogBG)
                                }
                            }
                        }
                        else -> {
                            Utils.printlnLog("invalid step $currStep")
                        }
                    }
                    break
                }
            }
            if (prevIdx == idx) {
                idx++
            }
        }

        if (starts.size == 0) {
            if (newValue == value) {
                return ""
            }
            stringBuilder.replace(0, newValue.length, newValue.replace("  ", " &nbsp;"))
        }
        else {
            var beforeStart = 0
            var isFirst = true
            while (!starts.isEmpty()) {
                val start = starts.pop()
                val end = ends.pop()

                val fgColor = fgColors.pop()
                var bgColor = bgColors.pop()

                if (isFirst) {
                    if (end < newValue.length) {
                        stringBuilder.replace(
                                end,
                                newValue.length,
                                newValue.substring(end, newValue.length).replace("  ", " &nbsp;")
                        )
                    }
                    isFirst = false
                }
                if (beforeStart > end) {
                    stringBuilder.replace(
                            end,
                            beforeStart,
                            newValue.substring(end, beforeStart).replace("  ", " &nbsp;")
                    )
                }
                if (start >= 0 && end >= 0) {
                    if (isSelected) {
                        val tmpColor = Color.decode(bgColor)
                        Color(tmpColor.red / 2 + mTableColor.mSelectedBG.red / 2, tmpColor.green / 2 + mTableColor.mSelectedBG.green / 2, tmpColor.blue / 2 + mTableColor.mSelectedBG.blue / 2)
                        bgColor = "#" + Integer.toHexString(Color(
                                tmpColor.red / 2 + mTableColor.mSelectedBG.red / 2,
                                tmpColor.green / 2 + mTableColor.mSelectedBG.green / 2,
                                tmpColor.blue / 2 + mTableColor.mSelectedBG.blue / 2).rgb).substring(2).uppercase()
                    }

                    stringBuilder.replace(
                            end,
                            end,
                            newValue.substring(end, end) + "</font></b>"
                    )
                    stringBuilder.replace(
                            start,
                            end,
                            newValue.substring(start, end).replace("  ", " &nbsp;")
                    )
                    stringBuilder.replace(
                            start,
                            start,
                            "<b><font style=\"color: $fgColor; background-color: $bgColor\">" + newValue.substring(start, start)
                    )
                }
                beforeStart = start
            }
            if (beforeStart > 0) {
                stringBuilder.replace(0, beforeStart, newValue.substring(0, beforeStart).replace("  ", " &nbsp;"))
            }
        }

        val color = getFgStrColor(row)
        if (htmlTag) {
            stringBuilder.replace(0, 0, "<html><p><nobr><font color=$color>")
            stringBuilder.append("</font></nobr></p></html>")
        }
        else {
            stringBuilder.replace(0, 0, "<font color=$color>")
            stringBuilder.append("</font>")
        }

        return stringBuilder.toString()
    }

    inner class LogItem(val mNum: String, val mLogLine: String, val mLevel: Int, val mTokenFilterLogs: Array<String>, val mTokenLogs: List<String>?, val mProcessName: String?) {
    }

    open fun makeLogItem(num: Int, logLine: String): LogItem {
        val level: Int
        val tokenFilterLogs: Array<String>

        val textSplited = FormatManager.splitLog(logLine, mTokenCount, mSeparator, mSeparatorList)
        if (textSplited.size > mTokenNthMax) {
            level = if (mFilterLevel == LEVEL_NONE) {
                LEVEL_NONE
            } else {
                mLevelMap[textSplited[mLevelIdx]] ?: LEVEL_NONE
            }

            tokenFilterLogs = Array(mSortedTokenFilters.size) {
                if (mSortedTokenFilters[it].mPosition >= 0) {
                    textSplited[mSortedTokenFilters[it].mPosition]
                }
                else {
                    ""
                }
            }
        } else {
            level = LEVEL_NONE
            tokenFilterLogs = mEmptyTokenFilters
        }

        val processName = if (TypeShowProcessName != SHOW_PROCESS_NONE && mSortedPidTokIdx >= 0 && tokenFilterLogs.size > mSortedPidTokIdx) {
            ProcessList.getInstance().getProcessName(tokenFilterLogs[mSortedPidTokIdx])
        } else {
            null
        }

        return LogItem(num.toString(), logLine, level, tokenFilterLogs, null, processName)
    }

    private fun makePattenPrintValue() {
        if (mBaseModel == null) {
            return
        }

        mBaseModel?.mFilterFindLog = mFilterFindLog
        if (mFilterFindLog.isEmpty()) {
            mPatternPrintFind = null
            mBaseModel?.mPatternPrintFind = null
        } else {
            var start = 0
            var index = 0
            var skip = false

            while (index != -1) {
                index = mFilterFindLog.indexOf('|', start)
                start = index + 1
                if (index == 0 || index == mFilterFindLog.lastIndex || mFilterFindLog[index + 1] == '|') {
                    skip = true
                    break
                }
            }

            if (!skip) {
                mPatternPrintFind = Pattern.compile(mFilterFindLog, mFindPatternCase)
                mBaseModel?.mPatternPrintFind = mPatternPrintFind
            }
        }

        mBaseModel?.mFilterHighlightLog = mFilterHighlightLog
        if (mFilterHighlightLog.isEmpty()) {
            mPatternPrintHighlight = null
            mBaseModel?.mPatternPrintHighlight = null
        } else {
            var start = 0
            var index = 0
            var skip = false

            while (index != -1) {
                index = mFilterHighlightLog.indexOf('|', start)
                start = index + 1
                if (index == 0 || index == mFilterHighlightLog.lastIndex || mFilterHighlightLog[index + 1] == '|') {
                    skip = true
                    break
                }
            }

            if (!skip) {
                mPatternPrintHighlight = Pattern.compile(mFilterHighlightLog, mPatternCase)
                mBaseModel?.mPatternPrintHighlight = mPatternPrintHighlight
            }
        }

        if (mFilterShowLog.isEmpty()) {
            mPatternPrintFilter = null
            mBaseModel?.mPatternPrintFilter = null
        } else {
            var start = 0
            var index = 0
            var skip = false

            while (index != -1) {
                index = mFilterShowLog.indexOf('|', start)
                start = index + 1
                if (index == 0 || index == mFilterShowLog.lastIndex || mFilterShowLog[index + 1] == '|') {
                    skip = true
                    break
                }
            }

            if (!skip) {
                mPatternPrintFilter = Pattern.compile(mFilterShowLog, mPatternCase)
                mBaseModel?.mPatternPrintFilter = mPatternPrintFilter
            }
        }

        return
    }

    private fun isMatchHideToken(item: LogItem): Boolean {
        var isMatch = false
        for (idx in 0 until FormatManager.MAX_TOKEN_FILTER_COUNT) {
            if (mFilterHideTokens[idx].isNotEmpty() && mPatternHideTokens[idx].matcher(item.mTokenFilterLogs[mSortedTokensIdxs[idx]]).find()) {
                isMatch = true
                break
            }
        }
        return isMatch
    }

    private fun isNotMatchShowToken(item: LogItem): Boolean {
        var isNotMatch = false
        for (idx in 0 until FormatManager.MAX_TOKEN_FILTER_COUNT) {
            if (mFilterShowTokens[idx].isNotEmpty() && mSortedTokensIdxs[idx] >= 0 && !mPatternShowTokens[idx].matcher(item.mTokenFilterLogs[mSortedTokensIdxs[idx]]).find()) {
                isNotMatch = true
                break
            }
        }
        return isNotMatch
    }

    private fun makeFilteredItems(isRedraw: Boolean) {
        if (mBaseModel == null || !mIsFilterUpdated) {
            Utils.printlnLog("skip makeFilteredItems $mBaseModel, $mIsFilterUpdated")
            return
        }
        else {
            mIsFilterUpdated = false
        }

        synchronized(this) {
            SwingUtilities.invokeAndWait {
                mLogItems.clear()
                mLogItems = mutableListOf()

                val logItems: MutableList<LogItem> = mutableListOf()
                if (mBookmarkMode) {
                    for (item in mBaseModel!!.mLogItems) {
                        if (mBookmarkManager.mBookmarks.contains(item.mNum.toInt())) {
                            logItems.add(item)
                        }
                    }
                } else {
                    makePattenPrintValue()
                    var isShow: Boolean

                    var regexShowLog = ""
                    var normalShowLog = ""
                    val showLogSplit = mFilterShowLog.split("|")

                    for (logUnit in showLogSplit) {
                        val hasIt: Boolean = logUnit.chars().anyMatch { c -> "\\.[]{}()*+?^$|".indexOf(c.toChar()) >= 0 }
                        if (hasIt) {
                            if (regexShowLog.isEmpty()) {
                                regexShowLog = logUnit
                            }
                            else {
                                regexShowLog += "|$logUnit"
                            }
                        }
                        else {
                            if (normalShowLog.isEmpty()) {
                                normalShowLog = logUnit
                            }
                            else {
                                normalShowLog += "|$logUnit"
                            }

                            if (mPatternCase == Pattern.CASE_INSENSITIVE) {
                                normalShowLog = normalShowLog.uppercase()
                            }
                        }
                    }

                    val patternShowLog = Pattern.compile(regexShowLog, mPatternCase)
                    val matcherShowLog = patternShowLog.matcher("")
                    val normalShowLogSplit = normalShowLog.split("|")

                    Utils.printlnLog("Show Log $normalShowLog, $regexShowLog")
                    for (item in mBaseModel!!.mLogItems) {
                        if (mIsFilterUpdated) {
                            break
                        }

                        isShow = true

                        if (!mFullMode) {
                            if (item.mLevel != LEVEL_NONE && item.mLevel < mFilterLevel) {
                                isShow = false
                            }
                            else if ((mFilterHideLog.isNotEmpty() && mPatternHideLog.matcher(item.mLogLine).find())
                                || isMatchHideToken(item)) {
                                isShow = false
                            }
                            else if (mFilterShowLog.isNotEmpty()) {
                                var isFound = false
                                if (normalShowLog.isNotEmpty()) {
                                    val logLine = if (mPatternCase == Pattern.CASE_INSENSITIVE) {
                                        item.mLogLine.uppercase()
                                    } else {
                                        item.mLogLine
                                    }
                                    for (sp in normalShowLogSplit) {
                                        if (logLine.contains(sp)) {
                                            isFound = true
                                            break
                                        }
                                    }
                                }

                                if (!isFound) {
                                    if (regexShowLog.isEmpty()) {
                                        isShow = false
                                    }
                                    else {
                                        matcherShowLog.reset(item.mLogLine)
                                        if (!matcherShowLog.find()) {
                                            isShow = false
                                        }
                                    }
                                }
                            }

                            if (isShow) {
                                if (isNotMatchShowToken(item)) {
                                    isShow = false
                                }
                            }
                        }

                        if (isShow || mBookmarkManager.mBookmarks.contains(item.mNum.toInt())) {
                            logItems.add(item)
                        }
                    }
                }

                mLogItems = logItems
            }

            if (!mIsFilterUpdated && isRedraw) {
                fireLogTableDataFiltered()
                mBaseModel?.fireLogTableDataFiltered()
            }
        }
    }

    internal inner class LogFilterItem(item: LogItem, isShow: Boolean) {
        val mItem = item
        val mIsShow = isShow
    }

    private var mScanThread:Thread? = null
    private var mFollowThread:Thread? = null
    private var mRecvThread:Thread? = null

    private var mFileWriter:FileWriter? = null
    private var mIsPause = false

    fun isScanning(): Boolean {
        return mScanThread != null
    }

    private fun updateLogItems(logLines: MutableList<String>, startNum: Int): Int {
        var removedCount = 0
        var baseRemovedCount = 0
        var num = startNum
        var isShow: Boolean
        var item: LogItem
        val logFilterItems: MutableList<LogFilterItem> = mutableListOf()
        synchronized(this) {
            for (tempLine in logLines) {
                item = makeLogItem(num, tempLine)
                isShow = true

                if (mBookmarkMode) {
                    isShow = false
                }

                if (!mFullMode) {
                    if (isShow && item.mLevel != LEVEL_NONE && item.mLevel < mFilterLevel) {
                        isShow = false
                    }
                    if (isShow
                        && ((mFilterHideLog.isNotEmpty() && mPatternHideLog.matcher(item.mLogLine)
                            .find())
                                || (mFilterShowLog.isNotEmpty() && !mPatternShowLog.matcher(item.mLogLine)
                            .find()))
                    ) {
                        isShow = false
                    }
                    if (isShow
                        && (isMatchHideToken(item) || isNotMatchShowToken(item))
                    ) {
                        isShow = false
                    }
                }
                logFilterItems.add(LogFilterItem(item, isShow))
                num++
            }

            SwingUtilities.invokeAndWait {
                if (mRecvThread == null) {
                    return@invokeAndWait
                }
                val isRemoveItem = !mScrollbackKeep && mScrollback > 0 && WaitTimeForDoubleClick < System.currentTimeMillis()

                for (filterItem in logFilterItems) {
                    if (mSelectionChanged) {
                        baseRemovedCount = 0
                        removedCount = 0
                        mSelectionChanged = false
                    }

                    if (mFilterTriggerLog.isNotEmpty() && mPatternTriggerLog.matcher(filterItem.mItem.mLogLine).find()) {
                        mAgingTestManager.pullTheTrigger(filterItem.mItem.mLogLine)
                    }

                    mBaseModel!!.mLogItems.add(filterItem.mItem)
                    if (filterItem.mIsShow || mBookmarkManager.mBookmarks.contains(filterItem.mItem.mNum.toInt())) {
                        mLogItems.add(filterItem.mItem)
                    }
                }
                if (isRemoveItem) {
                    while (mBaseModel!!.mLogItems.count() > mScrollback) {
                        mBaseModel!!.mLogItems.removeAt(0)
                        baseRemovedCount++
                    }

                    while (mLogItems.count() > mScrollback) {
                        mLogItems.removeAt(0)
                        removedCount++
                    }
                    fireLogTableDataChanged(removedCount)
                    removedCount = 0

                    mBaseModel!!.fireLogTableDataChanged(baseRemovedCount)
                    baseRemovedCount = 0
                }
            }
        }

        return num
    }
    fun startScan() {
        if (mLogFile == null) {
            return
        }

        if (SwingUtilities.isEventDispatchThread()) {
            mScanThread?.interrupt()
        }
        else {
            SwingUtilities.invokeAndWait {
                mScanThread?.interrupt()
            }
        }

        mGoToLast = true
        mBaseModel?.mGoToLast = true

        mScanThread = Thread {
            run {
                Utils.printlnLog("startScan thread started")
                SwingUtilities.invokeAndWait {
                    mLogItems.clear()
                    mLogItems = mutableListOf()
                    mBaseModel!!.mLogItems.clear()
                    mBaseModel!!.mLogItems = mutableListOf()
                    mBaseModel!!.mBookmarkManager.clear()
                    fireLogTableDataCleared()
                    mBaseModel!!.fireLogTableDataCleared()
                }
                fireLogTableDataChanged()
                mBaseModel!!.fireLogTableDataChanged()
                makePattenPrintValue()

                try {
                    if (MainUI.CurrentMethod == MainUI.METHOD_ADB && TypeShowProcessName != SHOW_PROCESS_NONE) {
                        ProcessList.getInstance().getProcessName("0")
                    }
                } catch (e: Exception) {
                    Utils.printlnLog("startScan thread stop - getProcessName : ${e.stackTraceToString()}")

                    mFileWriter?.close()
                    mFileWriter = null
                    return@run
                }

                var currLogFile: File? = mLogFile
                var bufferedReader = BufferedReader(InputStreamReader(mLogCmdManager.mProcessLogcat!!.inputStream))
                var line: String?
                var saveNum = 0
                var startNum = 0

                var nextUpdateTime: Long = 0

                val logLines: MutableList<String> = mutableListOf()

                line = bufferedReader.readLine()
                while (line != null || mMainUI.isRestartAdbLogcat()) {
                    try {
                        nextUpdateTime = System.currentTimeMillis() + 100
                        logLines.clear()

                        if (line == null && mMainUI.isRestartAdbLogcat()) {
                            Utils.printlnLog("line is Null")
                            if (mLogCmdManager.mProcessLogcat == null || !mLogCmdManager.mProcessLogcat!!.isAlive) {
                                if (mMainUI.isRestartAdbLogcat()) {
                                    Thread.sleep(5000)
                                    mMainUI.restartAdbLogcat()
                                    if (mLogCmdManager.mProcessLogcat?.inputStream != null) {
                                        bufferedReader =
                                            BufferedReader(InputStreamReader(mLogCmdManager.mProcessLogcat?.inputStream!!))
                                    } else {
                                        Utils.printlnLog("startScan : inputStream is Null")
                                    }
                                    line = "LogNote - RESTART LOGCAT"
                                }
                            }
                        }

                        if (!mIsPause) {
                            while (line != null) {
                                if (currLogFile != mLogFile) {
                                    try {
                                        mFileWriter?.flush()
                                    } catch (e: IOException) {
                                        e.printStackTrace()
                                    }
                                    mFileWriter?.close()
                                    mFileWriter = null
                                    currLogFile = mLogFile
                                    saveNum = 0
                                }

                                if (mFileWriter == null) {
                                    mFileWriter = FileWriter(mLogFile)
                                }
                                mFileWriter?.write(line + "\n")
                                saveNum++

                                if (mScrollbackSplitFile && mScrollback > 0 && saveNum >= mScrollback) {
                                    mMainUI.setSaveLogFile()
                                    Utils.printlnLog("Change save file : ${mLogFile?.absolutePath}")
                                }

                                logLines.add(line)
                                line = bufferedReader.readLine()
                                if (System.currentTimeMillis() > nextUpdateTime) {
                                    break
                                }
                            }
                        } else {
                            Thread.sleep(1000)
                        }

                        startNum = updateLogItems(logLines, startNum)
                    } catch (e: Exception) {
                        Utils.printlnLog("startScan thread stop")
                        Utils.printlnLog("stack trace : ${e.stackTraceToString()}")

                        if (e !is InterruptedException) {
                            JOptionPane.showMessageDialog(mMainUI, e.message, "Error", JOptionPane.ERROR_MESSAGE)
                        }

                        try {
                            mFileWriter?.flush()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                        mFileWriter?.close()
                        mFileWriter = null
                        return@run
                    }
                }
            }
        }
        mRecvThread = mScanThread
        mScanThread?.start()

        return
    }

    fun stopScan(){
        if (SwingUtilities.isEventDispatchThread()) {
            mScanThread?.interrupt()
        }
        else {
            SwingUtilities.invokeAndWait {
                mScanThread?.interrupt()
            }
        }
        mRecvThread = null
        mScanThread = null
        if (mFileWriter != null) {
            try {
                mFileWriter?.flush()
            } catch(e:IOException) {
                e.printStackTrace()
            }
            mFileWriter?.close()
            mFileWriter?.close()
            mFileWriter = null
        }
        return
    }

    fun pauseScan(pause:Boolean) {
        Utils.printlnLog("Pause adb scan $pause")
        mIsPause = pause
    }

    private var mIsFollowPause = false
    private var mIsKeepReading = true

    fun isFollowing(): Boolean {
        return mFollowThread != null
    }

    internal inner class MyFileInputStream(currLogFile: File?) : FileInputStream(currLogFile) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            var input = super.read(b, off, len)
            while (input == -1) {
                Thread.sleep(1000)
                input = super.read(b, off, len)
            }
            return input
        }
    }

    fun startFollow() {
        if (mLogFile == null) {
            return
        }

        if (SwingUtilities.isEventDispatchThread()) {
            mFollowThread?.interrupt()
        }
        else {
            SwingUtilities.invokeAndWait {
                mFollowThread?.interrupt()
            }
        }

        mGoToLast = true
        mBaseModel?.mGoToLast = true

        mFollowThread = Thread {
            run {
                Utils.printlnLog("startFollow thread started")

                SwingUtilities.invokeAndWait {
                    mIsKeepReading = true
                    mLogItems.clear()
                    mLogItems = mutableListOf()
                    mBaseModel!!.mLogItems.clear()
                    mBaseModel!!.mLogItems = mutableListOf()
                    mBaseModel!!.mBookmarkManager.clear()
                    fireLogTableDataCleared()
                    mBaseModel!!.fireLogTableDataCleared()
                }
                fireLogTableDataChanged()
                mBaseModel!!.fireLogTableDataChanged()
                makePattenPrintValue()

                val currLogFile: File? = mLogFile
                val scanner = Scanner(MyFileInputStream(currLogFile))
                var line: String? = null
                var startNum = 0

                var nextUpdateTime: Long = 0

                val logLines: MutableList<String> = mutableListOf()

                while (mIsKeepReading) {
                    try {
                        nextUpdateTime = System.currentTimeMillis() + 100
                        logLines.clear()
                        if (!mIsPause) {
                            while (mIsKeepReading) {
                                if (scanner.hasNextLine()) {
                                    line = try {
                                        scanner.nextLine()
                                    } catch (e: NoSuchElementException) {
                                        null
                                    }
                                } else {
                                    line = null
                                }
                                if (line == null) {
                                    Thread.sleep(1000)
                                } else {
                                    break
                                }
                            }

                            while (line != null) {
                                logLines.add(line)

                                if (scanner.hasNextLine()) {
                                    line = try {
                                        scanner.nextLine()
                                    } catch (e: NoSuchElementException) {
                                        null
                                    }
                                } else {
                                    line = null
                                }
                                if (System.currentTimeMillis() > nextUpdateTime) {
                                    if (line != null) {
                                        logLines.add(line)
                                    }
                                    break
                                }
                            }
                        } else {
                            Thread.sleep(1000)
                        }

                        startNum = updateLogItems(logLines, startNum)
                    } catch (e: Exception) {
                        Utils.printlnLog("startFollow thread stop")
                        Utils.printlnLog("stack trace : ${e.stackTraceToString()}")

                        if (e !is InterruptedException) {
                            JOptionPane.showMessageDialog(mMainUI, e.message, "Error", JOptionPane.ERROR_MESSAGE)
                        }

                        return@run
                    }
                }
                Utils.printlnLog("Exit follow")
            }
        }
        mRecvThread = mFollowThread
        mFollowThread?.start()

        return
    }

    fun stopFollow(){
        if (SwingUtilities.isEventDispatchThread()) {
            mIsKeepReading = false
            mFollowThread?.interrupt()
        }
        else {
            SwingUtilities.invokeAndWait {
                mIsKeepReading = false
                mFollowThread?.interrupt()
            }
        }
        mRecvThread = null
        mFollowThread = null
        return
    }

    fun pauseFollow(pause:Boolean) {
        Utils.printlnLog("Pause file follow $pause")
        mIsFollowPause = pause
    }

    fun moveToNextFind() {
        moveToFind(true)
    }

    fun moveToPrevFind() {
        moveToFind(false)
    }

    private infix fun Int.toward(to: Int): IntProgression {
        val step = if (this > to) -1 else 1
        return IntProgression.fromClosedRange(this, to, step)
    }

    private fun moveToFind(isNext: Boolean) {
        val selectedRow = if (mBaseModel != null) {
            mMainUI.mFilteredLogPanel.getSelectedRow()
        }
        else {
            mMainUI.mFullLogPanel.getSelectedRow()
        }

        var startRow = 0
        var endRow = 0

        if (isNext) {
            startRow = selectedRow + 1
            endRow = mLogItems.count() - 1
            if (startRow > endRow) {
                mMainUI.showFindResultTooltip(isNext,"\"${mFilterFindLog}\" ${Strings.NOT_FOUND}")
                return
            }
        }
        else {
            startRow = selectedRow - 1
            endRow = 0

            if (startRow < endRow) {
                mMainUI.showFindResultTooltip(isNext,"\"${mFilterFindLog}\" ${Strings.NOT_FOUND}")
                return
            }
        }

        var idxFound = -1
        for (idx in startRow toward endRow) {
            val item = mLogItems[idx]
            if (mNormalFindLogSplit != null) {
                var logLine = ""
                logLine = if (mFindPatternCase == Pattern.CASE_INSENSITIVE) {
                    item.mLogLine.uppercase()
                } else {
                    item.mLogLine
                }
                for (sp in mNormalFindLogSplit!!) {
                    if (sp.isNotEmpty() && logLine.contains(sp)) {
                        idxFound = idx
                        break
                    }
                }
            }

            if (idxFound < 0 && mRegexFindLog.isNotEmpty()) {
                mMatcherFindLog.reset(item.mLogLine)
                if (mMatcherFindLog.find()) {
                    idxFound = idx
                }
            }

            if (idxFound >= 0) {
                break
            }
        }

        if (idxFound >= 0) {
            if (mBaseModel != null) {
                mMainUI.mFilteredLogPanel.goToRow(idxFound, 0)
            }
            else {
                mMainUI.mFullLogPanel.goToRow(idxFound, 0)
            }
        }
        else {
            mMainUI.showFindResultTooltip(isNext,"\"${mFilterFindLog}\" ${Strings.NOT_FOUND}")
        }
    }

    fun getValuePid(row: Int): String {
        return if (row >= 0 && row < mLogItems.size) {
            if (mSortedPidTokIdx >= 0) {
                mLogItems[row].mTokenFilterLogs[mSortedPidTokIdx]
            }
            else {
                ""
            }
        } else {
            ""
        }
    }

    fun saveFile(target: String) {
        val bufferedWriter = BufferedWriter(FileWriter(target))
        if (SwingUtilities.isEventDispatchThread()) {
            for (item in mLogItems) {
                bufferedWriter.write(item.mLogLine)
                bufferedWriter.newLine()
            }
        }
        else {
            SwingUtilities.invokeAndWait {
                for (item in mLogItems) {
                    bufferedWriter.write(item.mLogLine)
                    bufferedWriter.newLine()
                }
            }
        }
        bufferedWriter.flush()
        bufferedWriter.close()
    }
}
