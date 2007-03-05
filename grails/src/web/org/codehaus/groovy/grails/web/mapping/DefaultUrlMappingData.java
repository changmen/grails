/* Copyright 2004-2005 Graeme Rocher
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
package org.codehaus.groovy.grails.web.mapping;

import org.apache.commons.lang.StringUtils;

import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * Default implementating of the UrlMappingData interface.
 *
 * @author Graeme Rocher
 * @since 0.4
 *        <p/>
 *        Created: Mar 5, 2007
 *        Time: 7:51:47 AM
 */
public class DefaultUrlMappingData implements UrlMappingData {
    private String urlPattern;
    private String[] tokens;
    private String[] logicalUrls;

    private static final String SLASH = "/";


    public DefaultUrlMappingData(String urlPattern) {
        if(StringUtils.isBlank(urlPattern)) throw new IllegalArgumentException("Argument [urlPattern] cannot be null or blank");
        if(!urlPattern.startsWith(SLASH)) throw new IllegalArgumentException("Argument [urlPattern] is not a valid URL. It must start with '/' !");

        this.urlPattern = urlPattern.substring(1); // remove starting /
        this.tokens = this.urlPattern.split(SLASH);
        List urls = new ArrayList();

        parseUrls(urls);

        this.logicalUrls = (String[])urls.toArray(new String[urls.size()]);
    }

    private void parseUrls(List urls) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();

            if(tokens.equals(SLASH)) continue;

            if(token.endsWith("?")) {
                urls.add(buf.toString());
                tokens[i] = token.substring(0, token.length()-1);
                buf.append(SLASH).append(tokens[i]);
            }
            else {
               buf.append(SLASH).append(token);
            }
        }
        urls.add(buf.toString());
        Collections.reverse(urls);
    }

    public String[] getTokens() {
        return this.tokens;
    }

    public String[] getLogicalUrls() {
        return logicalUrls;
    }

    public String getUrlPattern() {
        return this.urlPattern;
    }
}
