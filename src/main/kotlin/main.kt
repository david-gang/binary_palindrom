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
val FIRST_DIGITS_STRING = longArrayOf(1, 3, 5, 7, 9)
val ALL_DIGITS_STRING = longArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
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
        for (i in tenExponents.size - 1 until tenExponents.size + 5) {
            withContext(launcherDispatcher) {
                checkAllPalindromesForMagnitudeBig(i,counter)
            }
        }
    }

    val after = System.currentTimeMillis()
    val time = (after - before)/1000
    println("time $time")

}

fun cartesianLongProduct(vararg lists: LongArray): Sequence<LongArray>  = sequence{
    var index = IntArray(lists.size)
    val lastIndex = lists.size -1
    while(true) {
        val charPerm = LongArray(lists.size) {
            lists[it][index[it]]
        }
        yield(charPerm)

        var i = lastIndex
        index[lastIndex]++
        while(index[i] == lists[i].size) {
            index[i] =0
            i--
            if(i== -1) {
                return@sequence
            }
            index[i]++
        }
    }


}
private class BatchConstants(val batchFactor:BigInteger, val big: BigInteger, val small: BigInteger)

private fun createBatchConstant(n:Int, permutation: LongArray): BatchConstants {
    val tenDigits = createTenDigits(n)

    var small = BigInteger.ONE * BigInteger.valueOf(permutation[0])
    for(i in 1..permutation.lastIndex) {
        small += BigInteger.valueOf(permutation[i]) * tenDigits[i]
    }
    var big = BigInteger.valueOf(permutation[0]) * tenDigits.last()
    for(i in 1 .. permutation.lastIndex) {
        big += BigInteger.valueOf(permutation[i]) * tenDigits[tenDigits.lastIndex - i]
    }
    return BatchConstants(small = small, big = big, batchFactor = tenDigits[permutation.size])

}

private fun createTenDigits(n: Int): Array<BigInteger> {
    val tenDigits = arrayOfNulls<BigInteger>(n)
    tenDigits[0] = BigInteger.ONE
    tenDigits[1] = BigInteger.TEN
    for (i in 2 until tenDigits.size) {
        tenDigits[i] = tenDigits[i-1]!! * BigInteger.TEN
    }
    return tenDigits as Array<BigInteger>
}

private val batchList = cartesianLongProduct(ALL_DIGITS_STRING,ALL_DIGITS_STRING,ALL_DIGITS_STRING,ALL_DIGITS_STRING, ALL_DIGITS_STRING, ALL_DIGITS_STRING).toList()
suspend fun makePalindromBatch(ticketNum:Int, magnitude: Int, permutation: LongArray) {
   val batchConstants = createBatchConstant(magnitude, permutation)

    val batchItemSize = batchList[0].size
    val isOdd = magnitude %2 == 1
    val highest = if(isOdd) {
        batchItemSize - 2
    }
    else {
        batchItemSize - 1
    }
    val baseNum = batchConstants.big + batchConstants.small

    for(b in batchList) {
        var batchNum = 0L
        for (i in b.indices) {
            batchNum += b[i] * tenExponents[i]
        }

        for(i in 0 .. highest) {
            batchNum += b[highest - i] * tenExponents[i + b.size]
        }
        val bigNum = BigInteger.valueOf(batchNum) * batchConstants.batchFactor + baseNum
        if(isBinaryPalindrome(bigNum)) {
            sharedFlow.emit(ticketNum to bigNum.toString())
        }

    }
    sharedFlow.emit(ticketNum to "")

}
suspend fun checkAllPalindromesForMagnitudeBig(magnitude: Int, counter: AtomicInteger) {
    println("launching big $magnitude")
    val n = (magnitude +1)/2
    val sets = Array(n - batchList[0].size) {
        if(it == 0){
            FIRST_DIGITS_STRING
        }
        else {
            ALL_DIGITS_STRING
        }
    }

    coroutineScope {
        for (product in cartesianLongProduct(*sets)) {
            launch(Dispatchers.Default) {
                makePalindromBatch(counter.getAndIncrement(), magnitude, product)
            }

        }
    }


    println("ended $magnitude")
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