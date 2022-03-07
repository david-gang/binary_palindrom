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
val LONG_TEN_EXPONENTS = LongArray(max10) {
    LongMath.pow(10, it)
}
val bigIntegerDigits = (0..9).map { it.toBigInteger() }.toTypedArray()
val BIG_TEN_EXPONENTS = (0..100).map { BigInteger.TEN.pow(it) }.toTypedArray()

// when we want to run more than 10 minutes we need to make it bigger but this is not important now
const val EXPONENT_LIMIT = 50
val bigNineDifferences = getBigNineDifferences(BIG_TEN_EXPONENTS)

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
        // first find all long values
        for (i in 1 until 5) {
            launch(Dispatchers.Default) {
                checkAllPalindromesForDecimalLength(i, longMask)
            }
        }
        val counter = AtomicInteger(5)
        for (i in 5..29) {
            launch(launcherDispatcher) {
                checkAllBigNumberPalindromesForDecimalLength(i, counter)
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

suspend fun checkAllBigNumberPalindromesForDecimalLength(length: Int, counter: AtomicInteger) {
    val edgeSize = 2
    val nine = bigNineDifferences[EXPONENT_LIMIT * edgeSize + length - edgeSize]
    supervisorScope {
        for (i in 1..9 step 2) {
            val highI = BIG_TEN_EXPONENTS[length - 1] * bigIntegerDigits[i]
            val lowI = bigIntegerDigits[i]
            for (j in 0..9) {
                val ticket = counter.getAndIncrement()
                launch(Dispatchers.Default) {
                    try {
                        val high = highI + bigIntegerDigits[j] * BIG_TEN_EXPONENTS[length - 2]
                        val low = BigInteger.TEN * bigIntegerDigits[j] + lowI
                        checkAllBigNumberPalindromesForDecimalLengthRecursive(
                            high = high,
                            low = low,
                            nine = nine,
                            edgeSize = 2,
                            length = length,
                            ticket = ticket,
                            primeBitsTillNow = 0,
                            numLengthIn = 0
                        )
                    } finally {
                        sharedFlow.emit(ticket to "")
                    }

                }
            }
        }
    }
}


fun checkAllBigNumberPalindromesForDecimalLengthRecursive(
    high: BigInteger,
    low: BigInteger,
    nine: BigInteger,
    edgeSize: Int,
    length: Int,
    ticket: Int,
    primeBitsTillNow: Int,
    numLengthIn: Int
) {
    val remaining = length - 2 * edgeSize
    val num = high + low
    if (remaining == 0 || remaining == -1) {
        if (isBinaryPalindrome(num, primeBitsTillNow)) {
            sharedFlow.tryEmit(ticket to num.toString())
        }
        return
    }

    val numLength = if (numLengthIn == 0) {
        num.bitLength()
    } else {
        numLengthIn
    }
    var newNumLengthIn = 0

    val biggestNum = nine + num


    var leftSide = primeBitsTillNow

    if (!biggestNum.testBit(numLength)) {
        newNumLengthIn = numLengthIn
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

    val newNine = if (remaining > 2) {
        bigNineDifferences[EXPONENT_LIMIT * (edgeSize + 1) + length - edgeSize - 1]
    } else {
        nine
    }
    checkAllBigNumberPalindromesForDecimalLengthRecursive(
        high = high,
        low = low,
        nine = newNine,
        edgeSize = edgeSize + 1,
        length = length,
        ticket = ticket,
        primeBitsTillNow = leftSide,
        numLengthIn = newNumLengthIn
    )
    if (remaining == 1) {
        var lowCounter = low
        for (i in 1..9) {
            lowCounter += BIG_TEN_EXPONENTS[edgeSize]
            checkAllBigNumberPalindromesForDecimalLengthRecursive(
                high = high,
                low = lowCounter,
                nine = newNine,
                edgeSize = edgeSize + 1,
                length = length,
                ticket = ticket,
                primeBitsTillNow = leftSide,
                numLengthIn = newNumLengthIn
            )
        }
    } else {
        var lowCounter = low
        var highCounter = high
        for (i in 1..9) {
            lowCounter += BIG_TEN_EXPONENTS[edgeSize]
            highCounter += BIG_TEN_EXPONENTS[length - edgeSize - 1]
            checkAllBigNumberPalindromesForDecimalLengthRecursive(
                high = highCounter,
                low = lowCounter,
                nine = newNine,
                edgeSize = edgeSize + 1,
                length = length,
                ticket = ticket,
                primeBitsTillNow = leftSide,
                numLengthIn = newNumLengthIn
            )
        }
    }

}

/**
 * checks for all the palindromes
 */
suspend fun checkAllPalindromesForDecimalLength(length: Int, longMask: LongArray) {
    val biggestNum = LONG_TEN_EXPONENTS[length + 1]
    val maxBitIndex = Long.SIZE_BITS - biggestNum.countLeadingZeroBits() - 1
    val n = (length + 1) / 2


    val possibleDigits: Array<Set<Long>> = Array(n) { ALL_DIGITS }.apply {
        this[0] = FIRST_DIGITS
    }

    for (product in Sets.cartesianProduct(*possibleDigits)) {
        var num = 0L
        // construct lower half of the number
        for (i in 0 until n) {
            num += LONG_TEN_EXPONENTS[i] * product[i]
        }

        // construct higher half of the number
        val highest = length - n - 1
        for (i in highest downTo 0) {
            num += LONG_TEN_EXPONENTS[n + (highest - i)] * product[i]
        }

        if (isBinaryPalindrome(num, maxBitIndex, longMask)) {
            sharedFlow.emit(length to num.toString())
        }
    }
    sharedFlow.emit(length to "")
}

fun isBinaryPalindrome(n: BigInteger, primeBitsTillNow: Int): Boolean {
    val highestBitIndex = n.bitLength() - 1
    val numberOfPalindromicBits = (highestBitIndex - 1) / 2
    for (i in primeBitsTillNow..numberOfPalindromicBits) {
        val lowBit = n.testBit(i)
        val highBit = n.testBit(highestBitIndex - i)
        if (lowBit != highBit) {
            return false
        }
    }
    return true
}

fun isBinaryPalindrome(n: Long, maxBitIndex: Int, longMask: LongArray): Boolean {
    var highestNonZeroBitIndex = maxBitIndex
    while (n and longMask[highestNonZeroBitIndex] == 0L) {
        highestNonZeroBitIndex--
    }
    val numberOfPalindromicBits = (highestNonZeroBitIndex - 1) / 2
    for (i in 1..numberOfPalindromicBits) {
        val lowBit = (n shr i) and 1L
        val highBit = (n shr (highestNonZeroBitIndex - i)) and 1L
        if (lowBit != highBit) {
            return false
        }
    }
    return true
}