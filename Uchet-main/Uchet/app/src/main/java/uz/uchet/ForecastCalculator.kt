package uz.uchet

import java.time.YearMonth
import kotlin.math.roundToLong

object ForecastCalculator {

    data class DepositMonthState(
        val month: YearMonth,
        val startBalance: Long,
        val interest: Long,
        val contribution: Long,
        val withdrawal: Long,
        val oneTimeOps: Long,
        val endBalance: Long,
        val active: Boolean            // true, если вклад активен в этом месяце
    )

    data class MonthForecast(
        val month: YearMonth,
        val income: Long,              // доходы из ForecastItem (INCOME)
        val expense: Long,             // расходы из ForecastItem (EXPENSE)
        val depositPortfolioChange: Long, // общий прирост/убыль портфеля вкладов (для баланса)
        val depositPortfolioEnd: Long, // общая сумма вкладов на конец месяца
        val runningBalance: Long       // накопительный баланс на конец месяца
    )

    // Месяцы прогноза
    fun monthsOf(forecast: ForecastPlanEntity): List<YearMonth> {
        val start = YearMonth.parse(forecast.startMonth)
        return (0 until forecast.months).map { start.plusMonths(it.toLong()) }
    }

    // Состояние одного вклада за один месяц
    fun depositStateForMonth(
        deposit: ForecastDepositEntity,
        month: YearMonth,
        allOps: List<ForecastDepositOpEntity>,
        previousEndBalance: Long
    ): DepositMonthState {
        val start = YearMonth.parse(deposit.startMonth)
        val end = YearMonth.parse(deposit.endMonth)
        val active = month >= start && month <= end

        if (!active) {
            return DepositMonthState(
                month = month, startBalance = 0L, interest = 0L,
                contribution = 0L, withdrawal = 0L, oneTimeOps = 0L,
                endBalance = 0L, active = false
            )
        }

        val startBalance = previousEndBalance

        // Проценты начисляются на начало месяца (простая капитализация ежемесячно)
        val monthlyRate = deposit.annualRatePercent / 12.0 / 100.0
        val interest = (startBalance * monthlyRate).roundToLong()

        // Регулярные операции (только если не первый и не последний месяц)
        // Простое правило: regular applies в каждом месяце жизни вклада, кроме start
        val contribution = if (month > start) deposit.monthlyContributionMinor else 0L
        val withdrawal  = if (month > start) deposit.monthlyWithdrawalMinor else 0L

        // Разовые операции на этот месяц
        val monthStr = month.toString()
        val oneTimeOps = allOps.filter { it.depositId == deposit.id && it.month == monthStr }
            .sumOf { it.amountMinor }

        val endBalance = startBalance + interest + contribution - withdrawal + oneTimeOps

        return DepositMonthState(
            month = month, startBalance = startBalance, interest = interest,
            contribution = contribution, withdrawal = withdrawal,
            oneTimeOps = oneTimeOps, endBalance = endBalance, active = true
        )
    }

    // Полный расчёт прогноза на N месяцев
    fun calculate(
        forecast: ForecastPlanEntity,
        items: List<ForecastItemEntity>,
        deposits: List<ForecastDepositEntity>,
        allOps: List<ForecastDepositOpEntity>
    ): List<MonthForecast> {
        val months = monthsOf(forecast)
        val result = mutableListOf<MonthForecast>()
        var runningBalance = 0L
        // Храним предыдущий endBalance каждого вклада
        val depositPrevBalance = mutableMapOf<Long, Long>()

        months.forEachIndexed { index, month ->
            // 1. Доходы/расходы по items
            val monthStr = month.toString()
            val activeItems = items.filter { item ->
                item.forecastId == forecast.id &&
                monthStr >= item.startMonth &&
                monthStr <= item.endMonth
            }
            val income = activeItems.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }
            val expense = activeItems.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }

            // 2. Расчёт по вкладам
            val activeDeposits = deposits.filter {
                it.forecastId == forecast.id &&
                monthStr >= it.startMonth &&
                monthStr <= it.endMonth
            }

            var portfolioStart = 0L
            var portfolioEnd = 0L
            var portfolioChange = 0L

            activeDeposits.forEach { dep ->
                val prevEnd = depositPrevBalance[dep.id] ?: dep.initialAmountMinor
                // Если это первый месяц жизни вклада — startBalance = initialAmount
                val isFirstMonth = monthStr == dep.startMonth
                val state = if (isFirstMonth) {
                    // Учитываем проценты за первый месяц с initialAmount
                    val monthlyRate = dep.annualRatePercent / 12.0 / 100.0
                    val interest = (dep.initialAmountMinor * monthlyRate).roundToLong()
                    val oneTimeOps = allOps
                        .filter { it.depositId == dep.id && it.month == monthStr }
                        .sumOf { it.amountMinor }
                    val endBal = dep.initialAmountMinor + interest + oneTimeOps
                    DepositMonthState(
                        month = month,
                        startBalance = dep.initialAmountMinor,
                        interest = interest,
                        contribution = 0L,
                        withdrawal = 0L,
                        oneTimeOps = oneTimeOps,
                        endBalance = endBal,
                        active = true
                    )
                } else {
                    depositStateForMonth(dep, month, allOps, prevEnd)
                }

                portfolioStart += state.startBalance
                portfolioEnd += state.endBalance
                depositPrevBalance[dep.id] = state.endBalance
            }

            // Прирост портфеля = конец − начало (учитывая проценты и все движения)
            // Это и есть "инвестиционный доход" месяца, который тоже идёт в баланс
            portfolioChange = portfolioEnd - portfolioStart

            // 3. Итоговый баланс
            // Баланс = предыдущий + (доходы − расходы) + (прирост портфеля)
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

    // Полный расчёт по всем вкладам (для отдельного экрана вкладов)
    fun calculateDepositTimeline(
        deposit: ForecastDepositEntity,
        allOps: List<ForecastDepositOpEntity>
    ): List<DepositMonthState> {
        val start = YearMonth.parse(deposit.startMonth)
        val end = YearMonth.parse(deposit.endMonth)
        val result = mutableListOf<DepositMonthState>()
        var prevEnd = 0L
        var current = start
        while (current <= end) {
            val isFirst = current == start
            val state = if (isFirst) {
                val monthlyRate = deposit.annualRatePercent / 12.0 / 100.0
                val interest = (deposit.initialAmountMinor * monthlyRate).roundToLong()
                val oneTime = allOps.filter { it.depositId == deposit.id && it.month == current.toString() }
                    .sumOf { it.amountMinor }
                DepositMonthState(
                    month = current,
                    startBalance = deposit.initialAmountMinor,
                    interest = interest,
                    contribution = 0L,
                    withdrawal = 0L,
                    oneTimeOps = oneTime,
                    endBalance = deposit.initialAmountMinor + interest + oneTime,
                    active = true
                )
            } else {
                depositStateForMonth(deposit, current, allOps, prevEnd)
            }
            result.add(state)
            prevEnd = state.endBalance
            current = current.plusMonths(1)
        }
        return result
    }
}
