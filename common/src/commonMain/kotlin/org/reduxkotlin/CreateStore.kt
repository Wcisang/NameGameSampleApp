package org.reduxkotlin

import org.reduxkotlin.utils.isPlainObject

/**
 * Creates a Redux store that holds the state tree.
 * The only way to change the data in the store is to call `dispatch()` on it.
 *
 * There should only be a single store in your app. To specify how different
 * parts of the state tree respond to actions, you may combine several reducers
 * into a single reducer fun  by using `combineReducers`.
 *
 * @param {Reducer} [reducer] A fun  that returns the next state tree, given
 * the current state tree and the action to handle.
 *
 * @param {Any} [preloadedState] The initial state. You may optionally specify it
 * to hydrate the state from the server in universal apps, or to restore a
 * previously serialized user session.
 *
 * @param {Enhancer} [enhancer] The store enhancer. You may optionally specify it
 * to enhance the store with third-party capabilities such as middleware,
 * time travel, persistence, etc. The only store enhancer that ships with Redux
 * is `applyMiddleware()`.
 *
 * @returns {Store} A Redux store that lets you read the state, dispatch actions
 * and subscribe to changes.
 */
fun <S> createStore(reducer: Reducer<S>, preloadedState: S, enhancer: StoreEnhancer<S>? = null): Store<S> {


    if (enhancer != null) {
        return enhancer { reducer, initialState -> createStore(reducer, initialState) }(reducer, preloadedState)
    }


    var currentReducer = reducer
    var currentState = preloadedState
    var currentListeners = mutableListOf<()->Unit>()
    var nextListeners = currentListeners
    var isDispatching = false

    /**
     * This makes a shallow copy of currentListeners so we can use
     * nextListeners as a temporary list while dispatching.
     *
     * This prevents any bugs around consumers calling
     * subscribe/unsubscribe in the middle of a dispatch.
     */
    fun ensureCanMutateNextListeners() {
        if (nextListeners === currentListeners) {
            nextListeners = currentListeners.toMutableList()
        }
    }

    /**
     * Reads the state tree managed by the store.
     *
     * @returns {S} The current state tree of your application.
     */
    fun getState(): S {
        if (isDispatching) {
            throw Exception(
                    """You may not call store.getState() while the reducer is executing.
                            The reducer has already received the state as an argument.
                            Pass it down from the top reducer instead of reading it from the store."""
            )
        }

        return currentState
    }

    /**
     * Adds a change listener. It will be called any time an action is dispatched,
     * and some part of the state tree may potentially have changed. You may then
     * call `getState()` to read the current state tree inside the callback.
     *
     * You may call `dispatch()` from a change listener, with the following
     * caveats:
     *
     * 1. The subscriptions are snapshotted just before every `dispatch()` call.
     * If you subscribe or unsubscribe while the listeners are being invoked, this
     * will not have any effect on the `dispatch()` that is currently in progress.
     * However, the next `dispatch()` call, whether nested or not, will use a more
     * recent snapshot of the subscription list.
     *
     * 2. The listener should not expect to see all state changes, as the state
     * might have been updated multiple times during a nested `dispatch()` before
     * the listener is called. It is, however, guaranteed that all subscribers
     * registered before the `dispatch()` started will be called with the latest
     * state by the time it exits.
     *
     * @param {StoreSubscriber} [listener] A callback to be invoked on every dispatch.
     * @returns {StoreSubscription} A fun  to remove this change listener.
     */
    fun subscribe(listener: StoreSubscriber): StoreSubscription {
        if (isDispatching) {
            throw Exception(
                    """You may not call store.subscribe() while the reducer is executing.
                            If you would like to be notified after the store has been updated, subscribe from a
                            component and invoke store.getState() in the callback to access the latest state.
                            See https://redux.js.org/api-reference/store#subscribe(listener) for more details."""
            )
        }

        var isSubscribed = true

        ensureCanMutateNextListeners()
        nextListeners.add(listener)

        return {
            if (!isSubscribed) {
                Unit
            }

            if (isDispatching) {
                throw Exception(
                        """You may not unsubscribe from a store listener while the reducer is executing.
                                'See https://redux.js.org/api-reference/store#subscribe(listener) for more details."""
                )
            }

            isSubscribed = false

            ensureCanMutateNextListeners()
            val index = nextListeners.indexOf(listener)
            nextListeners.removeAt(index)
        }
    }

    /**
     * Dispatches an action. It is the only way to trigger a state change.
     *
     * The `reducer` function, used to create the store, will be called with the
     * current state tree and the given `action`. Its return value will
     * be considered the **next** state of the tree, and the change listeners
     * will be notified.
     *
     * The base implementation only supports plain object actions. If you want to
     * dispatch a something else, such as a function or 'thunk' you need to
     * wrap your store creating function into the corresponding middleware. For
     * example, see the documentation for the `redux-thunk` package. Even the
     * middleware will eventually dispatch plain object actions using this method.
     *
     * @param {Any} [action] A plain object representing “what changed”. It is
     * a good idea to keep actions serializable so you can record and replay user
     * sessions, or use the time travelling `redux-devtools`.
     *
     * @returns {Any} For convenience, the same action object you dispatched.
     *
     * Note that, if you use a custom middleware, it may wrap `dispatch()` to
     * return something else (for example, a Promise you can await).
     */
    fun dispatch(action: Any): Any {
        if (!isPlainObject(action)) {
            throw Exception(
                    "Actions must be plain objects. Use custom middleware for async actions.")
        }

        if (isDispatching) {
            throw Exception("Reducers may not dispatch actions.")
        }

        try {
            isDispatching = true
            currentState = currentReducer(currentState, action)
        } finally {
            isDispatching = false
        }

        val listeners = nextListeners
        currentListeners = nextListeners
        listeners.forEach { it() }

        return action
    }

    /**
     * Replaces the reducer currently used by the store to calculate the state.
     *
     * You might need this if your app implements code splitting and you want to
     * load some of the reducers dynamically. You might also need this if you
     * implement a hot reloading mechanism for Redux.
     *
     * @param {function} nextReducer The reducer for the store to use instead.
     * @returns {void}
     */
    fun replaceReducer(nextReducer: Reducer<S>) {
        currentReducer = nextReducer

        // This action has a similar effect to ActionTypes.INIT.
        // Any reducers that existed in both the new and old rootReducer
        // will receive the previous state. This effectively populates
        // the new state tree with any relevant data from the old one.
        dispatch(ActionTypes.REPLACE)
    }

    /**
     * Interoperability point for observable/reactive libraries.
     * @returns {observable} A minimal observable of state changes.
     * For more information, see the observable proposal:
     * https://github.com/tc39/proposal-observable
     */
    /* TODO: consider kotlinx.coroutines.flow?

     */

    // When a store is created, an "INIT" action is dispatched so that every
    // reducer returns their initial state. This effectively populates
    // the initial state tree.
    dispatch(ActionTypes.INIT)

    return Store(
            dispatch = ::dispatch,
            subscribe = ::subscribe,
            getState = ::getState,
            replaceReducer = ::replaceReducer
    )
}
