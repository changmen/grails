/*
 * Copyright 2004-2005 the original author or authors.
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
package org.codehaus.groovy.grails.plugins.web.filters

import org.apache.commons.logging.*

/**
 * @author mike
 * @author Graeme Rocher
 */
class FilterConfig {
    static final LOG = LogFactory.getLog(FilterConfig)
    String name
    Map scope
    def filter
    Closure before
    Closure after
    Closure afterView

    void propertyMissing(String name, value) {
       LOG.warn "Setting $name is invalid for filter config $name"
    }
    public String toString() {"FilterConfig[$name, scope=$scope]"}
}