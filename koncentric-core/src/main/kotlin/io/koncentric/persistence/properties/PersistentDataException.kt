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

import kotlin.reflect.KClass

/**
 * An exception thrown when there is some kind of unexpected error when dealing with
 * persistent data
 */
sealed class PersistentDataException(override val message: String) : IllegalStateException(message)

/**
 * An exception thrown when an undefined property is requested
 */
@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
class NoSuchPropertyException internal constructor(val prop: String) : PersistentDataException("No such property [$prop]")

/**
 * An exception thrown when a persistent property has an unexpected type
 */
@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
class UnexpectedPropertyTypeException internal constructor(
    val prop: String,
    val expectedType: KClass<*>,
    val actualType: KClass<*>,
    val lazy: Boolean = false
) : PersistentDataException("Property [$prop] (lazy=$lazy) has unexpected type [$actualType]. Expected [$expectedType]")

/**
 * An exception thrown when a persistent model has a property name clash
 */
@Suppress("CanBeParameter", "MemberVisibilityCanBePrivate")
class DuplicatePropertyException internal constructor(
    val prop: String,
    val modelClass: KClass<*>
) : PersistentDataException("Duplicate property [$prop] in [${modelClass.qualifiedName}]")