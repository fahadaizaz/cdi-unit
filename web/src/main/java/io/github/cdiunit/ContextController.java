/*
 * Copyright 2011 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.cdiunit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextException;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;

import org.jboss.weld.context.ConversationContext;
import org.jboss.weld.context.http.Http;

import io.github.cdiunit.internal.QuietDiscovery;
import io.github.cdiunit.internal.servlet.CdiUnitInitialListener;
import io.github.cdiunit.internal.servlet.LifecycleAwareRequest;
import io.github.cdiunit.internal.servlet.common.CdiUnitServlet;
import io.github.cdiunit.internal.servlet.common.HttpSessionAware;

/**
 * Use to explicitly open and close Request, Session and Conversation scopes.
 * <p>
 * If you are testing code that runs over several requests then you may want to
 * explicitly control activation and deactivation of scopes. Use
 * ContextController to do this.
 * </p>
 *
 * <pre>
 *
 * &#064;RunWith(CdiRunner.class)
 * &#064;AdditionalClasses(RequestScopedWarpDrive.class)
 * class TestStarship {
 *
 *     &#064;Inject
 *     ContextController contextController; // Obtain an instance of the context
 *                                          // controller.
 *
 *     &#064;Inject
 *     Starship starship;
 *
 *     &#064;Test
 *     void testStart() {
 *         contextController.openRequest(); // Start a new request.
 *
 *         starship.start();
 *         contextController.closeRequest(); // Close the current request.
 *     }
 * }
 * </pre>
 *
 */
@ApplicationScoped
@QuietDiscovery
public class ContextController {

    private ThreadLocal<HttpServletRequest> requests;

    private HttpSession currentSession;

    @Inject
    @CdiUnitServlet
    private ServletContext context;

    @Inject
    private CdiUnitInitialListener listener;

    @PostConstruct
    void initContext() {
        requests = new ThreadLocal<>();
        listener.contextInitialized(new ServletContextEvent(context));
    }

    @PreDestroy
    void destroyContext() {
        listener.contextDestroyed(new ServletContextEvent(context));
        requests = null;
    }

    @Inject
    @CdiUnitServlet
    private Provider<HttpServletRequest> requestProvider;

    @Inject
    @Http
    private ConversationContext conversationContext;

    /**
     * Start a request.
     *
     * @return The request opened.
     */
    public HttpServletRequest openRequest() {
        HttpServletRequest currentRequest = requests.get();
        if (currentRequest != null) {
            throw new ContextException("A request is already open");
        }

        HttpServletRequest request = requestProvider.get();

        if (currentSession != null) {
            if (request instanceof HttpSessionAware) {
                ((HttpSessionAware) request).setSession(currentSession);
            }
            request.getSession();
        }

        currentRequest = new LifecycleAwareRequest(listener, request);
        requests.set(currentRequest);

        listener.requestInitialized(new ServletRequestEvent(context, currentRequest));
        if (!conversationContext.isActive()) {
            conversationContext.activate();
        }

        return currentRequest;
    }

    /**
     * @return Returns the current in progress request or throws an exception if the request was not active
     */
    public HttpServletRequest currentRequest() {
        HttpServletRequest currentRequest = requests.get();
        if (currentRequest == null) {
            throw new ContextNotActiveException("A request has not been opened");
        }

        return currentRequest;
    }

    /**
     * Close the currently active request.
     */
    public void closeRequest() {

        HttpServletRequest currentRequest = requests.get();
        if (currentRequest != null) {

            listener.requestDestroyed(new ServletRequestEvent(context, currentRequest));
            currentSession = currentRequest.getSession(false);
        }

        requests.remove();
    }

    /**
     * Close the currently active session.
     */
    public void closeSession() {

        HttpServletRequest currentRequest = requests.get();
        if (currentRequest != null) {
            currentSession = currentRequest.getSession(false);
        }

        if (currentSession != null) {

            listener.sessionDestroyed(new HttpSessionEvent(currentSession));
            currentSession = null;
        }
    }

    public HttpSession getSession() {
        return currentSession;
    }

}
