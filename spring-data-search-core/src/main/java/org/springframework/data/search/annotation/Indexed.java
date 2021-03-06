/*
 *
 * Copyright 2008-2012 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.search.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When applied to a field or property on a class, denotes that the field or
 * property needs to be indexed.
 * 
 * @author Vincent Devillers
 * @author Manish Baxi
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = { ElementType.FIELD, ElementType.METHOD, ElementType.TYPE })
public @interface Indexed
{
    /**
     * Gets the field name to use when storing the field value in the search
     * index.
     * 
     * @return The field name to use when storing the field value in the search
     *         index.
     */
    String fieldName() default "";
}
