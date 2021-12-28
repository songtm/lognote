package com.blogspot.kotlinstudy.lognote

class TooltipStringsEn private constructor() {
    companion object {
        val STRINGS = listOf(
            "Start adb logcat and receive logs"
            , "Stop adb logcat"
            , "Clear log view"
            , "Save new file"
            , "List of connected devices or an address to connect to\n When the Enter key (or ctrl-r) is pressed, log reception starts after reconnection"
            , "adb connect [address]"
            , "Get list of connected devices"
            , "adb disconnect"
            , "Number of log lines to keep in the log view (0: no limit)"
            , "Save file is changed every number of scroll lines"
            , "Apply Line Count and File Split"
            , "Log is kept in the log view, used when the log is pushed up by the number of applied lines when reviewing the log"
            , "Change log views position up/down, left/right"
            , "Log filter case-sensitive"
            , "Manage frequently used filters"
            , "Full log filter"
            , "Regex support, filter: search, -filter: exclude search, ex filter1|filter2|-exclude1"
            , "Tag filter"
            , "Regex support, filter: search, -filter: exclude search, ex filter1|filter2|-exclude1"
            , "PID filter"
            , "Regex support, filter: search, -filter: exclude search, ex filter1|filter2|-exclude1"
            , "TID filter"
            , "Regex support, filter: search, -filter: exclude search, ex filter1|filter2|-exclude1"
            , "Change text to bold only"
            , "Regex support ex text1|text2"
            , "Go to first line"
            , "Go to last line"
            , "Highlight PID"
            , "Highlight TID"
            , "Highlight tags"
            , "Show full log"
            , "Show bookmarks only"
            , "Move the view to a new window"
            , "Saved file name"
            , "Open filter list(add filter)"
//            , ""
        )
    }
}