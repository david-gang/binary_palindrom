import com.google.common.collect.Sets
import com.google.common.math.LongMath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10

val FIRST_DIGITS = setOf(1L, 3L, 5L, 7L, 9L)
val ALL_DIGITS = setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)

val launcherDispatcher = Dispatchers.IO.limitedParallelism(1)

fun createCollector(): (Pair<Int, String>) -> Unit {
    var currentIndex = 1
    var buffer = mutableListOf<Pair<Int, String>>()
    return fun(p: Pair<Int, String>) {
        if (p.second.isEmpty() && currentIndex == p.first) {
            currentIndex++
            while (buffer.isNotEmpty()) {
                var hasNext = false
                val newBuffer = mutableListOf<Pair<Int, String>>()
                for (b in buffer) {
                    if (b.first == currentIndex) {
                        if (b.second.isEmpty()) {
                            hasNext = true
                        } else {
                            println(b.second)
                        }
                    } else {
                        newBuffer.add(b)
                    }
                }
                buffer = newBuffer
                if (hasNext) {
                    currentIndex++
                } else {
                    break
                }
            }
            System.out.flush()
        } else if (p.first > currentIndex) {
            buffer.add(p)
        } else {
            println(p.second)
            System.out.flush()
        }
    }
}

val sharedFlow = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 128)
val max10 = log10(Long.MAX_VALUE.toDouble()).toInt()
val tenExponents = LongArray(max10) {
    LongMath.pow(10, it)
}
val bigIntegerDigits = (0..9).map { it.toBigInteger() }.toTypedArray()
val bigtenExponents = (0..100).map { BigInteger.TEN.pow(it) }.toTypedArray()

// when we want to run more than 10 minutes we need to make it bigger but this is not important now
const val EXPONENT_LIMIT = 50
val bigNineDifferences = getBigNineDifferences(bigtenExponents)

suspend fun main() {

    val collector = createCollector()
    sharedFlow.onEach {
        collector.invoke(it)
    }.launchIn(CoroutineScope(Dispatchers.IO))
    val before = System.currentTimeMillis()
    val longMask = LongArray(Long.SIZE_BITS) {
        1L shl it
    }


    // 0 is palindrome 0 in every base
    println(0)
    supervisorScope {
        for (i in 1 until 5) {
            launch(Dispatchers.Default) {
                checkAllPalindromesForMagnitude(i, tenExponents, longMask)
            }
        }
        val counter = AtomicInteger(5)
        for (i in 5 until tenExponents.size + 12) {
            launch(launcherDispatcher) {
                checkAllPalindromesForMagnitudeBig(i, counter)
            }
        }
    }

    val after = System.currentTimeMillis()
    val time = (after - before) / 1000
    println("time $time")

}


fun getBigNineDifferences(bigtenExponents: Array<BigInteger>): Array<BigInteger> {
    val arr = arrayOfNulls<BigInteger>(EXPONENT_LIMIT * EXPONENT_LIMIT)
    for (i in 0 until EXPONENT_LIMIT) {
        for (j in 0..i) {
            arr[EXPONENT_LIMIT * i + j] = BigInteger.ZERO
        }
        for (j in i + 1 until EXPONENT_LIMIT) {
            arr[EXPONENT_LIMIT * i + j] = bigtenExponents[j] - bigtenExponents[i]
        }
    }
    @Suppress("UNCHECKED_CAST")
    return arr as Array<BigInteger>

}

suspend fun checkAllPalindromesForMagnitudeBig(magnitude: Int, counter: AtomicInteger) {
    val edgeSize = 2
    val nine = bigNineDifferences[EXPONENT_LIMIT * edgeSize + magnitude - edgeSize]
    supervisorScope {
        for (i in 1..9 step 2) {
            val highI = bigtenExponents[magnitude - 1] * bigIntegerDigits[i]
            val lowI = bigIntegerDigits[i]
            for (j in 0..9) {
                launch(Dispatchers.Default) {
                    val ticket = counter.getAndIncrement()
                    try {
                        val high = highI + bigIntegerDigits[j] * bigtenExponents[magnitude - 2]
                        val low = BigInteger.TEN * bigIntegerDigits[j] + lowI
                        checkAllPalindromesForMagnitudeBigRecursive(high, low, nine, 2, magnitude, ticket)
                    } finally {
                        sharedFlow.emit(ticket to "")
                    }

                }
            }
        }
    }
}


fun checkAllPalindromesForMagnitudeBigRecursive(
    high: BigInteger,
    low: BigInteger,
    nine: BigInteger,
    edgeSize: Int,
    magnitude: Int,
    ticket: Int
) {
    val remaining = magnitude - 2 * edgeSize
    val num = high + low
    if (remaining <= 0) {
        if (isBinaryPalindrome(num)) {
            sharedFlow.tryEmit(ticket to num.toString())
        }
        return
    }

    val numLength = num.bitLength()
    val biggestNum = nine + num


    if (!biggestNum.testBit(numLength)) {
        var leftSide = 0
        val numHighestIndex = numLength - 1
        while (leftSide < edgeSize && num.testBit(numHighestIndex - leftSide) == biggestNum.testBit(numHighestIndex - leftSide)) {
            leftSide++
        }
        for (i in 1 until leftSide) {
            if (num.testBit(numHighestIndex - i) != num.testBit(i)) {
                return
            }
        }
    }

    val newNine = bigNineDifferences[EXPONENT_LIMIT * (edgeSize + 1) + magnitude - edgeSize - 1]
    checkAllPalindromesForMagnitudeBigRecursive(high, low, newNine, edgeSize + 1, magnitude, ticket)
    if (remaining == 1) {
        var lowCounter = low
        for (i in 1..9) {
            lowCounter += bigtenExponents[edgeSize]
            checkAllPalindromesForMagnitudeBigRecursive(high, lowCounter, newNine, edgeSize + 1, magnitude, ticket)
        }
    } else {
        var lowCounter = low
        var highCounter = high
        for (i in 1..9) {
            lowCounter += bigtenExponents[edgeSize]
            highCounter += bigtenExponents[magnitude - edgeSize - 1]
            checkAllPalindromesForMagnitudeBigRecursive(
                highCounter,
                lowCounter,
                newNine,
                edgeSize + 1,
                magnitude,
                ticket
            )
        }
    }

}

suspend fun checkAllPalindromesForMagnitude(magnitude: Int, tenExponents: LongArray, longMask: LongArray) {
    val biggestNum = tenExponents[magnitude + 1]
    val maxBitIndex = Long.SIZE_BITS - biggestNum.countLeadingZeroBits() - 1
    val n = (magnitude + 1) / 2
    val isOdd = magnitude % 2 == 1
    val sets = arrayOfNulls<Set<Long>>(n)
    sets[0] = FIRST_DIGITS
    for (i in 1 until n) {
        sets[i] = ALL_DIGITS
    }

    for (product in Sets.cartesianProduct(*sets)) {
        var num = 0L
        for (i in 0 until n) {
            num += tenExponents[i] * product[i]
        }
        val highest = if (isOdd) {
            n - 2
        } else {
            n - 1
        }
        for (i in highest downTo 0) {
            num += tenExponents[n + (highest - i)] * product[i]
        }
        if (isBinaryPalindrome(num, maxBitIndex, longMask)) {
            sharedFlow.emit(magnitude to num.toString())
        }
    }
    sharedFlow.emit(magnitude to "")
}

fun isBinaryPalindrome(n: BigInteger): Boolean {
    val highestBitIndex = n.bitLength() - 1
    val loops = (highestBitIndex + 1) / 2
    for (i in 1 until loops) {
        val lowBit = n.testBit(i)
        val highBit = n.testBit(highestBitIndex - i)
        if (lowBit != highBit) {
            return false
        }
    }
    return true
}

fun isBinaryPalindrome(n: Long, maxBitIndex: Int, longMask: LongArray): Boolean {
    var highestBitIndex = maxBitIndex
    while (n and longMask[highestBitIndex] == 0L) {
        highestBitIndex--
    }
    val loops = (highestBitIndex + 1) / 2
    for (i in 1 until loops) {
        val lowBit = (n shr i) and 1L
        val highBit = (n shr (highestBitIndex - i)) and 1L
        if (lowBit != highBit) {
            return false
        }
    }
    return true
}