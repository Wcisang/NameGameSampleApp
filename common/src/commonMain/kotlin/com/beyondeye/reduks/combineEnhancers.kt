package com.beyondeye.reduks

/**
 * combine multiple store enhancers to a single one
 * Created by daely on 8/24/2016.
 */


//TODO refactor compose (used also for middleware) to single place
private  fun <T> compose(functions: List<(T) -> T>): (T) -> T {
    return { x -> functions.foldRight(x, { f, composed -> f(composed) }) }
}

fun <S> combineEnhancers(vararg enhancers: StoreEnhancer<S>): StoreEnhancer<S> =
        { storeCreator ->
            compose(enhancers.map { it })(storeCreator)
        }

