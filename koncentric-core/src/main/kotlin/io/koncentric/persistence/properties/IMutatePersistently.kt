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

import kotlin.reflect.KClass

/**
 * Marker interface for domain models whose internals mutate persistently
 */
interface IMutatePersistently

/**
 * Utility function to build an [IPersistentProperties] from an [IMutatePersistently] instance
 * @receiver the [IMutatePersistently]
 */
inline fun <reified T: IMutatePersistently> T.toPersistentProperties() = toPersistentProperties(this, T::class)

/**
 * Utility function to build an [IPersistentProperties] from an [IMutatePersistently] instance
 * @param obj the [IMutatePersistently] instance
 * @param klass the [KClass] of [obj]
 * @return an [IPersistentProperties]
 */
fun <T: IMutatePersistently> toPersistentProperties(obj: T, klass: KClass<T>): IPersistentProperties {
    val dataMap = toPersistentMap(obj, klass)
    return PersistentProperties(dataMap)
}