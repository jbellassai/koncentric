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

private const val NOT_YET_RESOLVED = "<<Not Yet Resolved>>"

/**
 * A placeholder for lazy properties which have not yet been resolved
 */
object NotYetResolved {
    override fun toString(): String = NOT_YET_RESOLVED
}

/**
 * An interface for classes backed primarily by persistent
 */
interface IDebugLazily {

    /**
     * @return a [Map] containing debug property names and values. Lazy
     * properties not yet resolved should be associated with a value of
     * [NotYetResolved]
     */
    fun toLazyDebugMap(): Map<String, Any>
}