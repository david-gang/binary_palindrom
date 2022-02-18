import com.google.common.collect.Sets
import com.google.common.math.LongMath
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10

val FIRST_DIGITS= setOf(1L, 3L, 5L, 7L, 9L)
val ALL_DIGITS = setOf(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L)
val launcherDispatcher = Dispatchers.IO.limitedParallelism(1)

fun createCollector(): (Pair<Int, String>) -> Unit {
    var currentIndex = 1
    var buffer = mutableListOf<Pair<Int,String>>()
    return fun (p: Pair<Int,String>) {
        if(p.second.isEmpty() && currentIndex == p.first){
            currentIndex++
            while(buffer.isNotEmpty()) {
                var hasNext = false
                val newBuffer = mutableListOf<Pair<Int,String>>()
                for(b in buffer) {
                    if(b.first == currentIndex){
                        if(b.second.isEmpty()) {
                            hasNext = true
                        }
                        else {
                            println(b.second)
                        }
                    }
                    else {
                        newBuffer.add(b)
                    }
                }
                buffer = newBuffer
                if(hasNext) {
                    currentIndex++
                }
                else {
                    break
                }
            }
            System.out.flush()
        }
        else if(p.first > currentIndex) {
            buffer.add(p)
        }
        else {
            println(p.second)
            System.out.flush()
        }
    }
}
val sharedFlow = MutableSharedFlow<Pair<Int, String>>(extraBufferCapacity = 128)
val max10 = log10(Long.MAX_VALUE.toDouble()).toInt()
val tenExponents = LongArray(max10){
    LongMath.pow(10, it)
}

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
        for(i in 1 until tenExponents.size - 1) {
            println("launching $i")
            launch(Dispatchers.Default) {
                checkAllPalindromesForMagnitude(i, tenExponents, longMask)
            }
        }
        val counter = AtomicInteger(tenExponents.size - 1)
        print("atomic "+counter.get())
        withContext(launcherDispatcher) {
            for (i in tenExponents.size - 1 until tenExponents.size + 11) {
                checkAllPalindromesForMagnitudeBig(i,counter)
            }
        }
    }

    val after = System.currentTimeMillis()
    val time = (after - before)/1000
    println("time $time")

}


val bigIntegerDigits = (0..9).map { it.toBigInteger() }.toTypedArray()
val bigtenExponents = (0..100).map { BigInteger.TEN.pow(it) }.toTypedArray()
val bigNineExponents = bigtenExponents.map { it * 9.toBigInteger() }.toTypedArray()
suspend fun checkAllPalindromesForMagnitudeBig(magnitude: Int, counter: AtomicInteger) {
    println("launching big $magnitude")
    val edgeSize = 2
    val nine = bigtenExponents[magnitude - edgeSize] - bigtenExponents[edgeSize]
    supervisorScope {
        for( i in 1..9 step 2) {
            val highI = bigtenExponents[magnitude -1] * bigIntegerDigits[i]
            val lowI = bigIntegerDigits[i]
            for(j in 0..9) {
                launch(Dispatchers.Default) {
                    val high =  highI + bigIntegerDigits[j] * bigtenExponents[magnitude -2]
                    val low = BigInteger.TEN * bigIntegerDigits[j] + lowI
                    checkAllPalindromesForMagnitudeBigRecursive(high, low, nine, 2, magnitude)
                }
            }
    }
    }

    println("ended $magnitude")
}


fun checkAllPalindromesForMagnitudeBigRecursive(high: BigInteger, low: BigInteger, nine:BigInteger, edgeSize: Int, magnitude: Int) {
    val rightSide = edgeSize
    val remaining = magnitude - 2*edgeSize
    val num = high + low
    val numLength = num.bitLength()
    if(remaining <= 0) {
        if(isBinaryPalindrome(num)) {
            println("found $num")
        }
        return
    }
    val biggestNum = nine + num
    val biggestLength = biggestNum.bitLength()


    if(biggestLength == numLength) {
        var leftSide = 0
        val numHighestIndex = numLength - 1
        while(leftSide < rightSide && num.testBit(numHighestIndex -  leftSide) == biggestNum.testBit(numHighestIndex - leftSide))
        {
            leftSide ++
        }
        for(i in 1 until leftSide) {
            if(num.testBit(numHighestIndex - i)!= num.testBit(i)) {
                return
            }
        }
    }

    val newNine = bigtenExponents[magnitude - edgeSize - 1] - bigtenExponents[edgeSize + 1]
    if(remaining == 1) {
        for(i in 0..9) {
            checkAllPalindromesForMagnitudeBigRecursive(high, low + (bigIntegerDigits[i]* bigtenExponents[edgeSize]), newNine, edgeSize + 1, magnitude)
        }
    }
    else {
        for(i in 0..9) {
            checkAllPalindromesForMagnitudeBigRecursive(high+ bigtenExponents[magnitude - edgeSize -1]*bigIntegerDigits[i], low + bigIntegerDigits[i]* bigtenExponents[edgeSize], newNine, edgeSize + 1, magnitude)
        }
    }

}

suspend fun checkAllPalindromesForMagnitude(magnitude: Int, tenExponents: LongArray, longMask: LongArray) {
    val biggestNum = tenExponents[magnitude + 1]
    val maxBitIndex = Long.SIZE_BITS - biggestNum.countLeadingZeroBits() - 1
    val n = (magnitude +1)/2
    val isOdd = magnitude %2 == 1
    val sets = arrayOfNulls<Set<Long>>(n)
    sets[0] = FIRST_DIGITS
    for (i in 1 until n) {
        sets[i] = ALL_DIGITS
    }

    for (product in Sets.cartesianProduct(*sets)) {
        var num = 0L
        for(i in 0 until n) {
            num += tenExponents[i] * product[i]
        }
        val highest = if(isOdd) {
            n - 2
        }
        else {
            n - 1
        }
        for ( i in highest downTo 0) {
            num += tenExponents[n + (highest - i)] * product[i]
        }
        if(isBinaryPalindrome(num, maxBitIndex, longMask)) {
            sharedFlow.emit(magnitude to num.toString())
        }
    }
    sharedFlow.emit(magnitude to "")
    println("ended $magnitude")
}

fun isBinaryPalindrome(n:BigInteger): Boolean {
    val highestBitIndex = n.bitLength() - 1
    val loops = (highestBitIndex + 1)/2
    for (i in 1 until  loops){
        val lowBit = n.testBit(i)
        val highBit = n.testBit(highestBitIndex-i)
        if(lowBit != highBit) {
            return false
        }
    }
    return true
}

fun isBinaryPalindrome(n:Long, maxBitIndex:Int, longMask: LongArray): Boolean {
    var highestBitIndex = maxBitIndex
    while(n and longMask[highestBitIndex] == 0L) {
        highestBitIndex--
    }
    val loops = (highestBitIndex + 1)/2
    for (i in 1 until  loops){
        val lowBit = (n shr i) and 1L
        val highBit = (n shr (highestBitIndex-i)) and 1L
        if(lowBit != highBit) {
            return false
        }
    }
    return true
}