package topics

fun main(){
    val items = listOf("hello", "world", "!")
    for (item in items)
        println(item)

    var index = 0
    while(index < items.size){
        println(items[index])
        index++
    }

    for(index in 0 until items.size)
        println(items[index])
}