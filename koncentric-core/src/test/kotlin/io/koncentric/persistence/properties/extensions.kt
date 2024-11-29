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

import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.vavr.collection.Map
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

fun Map<String, Any>.getOrFail(key: String) =
    this.get(key).orNull ?: fail("No such key: [$key]")

infix fun Any.shouldBeSameFunctionAs(fn: KFunction<*>) =
    if(this !is KFunctionReference) fail("Not a function")
    else this.fn.javaMethod shouldBe fn.javaMethod