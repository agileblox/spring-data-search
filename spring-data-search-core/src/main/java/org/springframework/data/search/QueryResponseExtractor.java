/*
 * Copyright 2008-2012 the original author or authors.
 *
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

package org.springframework.data.search;

import java.util.Collection;

/**
 * Contract for a converter capable of converting search index entries into
 * objects from which the entries were originally created.
 * 
 * @author Vincent Devillers
 * @author Manish Baxi
 */
public interface QueryResponseExtractor<T>
{
    /**
     * Converts index entries in a query response into objects from which the
     * entries were originally created.
     * 
     * @param response A {@link QueryResponse}.
     * @return A {@link Collection} of objects.
     */
    Collection<T> extractData(QueryResponse response);
}
