/*
 * Copyright 2014 Bazaarvoice, Inc.
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
package com.bazaarvoice.dropwizard.caching;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.yammer.dropwizard.ConfiguredBundle;
import com.yammer.dropwizard.config.Bootstrap;
import com.yammer.dropwizard.config.Environment;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.MetricsRegistry;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.Set;

/**
 * Bundle that sets up request caching for an application's resources.
 */
public class CachingBundle implements ConfiguredBundle<CachingBundleConfiguration> {
    private static final Set<String> SINGLETON_HEADERS = HttpHeaderUtils.headerNames(
            // Jetty sets the Date header automatically to the current time after the request has been
            // processed. Any other attempts to set the date header result in duplicate date headers. The
            // caching layer needs to be able to set the date header to the date the cached response was
            // generated. Duplicate headers are unexpected, confusing, and will likely result in problems
            // for clients.
            HttpHeaders.DATE
    );

    public void initialize(Bootstrap<?> bootstrap) {
        // Nothing to do
    }

    @Override
    public void run(CachingBundleConfiguration configuration, Environment environment) {
        MetricsRegistry metricsRegistry = Metrics.defaultRegistry();
        Function<String, Optional<String>> cacheControlMapper = configuration.getCacheControl().buildMapper();
        ResponseCache responseCache = configuration.getCache().buildCache(metricsRegistry);

        environment.addProvider(new CacheResourceMethodDispatchAdapter(responseCache, cacheControlMapper));

        environment.addFilter(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) throws ServletException {
                // Nothing to do
            }

            @Override
            public void doFilter(ServletRequest request, final ServletResponse response, FilterChain chain) throws IOException, ServletException {
                HttpServletResponse httpResponse = (HttpServletResponse) response;

                chain.doFilter(request, new HttpServletResponseWrapper(httpResponse) {
                    @Override
                    public void addHeader(String name, String value) {
                        if (SINGLETON_HEADERS.contains(name)) {
                            super.setHeader(name, value);
                        } else {
                            super.addHeader(name, value);
                        }
                    }
                });
            }

            @Override
            public void destroy() {
                // Nothing to do
            }
        }, "*");
    }
}
