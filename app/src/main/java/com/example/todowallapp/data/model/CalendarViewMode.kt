package com.example.todowallapp.data.model

enum class CalendarViewMode {
    MONTH,
    WEEK,
    THREE_DAY,  // 3-column landscape view showing today ± 1 day side-by-side
    DAY         // existing day schedule behavior — accessible via drill-down from MONTH/WEEK
}
