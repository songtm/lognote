package com.blogspot.cdcsutils.lognote

import java.util.Base64

class BookmarkEvent(change:Int) {
    val mBookmarkChange = change
    companion object {
        const val ADDED = 0
        const val REMOVED = 1
    }
}

interface BookmarkEventListener {
    fun bookmarkChanged(event:BookmarkEvent?)
}

class BookmarkSnapshot(val mBookmarks: List<Int>, val mComments: Map<Int, String>)

class BookmarkManager private constructor(){
    companion object {
        private val mInstance: BookmarkManager = BookmarkManager()

        fun getInstance(): BookmarkManager {
            return mInstance
        }
    }

    var mBookmarks = mutableListOf<Int>()
    private val mBookmarkComments = mutableMapOf<Int, String>()
    private val mEventListeners = ArrayList<BookmarkEventListener>()

    fun addBookmarkEventListener(listener:BookmarkEventListener) {
        mEventListeners.add(listener)
    }

    fun isBookmark(bookmark:Int): Boolean {
        return mBookmarks.contains(bookmark)
    }

    fun getBookmarkComment(bookmark:Int): String? {
        return mBookmarkComments[bookmark]
    }

    fun updateBookmark(bookmark:Int) {
        if (mBookmarks.contains(bookmark)) {
            removeBookmark(bookmark)
        } else {
            addBookmark(bookmark)
        }
    }

    fun addBookmark(bookmark:Int, comment:String? = null) {
        if (mBookmarks.contains(bookmark)) {
            Utils.printlnLog("addBookmark : already added - $bookmark")
            return
        }
        mBookmarks.add(bookmark)
        mBookmarks.sort()
        if (!comment.isNullOrEmpty()) {
            mBookmarkComments[bookmark] = comment
        }

        for (listener in mEventListeners) {
            listener.bookmarkChanged(BookmarkEvent(BookmarkEvent.ADDED))
        }
    }

    fun removeBookmark(bookmark:Int) {
        if (!mBookmarks.contains(bookmark)) {
            Utils.printlnLog("addBookmark : already removed - $bookmark")
            return
        }
        mBookmarks.remove(bookmark)
        mBookmarkComments.remove(bookmark)

        for (listener in mEventListeners) {
            listener.bookmarkChanged(BookmarkEvent(BookmarkEvent.REMOVED))
        }
    }

    fun clear() {
        mBookmarks.clear()
        mBookmarkComments.clear()

        for (listener in mEventListeners) {
            listener.bookmarkChanged(BookmarkEvent(BookmarkEvent.REMOVED))
        }
    }

    fun snapshotBookmarks(): BookmarkSnapshot {
        return BookmarkSnapshot(mBookmarks.toList(), mBookmarkComments.toMap())
    }

    fun restoreBookmarks(snapshot: BookmarkSnapshot) {
        mBookmarks.clear()
        mBookmarks.addAll(snapshot.mBookmarks)
        mBookmarkComments.clear()
        mBookmarkComments.putAll(snapshot.mComments)

        for (listener in mEventListeners) {
            listener.bookmarkChanged(BookmarkEvent(BookmarkEvent.ADDED))
        }
    }

    fun serializeBookmarks(startLine: Int, endLine: Int): String {
        val sb = StringBuilder()
        for (bookmark in mBookmarks) {
            if (bookmark < startLine || bookmark > endLine) {
                continue
            }
            sb.append(bookmark - startLine)
            val comment = mBookmarkComments[bookmark]
            if (!comment.isNullOrEmpty()) {
                sb.append(':').append(Base64.getEncoder().encodeToString(comment.toByteArray(Charsets.UTF_8)))
            }
            sb.append(',')
        }
        return sb.toString()
    }

    fun deserializeBookmarks(data: String, startLine: Int) {
        for (entry in data.split(',')) {
            if (entry.isBlank()) {
                continue
            }
            val colonIdx = entry.indexOf(':')
            if (colonIdx < 0) {
                entry.trim().toIntOrNull()?.let { addBookmark(it + startLine) }
                continue
            }
            val line = entry.substring(0, colonIdx).trim().toIntOrNull() ?: continue
            val comment = if (colonIdx + 1 < entry.length) {
                try {
                    String(Base64.getDecoder().decode(entry.substring(colonIdx + 1)), Charsets.UTF_8)
                } catch (e: Exception) {
                    entry.substring(colonIdx + 1)
                }
            } else {
                null
            }
            addBookmark(line + startLine, comment)
        }
    }

    fun getNextBookmark(row: Int): Int {
        if (mBookmarks.isEmpty()) return -1
        for (bookmark in mBookmarks) {
            if (row < bookmark) return bookmark
        }
        return mBookmarks[0]
    }

    fun getPrevBookmark(row: Int): Int {
        if (mBookmarks.isEmpty()) return -1
        for (bookmark in mBookmarks.reversed()) {
            if (row > bookmark) return bookmark
        }
        return mBookmarks.last()
    }
}