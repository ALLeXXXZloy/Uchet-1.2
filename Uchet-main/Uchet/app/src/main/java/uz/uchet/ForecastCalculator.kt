package uz.uchet

import java.time.YearMonth
import kotlin.math.roundToLong

object ForecastCalculator {

    data class DepositMonthState(
        val month: YearMonth,
        val startBalance: Long,
        val interest: Long,
        val accruedInterestNotYetPaid: Long, // для END_OF_TERM: накоплено, но не выплачено
        val contribution: Long,
        val withdrawal: Long,
        val oneTimeOps: Long,
        val endBalance: Long,
        val active: Boolean
    )

    data class MonthForecast(
        val month: YearMonth,
        val income: Long,
        val expense: Long,
        val depositPortfolioChange: Long,
        val depositPortfolioEnd: Long,
        val runningBalance: Long
    )

    fun monthsOf(forecast: ForecastPlanEntity): List<YearMonth> {
        val start = YearMonth.parse(forecast.startMonth)
        return (0 until forecast.months).map { start.plusMonths(it.toLong()) }
    }

    // Ежемесячная ставка через простые проценты (не зависит от дней месяца)
    private fun monthlyInterest(balance: Long, annualRatePercent: Double): Long {
        val r = annualRatePercent / 12.0 / 100.0
        return (balance * r).roundToLong()
    }

    // Ежедневные проценты: balance × rate/365/100 × daysInMonth
    private fun dailyInterest(balance: Long, annualRatePercent: Double, daysInMonth: Int): Long {
        val r = annualRatePercent / 365.0 / 100.0
        return (balance * r * daysInMonth).roundToLong()
    }

    // Полный расчёт прогноза
    fun calculate(
        forecast: ForecastPlanEntity,
        items: List<ForecastItemEntity>,
        deposits: List<ForecastDepositEntity>,
        allOps: List<ForecastDepositOpEntity>
    ): List<MonthForecast> {
        val months = monthsOf(forecast)
        val result = mutableListOf<MonthForecast>()
        var runningBalance = 0L
        val depositPrevBalance = mutableMapOf<Long, Long>()
        val depositAccrued = mutableMapOf<Long, Long>()

        months.forEach { month ->
            val monthStr = month.toString()

            // 1. Доходы/расходы
            val activeItems = items.filter { item ->
                item.forecastId == forecast.id &&
                monthStr >= item.startMonth &&
                monthStr <= item.endMonth
            }
            val income = activeItems.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
            val expense = activeItems.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }

            // 2. Вклады
            val activeDeposits = deposits.filter {
                it.forecastId == forecast.id &&
                monthStr >= it.startMonth &&
                monthStr <= it.endMonth
            }

            var portfolioStart = 0L
            var portfolioEnd = 0L

            activeDeposits.forEach { dep ->
                val isFirst = monthStr == dep.startMonth
                val isLast = monthStr == dep.endMonth
                val startBalance = if (isFirst) dep.initialAmountMinor
                    else (depositPrevBalance[dep.id] ?: dep.initialAmountMinor)
                val prevAccrued = depositAccrued[dep.id] ?: 0L
                val daysInMonth = month.lengthOfMonth()

                // Начисленные проценты за месяц
                val interest: Long = when (dep.capitalization) {
                    CapitalizationType.MONTHLY -> monthlyInterest(startBalance, dep.annualRatePercent)
                    CapitalizationType.DAILY,
                    CapitalizationType.END_OF_TERM -> dailyInterest(startBalance, dep.annualRatePercent, daysInMonth)
                }

                // Регулярные пополнения/снятия (не в первом месяце — там только initial)
                val contribution = if (!isFirst) dep.monthlyContributionMinor else 0L
                val withdrawal = if (!isFirst) dep.monthlyWithdrawalMinor else 0L

                // Разовые операции этого месяца
                val oneTimeOps = allOps
                    .filter { it.depositId == dep.id && it.month == monthStr }
                    .sumOf { it.amountMinor }

                // Итог месяца по режиму
                val newAccrued: Long
                val endBalance: Long
                when (dep.capitalization) {
                    CapitalizationType.MONTHLY, CapitalizationType.DAILY -> {
                        endBalance = startBalance + interest + contribution - withdrawal + oneTimeOps
                        newAccrued = 0L
                    }
                    CapitalizationType.END_OF_TERM -> {
                        val totalAccrued = prevAccrued + interest
                        if (isLast) {
                            // Выплата в конце срока: проценты идут в баланс
                            endBalance = startBalance + contribution - withdrawal + oneTimeOps + totalAccrued
                            newAccrued = 0L
                        } else {
                            // Проценты накапливаются отдельно, в баланс не идут
                            endBalance = startBalance + contribution - withdrawal + oneTimeOps
                            newAccrued = totalAccrued
                        }
                    }
                }

                portfolioStart += startBalance
                portfolioEnd += endBalance
                depositPrevBalance[dep.id] = endBalance
                depositAccrued[dep.id] = newAccrued
            }

            val portfolioChange = portfolioEnd - portfolioStart
            runningBalance += (income - expense) + portfolioChange

            result.add(
                MonthForecast(
                    month = month,
                    income = income,
                    expense = expense,
                    depositPortfolioChange = portfolioChange,
                    depositPortfolioEnd = portfolioEnd,
                    runningBalance = runningBalance
                )
            )
        }
        return result
    }

    // Полный расчёт состояния одного вклада (для экрана вклада)
    fun calculateDepositTimeline(
        deposit: ForecastDepositEntity,
        allOps: List<ForecastDepositOpEntity>
    ): List<DepositMonthState> {
        val start = YearMonth.parse(deposit.startMonth)
        val end = YearMonth.parse(deposit.endMonth)
        val result = mutableListOf<DepositMonthState>()
        var prevEnd = deposit.initialAmountMinor
        var prevAccrued = 0L
        var current = start

        while (current <= end) {
            val isFirst = current == start
            val isLast = current == end
            val startBalance = if (isFirst) deposit.initialAmountMinor else prevEnd
            val daysInMonth = current.lengthOfMonth()

            val interest: Long = when (deposit.capitalization) {
                CapitalizationType.MONTHLY -> monthlyInterest(startBalance, deposit.annualRatePercent)
                CapitalizationType.DAILY,
                CapitalizationType.END_OF_TERM -> dailyInterest(startBalance, deposit.annualRatePercent, daysInMonth)
            }

            val contribution = if (!isFirst) deposit.monthlyContributionMinor else 0L
            val withdrawal = if (!isFirst) deposit.monthlyWithdrawalMinor else 0L
            val oneTimeOps = allOps
                .filter { it.depositId == deposit.id && it.month == current.toString() }
                .sumOf { it.amountMinor }

            val newAccrued: Long
            val endBalance: Long
            when (deposit.capitalization) {
                CapitalizationType.MONTHLY, CapitalizationType.DAILY -> {
                    endBalance = startBalance + interest + contribution - withdrawal + oneTimeOps
                    newAccrued = 0L
                }
                CapitalizationType.END_OF_TERM -> {
                    val totalAccrued = prevAccrued + interest
                    if (isLast) {
                        endBalance = startBalance + contribution - withdrawal + oneTimeOps + totalAccrued
                        newAccrued = 0L
                    } else {
                        endBalance = startBalance + contribution - withdrawal + oneTimeOps
                        newAccrued = totalAccrued
                    }
                }
            }

            result.add(
                DepositMonthState(
                    month = current,
                    startBalance = startBalance,
                    interest = interest,
                    accruedInterestNotYetPaid = newAccrued,
                    contribution = contribution,
                    withdrawal = withdrawal,
                    oneTimeOps = oneTimeOps,
                    endBalance = endBalance,
                    active = true
                )
            )
            prevEnd = endBalance
            prevAccrued = newAccrued
            current = current.plusMonths(1)
        }
        return result
    }
}
