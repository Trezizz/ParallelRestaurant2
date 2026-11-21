import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.random.Random

data class Order(
    val id: String,
    val dishName: String,
    var cookingProgress: Int = 0,
    var status: String = "Ожидает"
)

data class DishResult(
    val orderId: String,
    val dishName: String,
    val qualityScore: Double
)


suspend fun prepareIngredient(ingredientName: String): String {
    return withContext(Dispatchers.Default) {
        delay(1500)
        val statuses = listOf("Нарезанный лук", "Расплавленное масло", "Взбитые сливки")
        val result = statuses.random()
        println(" [Ингредиент] $ingredientName подготовлен: $result")
        result
    }
}


suspend fun cookOrder(order: Order) = coroutineScope {
    order.status = "Готовится"


    launch {

        try {
            while (order.cookingProgress < 100 && isActive) {
                delay(500)
                order.cookingProgress += 10
                println(" [Готовка] ${order.dishName}: прогресс ${order.cookingProgress}%")


                if (order.cookingProgress == 30 && order.id == "ORD-2") {
                    throw RuntimeException("Повар уронил тарелку!")
                }
            }
        } catch (e: Exception) {
            println(" [ОШИБКА] Заказ ${order.id} (${order.dishName}) провален: ${e.message}")
            order.status = "Ошибка"

        }
    }

    launch {

        delay(2000)
        if (order.status != "Ошибка") {
            println(" [Температура] ${order.dishName}: Температура в норме")
        }
    }

    launch {

        while (order.cookingProgress < 100 && isActive) {
            if (order.cookingProgress >= 80) {
                println(" [Мониторинг] ВНИМАНИЕ! Блюдо ${order.dishName} почти готово, пора на финишную обработку")
                break
            }
            delay(100)
        }
    }
}


suspend fun checkDishQuality(orderId: String): Deferred<DishResult> {
    return CoroutineScope(Dispatchers.Default).async {
        delay(3000)
        val score = Random.nextDouble(3.0, 5.0)
        DishResult(orderId, "Блюдо", score)
    }
}


suspend fun cancelOrder(order: Order, job: Job) {
    delay(2000)
    job.cancel()
    println(" [Отмена] Заказ ${order.id} (${order.dishName}) снято с приготовления")
}


suspend fun testChannels() {
    println("\n--- Тест Каналов ---")
    val channel = Channel<DishResult>()


    val producer = CoroutineScope(Dispatchers.Default).launch {
        repeat(5) { i ->
            delay(500)
            val result = DishResult("CH-${i+1}", "Блюдо ${i+1}", Random.nextDouble(3.0, 5.0))
            println(" [Кухня] Отправлено блюдо: ${result.dishName}")
            channel.send(result)
        }
        channel.close()
        println(" [Кухня] Все заказы переданы, канал закрыт.")
    }


    val consumer = CoroutineScope(Dispatchers.Default).launch {
        for (result in channel) {
            println(" [Раздача] Получено: ${result.dishName} (Оценка: ${String.format("%.2f", result.qualityScore)})")
        }
    }

    producer.join()
    consumer.join()
}


fun main() = runBlocking {
    println("--- Старт смены в ресторане 'Параллель' ---")


    prepareIngredient("Сыр")


    println("\n--- Тест Приготовления и Обработки ошибок ---")
    val order1 = Order("ORD-1", "Паста")
    val order2 = Order("ORD-2", "Стейк")

    val job1 = launch { cookOrder(order1) }
    val job2 = launch { cookOrder(order2) }

    job1.join()

    // ТЕСТ 4: await()
    if (order1.status != "Ошибка" && order1.cookingProgress == 100) {
        println(" [Дегустация] Шеф пробует ${order1.dishName}...")
        val qualityDeferred = checkDishQuality(order1.id)
        val dishResult = qualityDeferred.await()
        println(" [Дегустация] Итоговая оценка: ${String.format("%.2f", dishResult.qualityScore)}")
    }


    println("\n--- Тест Отмены ---")
    val order3 = Order("ORD-3", "Суп")
    val job3 = launch { cookOrder(order3) }
    cancelOrder(order3, job3)
    job3.join()


    testChannels()

    println("\n--- Смена окончена ---")
}