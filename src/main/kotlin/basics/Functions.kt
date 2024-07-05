package topics

fun main() {
    println(sum(1,2))
    println(showString(10))
}

fun sum(a: Int, b: Int) = a + b
fun showString(x: Int): String = "String is $x"
fun defaultArgs(x: Int = 1, y: Int = 2) {
    println(x + y)
}