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

import java.util.concurrent.atomic.AtomicReference

/**
 * Utility to perform an [AtomicReference.compareAndSet] repeatedly
 * until it succeeds.
 */
inline fun <T: Any> AtomicReference<T>.compareAndSetUntilSuccessful(newValue: (T) -> T) {
    do {
        val current = this.get()
        val new = newValue(current)
    } while(!this.compareAndSet(current, new))
}

inline fun <T: Any?> AtomicReference<T>.casUntilSuccessful(newValue: (T) -> T) {
    do {
        val current = this.get()
        val new = newValue(current)
    } while(!this.compareAndSet(current, new))
}