package com.sandwich.features.createOrder

import com.sandwich.features.Order
import com.sandwich.features.OrderErrorCode.*
import com.sandwich.features.orderError

// ══════════════════════════════════════════════════════════════
//  🔴 WRITE фаза: зберегти результат + сформувати відповідь
//  Помилкові рішення → OrderException (ловить StatusPages)
// ══════════════════════════════════════════════════════════════

fun ProduceCreateOrderOutput(
    storeOrder: suspend (Order) -> Unit
): suspend (CreateOrderDecision) -> CreateOrderResponse = { decision ->
    when (decision) {
        is CreateOrderDecision.Created -> {
            storeOrder(decision.order)
            CreateOrderResponse(orderId = decision.order.id, total = decision.order.total)
        }
        is CreateOrderDecision.BlankName ->
            orderError(BLANK_NAME, decision.message)
        is CreateOrderDecision.EmptyOrder ->
            orderError(EMPTY_ORDER, decision.message)
        is CreateOrderDecision.TooManyItems ->
            orderError(TOO_MANY_ITEMS, "Максимум ${decision.max} сендвічів")
        is CreateOrderDecision.UnknownSandwich ->
            orderError(UNKNOWN_SANDWICH, "Невідомі сендвічі: ${decision.ids}")
        is CreateOrderDecision.UnknownExtras ->
            orderError(UNKNOWN_EXTRAS, "Невідомі додатки: ${decision.ids}")
        is CreateOrderDecision.TooManyExtras ->
            orderError(TOO_MANY_EXTRAS, "Макс ${decision.max} додатків для ${decision.sandwichId}")
    }
}
