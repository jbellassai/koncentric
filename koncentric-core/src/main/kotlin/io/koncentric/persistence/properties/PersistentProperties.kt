/*
 * Copyright 2021 Koncentric, https://koncentric.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.koncentric.persistence.properties

import io.vavr.collection.LinkedHashMap
import io.vavr.collection.Map
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import kotlin.collections.Map as KotlinMap

/**
 * Standard [IPersistentProperties] implementation backed by a persistent [HashMap].
 */
internal class PersistentProperties(initial: Map<String, Any>) : IPersistentProperties {

    /**
     * The reference to the current properties map
     */
    private val propsMapRef = AtomicReference(initial)

    private fun getCurrentRef(): Map<String, Any> = propsMapRef.get()

    override fun set(prop: String, value: Any) {
        propsMapRef.compareAndSetUntilSuccessful { current -> current.put(prop, value) }
    }

    override fun setLazy(prop: String, fn: suspend () -> Any) {
        propsMapRef.compareAndSetUntilSuccessful { current -> current.put(prop, fn.ref()) }
    }

    override fun remove(prop: String) {
        propsMapRef.compareAndSetUntilSuccessful { current -> current.remove(prop) }
    }

    override fun <T : Any> get(prop: String, klass: KClass<T>): T {
        return getCurrentRef().get(prop).transform { optValue ->
            val value = optValue.getOrElseThrow { NoSuchPropertyException(prop) }
            if(klass.isInstance(value)) {
                @Suppress("UNCHECKED_CAST")
                value as T
            } else {
                throw UnexpectedPropertyTypeException(prop, klass, value::class)
            }
        }
    }

    override suspend fun <T : Any> getLazy(prop: String, klass: KClass<T>): T {
        val opt = getCurrentRef().get(prop)
        @Suppress("MoveVariableDeclarationIntoWhen")
        val value = opt.getOrElseThrow { NoSuchPropertyException(prop) }
        return when(value) {
            is FnReference -> {
                value.resolve(prop, klass)
            }
            is ResettableLazyProperty<*> -> {
                if(coroutineContext[LazyRefresh] != null) {
                    val originalFn = value.reset(prop)
                    originalFn.resolve(prop, klass)
                }
                else {
                    value.resolvedValue.validateType(prop, klass)
                }
            }
            else -> {
                value.validateType(prop, klass)
            }
        }
    }

    private suspend fun <T : Any> FnReference.resolve(prop: String, klass: KClass<T>): T {
        return when (this) {
            is KFunctionReference -> {
                val fn = this.fn
                val rawResult =
                    if (fn.isSuspend) fn.callSuspend(this.instance)
                    else fn.call(this.instance)
                val typedResult = rawResult.validateType(prop, klass)
                val resettable = ResettableLazyProperty(this, typedResult)
                set(prop, resettable)
                typedResult
            }
            is SuspendableLambdaReference -> {
                val fn = this.fn
                val rawResult = fn.invoke()
                val typedResult = rawResult.validateType(prop, klass)
                val resettable = ResettableLazyProperty(this, typedResult)
                set(prop, resettable)
                typedResult
            }
        }
    }

    private fun ResettableLazyProperty<*>.reset(prop: String): FnReference {
        val originalFn = this.fn
        set(prop, originalFn)
        return originalFn
    }

    override fun resetLazy(prop: String) {
        val opt = getCurrentRef().get(prop)
        val value = opt.getOrElseThrow { NoSuchPropertyException(prop) }
        if(value is ResettableLazyProperty<*>) {
            value.reset(prop)
        }
    }

    override suspend fun <T: Any> updateIfResolved(prop: String, klass: KClass<T>, updateFn: suspend (T) -> (T)) {
        val opt = getCurrentRef().get(prop)
        @Suppress("MoveVariableDeclarationIntoWhen")
        val value = opt.getOrElseThrow { NoSuchPropertyException(prop) }
        when (value) {
            is FnReference -> {
                // value has not been resolved yet, this is a no-op
            }
            is ResettableLazyProperty<*> -> {
                val currentValue = value.resolvedValue.validateType(prop, klass)
                val newValue = updateFn(currentValue)
                val originalFn = value.fn
                val resettable = ResettableLazyProperty(originalFn, newValue)
                set(prop, resettable)
            }
            else -> {
                val currentValue = value.validateType(prop, klass)
                val newValue = updateFn(currentValue)
                set(prop, newValue)
            }
        }
    }

    private fun <T: Any> Any?.validateType(name: String, klass: KClass<T>): T {
        return if(this == null) {
            throw UnexpectedPropertyTypeException(name, klass, Nothing::class)
        }
        else if(klass.isInstance(this)) {
            @Suppress("UNCHECKED_CAST")
            this as T
        }
        else {
            throw UnexpectedPropertyTypeException(name, klass, this::class)
        }
    }

    override fun copy(): IPersistentProperties {
        return PersistentProperties(this.getCurrentRef())
    }

    private val currentPropertyOrder = AtomicReference<List<String>>(emptyList())

    override fun setDebugPropertyOrder(propertyOrder: List<String>) {
        this.currentPropertyOrder.set(propertyOrder.toList())
    }

    private fun Any.toDebugMapValue() : Any {
        val mapValue = when(this) {
            is FnReference -> NotYetResolved
            is ResettableLazyProperty<*> -> this.resolvedValue
            else -> this
        }
        return if(mapValue is IDebugLazily) mapValue.toLazyDebugMap()
        else mapValue
    }

    override fun toLazyDebugMap(): KotlinMap<String, Any> {
        val props = getCurrentRef()
        val ret = mutableMapOf<String, Any>()
        currentPropertyOrder.get().forEach { prop ->
            val value = props.get(prop).orNull
            if(value != null) {
                ret[prop] = value.toDebugMapValue()
            }
        }
        props.forEach { (key, value) ->
            if(!ret.containsKey(key)) {
                ret[key] = value.toDebugMapValue()
            }
        }
        return ret
    }
}


private fun LinkedHashMap<String, Any>.putProp(name: String, value: Any) =
    if(value is IMutatePersistently) {
        this.put(name, value.toPersistentMap())
    } else {
        this.put(name, value)
    }

internal sealed class FnReference
internal data class KFunctionReference(val fn: KFunction<*>, val instance: Any) : FnReference()
internal data class SuspendableLambdaReference(val fn : suspend () -> Any): FnReference()

private data class ResettableLazyProperty<T: Any>(
    val fn: FnReference,
    val resolvedValue: T
)

private fun KFunction<*>.ref(callOn: Any) = KFunctionReference(this, callOn)
private fun (suspend () -> Any).ref() = SuspendableLambdaReference(this)

internal fun <T: IMutatePersistently> toPersistentMap(obj: T, klass: KClass<T>): Map<String, Any> {
    var ret = LinkedHashMap.empty<String, Any>()
    klass.declaredMemberProperties.forEach { prop ->
        val value = prop.get(obj)
        if(value != null) {
            ret = ret.putProp(prop.name, value)
        }
    }
    klass.declaredMemberFunctions
        .filter { it.parameters.isEmpty() || it.parameters.all { param -> param.kind == KParameter.Kind.INSTANCE }}
        .forEach { fn ->
            val functionName = fn.name
            if(ret.containsKey(functionName)) {
                throw DuplicatePropertyException(functionName, klass)
            }
            ret = ret.put(functionName, fn.ref(obj))
        }
    return ret
}

internal inline fun <reified T: IMutatePersistently> T.toPersistentMap() = toPersistentMap(this, T::class)