/*
 * Copyright 2021-2024 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.koncentric.persistence.properties

import io.vavr.collection.HashMap
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty

internal class LazyRefresh : AbstractCoroutineContextElement(LazyRefresh) {
    companion object Key: CoroutineContext.Key<LazyRefresh>
}

/**
 * Calls the specified suspending block and causes any lazy properties
 * accessed inside the block to ignore their previously resolved values
 * and instead calculate new values from the original function.
 */
suspend fun <T: Any?> lazyRefresh(block: suspend () -> T): T {
    return withContext(LazyRefresh()) {
        block()
    }
}

/**
 * A data holder for a persistent properties backing a persistent domain model.
 * Persistent property values can be either known values or lazy values computed
 * by a suspendable function. Once a lazy function is evaluated successfully, the
 * resulting value is memoized unless and until the property is overwritten by a
 * call to [set].
 */
interface IPersistentProperties : IDebugLazily {

    /**
     * Set the [value] of property [prop]
     * @param prop the name of the property to set
     * @param value the value of the property
     */
    fun set(prop: String, value: Any)

    /**
     * Set the value of a lazy property
     * @param prop the name of the property to set
     * @param fn the function to calculate the lazy value of the property
     */
    fun setLazy(prop: String, fn: suspend () -> Any)

    /**
     * Remove the property [prop]
     * @param prop the name of the property to remove
     */
    fun remove(prop: String)

    /**
     * Get the value of the property named [prop] as a [T]
     * @param prop the name of the property
     * @param klass the [KClass] indicating the type of the property value
     * @throws PersistentDataException if no such property exists, or if it is not the expected type
     */
    fun <T: Any> get(prop: String, klass: KClass<T>) : T

    /**
     * Get the value of the lazy property named [prop] as a [T].
     * @param prop the name of the lazy property
     * @param klass the [KClass] indicating the type of the property value
     */
    suspend fun <T: Any> getLazy(prop: String, klass: KClass<T>): T

    /**
     * Resets a lazy property so that calling [getLazy] again subsequently
     * will result in another lazy resolution. If the property [prop] was
     * not truly lazy to begin with, this method is a no-op.
     * @param prop the name of the lazy property to reset
     * @throws PersistentDataException if no such property exists
     */
    fun resetLazy(prop:String)

    /**
     * Updates the value of the property named [prop] to the result of the [updateFn].
     * If [prop] is a lazy property which has not yet been resolved, then [updateFn] will
     * not be called and the property will not be updated.
     *
     * @param prop the name of the property to update
     * @param klass the [KClass] indicating the type of the property value
     * @param updateFn the function which will be executed to provide the new updated value for the property.
     * If [updateFn] is called, it will be passed the current value of the property.
     */
    suspend fun <T: Any> updateIfResolved(prop: String, klass: KClass<T>, updateFn: suspend (T) -> (T))

    /**
     * @return an independent copy of this object
     */
    fun copy(): IPersistentProperties

    /**
     * Set an order for properties for [IDebugLazily.toLazyDebugMap]
     */
    fun setDebugPropertyOrder(propertyOrder: List<String>)

    companion object {
        /**
         * @return an empty [IPersistentProperties]
         */
        fun empty(): IPersistentProperties = PersistentProperties(HashMap.empty())
    }
}

/**
 * Set the [value] of the property whose name is the same as the name of [prop]
 * @param prop the [KProperty]
 * @param value the value of the property
 */
fun IPersistentProperties.set(prop: KProperty<*>, value: Any) = set(prop.name, value)

/**
 * Set the [value] of the property whose name is the same as the name of [prop].
 * @param prop the [KFunction]
 * @param value the value of the property
 */
fun IPersistentProperties.set(prop: KFunction<*>, value: Any) = set(prop.name, value)

/**
 * Set the lazy [fn] of the property whose name is the same as the name of [prop].
 * @param prop the [KFunction]
 * @param fn the function to calculate the lazy value of the property
 */
fun IPersistentProperties.setLazy(prop: KFunction<*>, fn: suspend () -> Any) = setLazy(prop.name, fn)

/**
 * Get the [T] value of the property whose name is the same as the name of [prop].
 * @param prop the [KProperty]
 * @param klass the [KClass] indicating the type of the property value
 * @return the property value as a [T]
 * @throws PersistentDataException if no such property exists, or if it is not the expected type
 */
fun <T: Any> IPersistentProperties.get(prop: KProperty<*>, klass: KClass<T>) : T = get(prop.name, klass)

/**
 * Get the [T] value of the lazy property whose name is teh same as the name of [prop].
 * @param prop the name of the lazy property
 * @param klass the [KClass] indicating the type of the property value
 * @return the property value as a [T]
 * @throws PersistentDataException if no such property exists, or if it is not the expected type
 */
suspend fun <T: Any> IPersistentProperties.getLazy(prop: KFunction<*>, klass: KClass<T>): T = getLazy(prop.name, klass)

/**
 * Get the [T] value of the property named [name]
 * @param name the name of the property
 * @receiver an instance of [IPersistentProperties]
 */
inline fun <reified T: Any> IPersistentProperties.prop(name: String) : T = this.get(name, T::class)

/**
 * Get the [T] value of the property whose name is the same as [prop]
 * @param prop the [KProperty] whose name is used as the property value
 * @receiver an instance of [IPersistentProperties]
 */
inline fun <reified T: Any> IPersistentProperties.prop(prop: KProperty<T>) : T = this.get(prop.name, T::class)

/**
 * Get the [T] value of the lazy property named [name]
 * @param name the name of the property
 * @receiver an instance of [IPersistentProperties]
 */
suspend inline fun <reified T: Any> IPersistentProperties.lazyProp(name: String): T = this.getLazy(name, T::class)

/**
 * Get the [T] value of the lazy property whose name is the same as the name of [prop].
 * @param fn the [KFunction] whose name is used as the property value
 * @return the property value as a [T]
 * @throws PersistentDataException if no such property exists, or if it is not the expected type
 * @receiver an instance of [IPersistentProperties]
 */
suspend inline fun <reified T: Any> IPersistentProperties.lazyProp(fn: KFunction<T>): T = this.getLazy(fn, T::class)

/**
 * Reset the lazy property with the same name as [prop].
 * @param prop the lazy [KFunction] which should be reset
 * @receiver an instance of [IPersistentProperties]
 * @see IPersistentProperties.resetLazy
 */
inline fun <reified T: Any> IPersistentProperties.resetLazy(prop: KFunction<T>) = this.resetLazy(prop.name)

/**
 * Updates the value of the property named [prop] to the result of the [updateFn].
 * If [prop] is a lazy property which has not yet been resolved, then [updateFn] will
 * not be called and the property will not be updated.
 *
 * @param fn the lazy [KFunction] whose associated resolved property value will be updated
 * @param updateFn the function which will be executed to provide the new updated value for the property.
 * If [updateFn] is called, it will be passed the current value of the property.
 */
suspend inline fun <reified T: Any> IPersistentProperties.updateIfResolved(fn: KFunction<T>, noinline updateFn: suspend (T) -> T) =
    this.updateIfResolved(fn.name, T::class, updateFn)