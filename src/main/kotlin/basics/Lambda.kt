package topics

fun main() {
    val numbers = listOf(1,1,3,2,5)
    numbers.forEach{ e -> println(e) }
    println(numbers.map { e -> e * 2 })
    println(numbers.filter { e -> e == 1 })
}