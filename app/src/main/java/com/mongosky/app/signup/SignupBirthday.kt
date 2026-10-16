package com.mongosky.app.signup

import java.time.DateTimeException
import java.time.LocalDate
import java.time.YearMonth

/** Web-equivalent calendar rules; stored/transmitted values are Gregorian integers. */
internal data class SignupBirthday(val month: Int? = null, val day: Int? = null, val year: Int? = null) {
    val dayCount: Int get() {
        val selectedMonth = month ?: return 31
        val selectedYear = year ?: return 31
        if (selectedMonth !in 1..12 || selectedYear < 1900) return 31
        return runCatching { YearMonth.of(selectedYear, selectedMonth).lengthOfMonth() }.getOrDefault(31)
    }

    fun withMonth(value: Int) = copy(month = value).clearInvalidDay()
    fun withYear(value: Int) = copy(year = value).clearInvalidDay()
    fun withDay(value: Int) = copy(day = value.takeIf { it in 1..dayCount })
    private fun clearInvalidDay() = if (day != null && day !in 1..dayCount) copy(day = null) else this

    fun dateOrNull(): LocalDate? {
        if (month == null || day == null || year == null || year < 1900) return null
        return try { LocalDate.of(year, month, day) } catch (_: DateTimeException) { null }
    }

    fun validationError(today: LocalDate = LocalDate.now()): String? {
        if (month == null || day == null || year == null) return "Select your date of birth."
        val date = dateOrNull() ?: return "Enter a valid date of birth."
        return if (date.isAfter(today)) "Your date of birth cannot be in the future." else null
    }

    companion object {
        fun years(today: LocalDate = LocalDate.now()) = (today.year downTo 1900).toList()
    }
}
