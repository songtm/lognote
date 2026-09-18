package com.blogspot.cdcsutils.lognote

import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel


class VStatusPanel(logTable: LogTable) : JPanel() {
    private val mLogTable = logTable
    private val mBookmarkManager = BookmarkManager.getInstance()

    companion object {
        const val VIEW_RECT_WIDTH = 20
        const val VIEW_RECT_HEIGHT = 5
    }
    init {
        preferredSize = Dimension(VIEW_RECT_WIDTH, VIEW_RECT_HEIGHT)
        background = if (MainUI.IsFlatLaf && !MainUI.IsFlatLightLaf) {
            Color(0x46494B)
        }
        else {
            Color.WHITE
        }
        border = BorderFactory.createLineBorder(Color.DARK_GRAY)
        addMouseListener(MouseHandler())
    }

    override fun updateUI() {
        background = if (MainUI.IsFlatLaf && !MainUI.IsFlatLightLaf) {
            Color(0x46494B)
        }
        else {
            Color.WHITE
        }
        super.updateUI()
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        if (MainUI.IsFlatLaf && !MainUI.IsFlatLightLaf) {
            g?.color = Color(0xFFFFFF)
        }
        else {
            g?.color = Color(0x000000)
        }
        // 只遍历书签(数量通常很少)并通过二分查找定位行号,
        // 避免大文件下每次滚动重绘都遍历全部行(300 万行时每帧 O(N) 导致滚动卡顿)
        val rowCount = mLogTable.rowCount
        if (rowCount > 0 && height > 0 && mBookmarkManager.mBookmarks.isNotEmpty()) {
            val logItems = mLogTable.mTableModel.mLogItems
            for (num in mBookmarkManager.mBookmarks) {
                val idx = logItems.binarySearch { logItem -> logItem.mNum.toInt() - num }
                if (idx in 0 until rowCount) {
                    val y = (idx.toLong() * height / rowCount).toInt()
                    g?.fillRect(0, y, width, 1)
                }
            }
        }

        val visibleY:Long = (mLogTable.visibleRect.y).toLong()
        val totalHeight:Long = (mLogTable.rowHeight * mLogTable.rowCount).toLong()
        if (mLogTable.rowCount != 0 && height != 0) {
            if (MainUI.IsFlatLaf && !MainUI.IsFlatLightLaf) {
                g?.color = Color(0xC0, 0xC0, 0xC0, 0x50)
            }
            else {
                g?.color = Color(0xA0, 0xA0, 0xA0, 0x50)
            }
            var viewHeight = mLogTable.visibleRect.height * height / totalHeight
            if (viewHeight < VIEW_RECT_HEIGHT) {
                viewHeight = VIEW_RECT_HEIGHT.toLong()
            }

            var viewY = visibleY * height / totalHeight
            if (viewY + viewHeight > height) {
                viewY = height - viewHeight
            }
            g?.fillRect(0, viewY.toInt(), width, viewHeight.toInt())
        }
    }

    internal inner class MouseHandler : MouseAdapter() {
        override fun mouseClicked(p0: MouseEvent?) {
            val row = p0!!.point.y * mLogTable.rowCount / height
            try {
                // mLogTable.setRowSelectionInterval(row, row)
                mLogTable.scrollRectToVisible(Rectangle(mLogTable.getCellRect(row, 0, true)))
            } catch (e: IllegalArgumentException) {
                Utils.printlnLog("e : $e")
            }
            super.mouseClicked(p0)
        }
    }
}
