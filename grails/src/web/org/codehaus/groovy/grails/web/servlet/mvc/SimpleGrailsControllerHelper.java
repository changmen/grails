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
package org.codehaus.groovy.grails.web.servlet.mvc;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MissingPropertyException;
import groovy.lang.ProxyMetaClass;
import groovy.util.Proxy;
import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.collections.map.CompositeMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsControllerClass;
import org.codehaus.groovy.grails.commons.GrailsClassUtils;
import org.codehaus.groovy.grails.commons.metaclass.GenericDynamicProperty;
import org.codehaus.groovy.grails.scaffolding.GrailsScaffolder;
import org.codehaus.groovy.grails.web.metaclass.ChainDynamicMethod;
import org.codehaus.groovy.grails.web.metaclass.ControllerDynamicMethods;
import org.codehaus.groovy.grails.web.metaclass.GetParamsDynamicProperty;
import org.codehaus.groovy.grails.web.servlet.DefaultGrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.FlashScope;
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
import org.codehaus.groovy.grails.web.servlet.GrailsHttpServletResponse;
import org.codehaus.groovy.grails.web.servlet.mvc.exceptions.ControllerExecutionException;
import org.codehaus.groovy.grails.web.servlet.mvc.exceptions.NoClosurePropertyForURIException;
import org.codehaus.groovy.grails.web.servlet.mvc.exceptions.NoViewNameDefinedException;
import org.codehaus.groovy.grails.web.servlet.mvc.exceptions.UnknownControllerException;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.beans.IntrospectionException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper class for handling controller requests
 *
 * @author Graeme Rocher
 * @since 0.1
 * 
 * Created: 12-Jan-2006
 */
public class SimpleGrailsControllerHelper implements GrailsControllerHelper {

    private static final String SCAFFOLDER = "Scaffolder";

    private GrailsApplication application;
    private ApplicationContext applicationContext;
    private Map chainModel = Collections.EMPTY_MAP;
    private ControllerDynamicMethods interceptor;
    private GrailsScaffolder scaffolder;
    private ServletContext servletContext;
    private GrailsApplicationAttributes grailsAttributes;
    private Pattern uriPattern = Pattern.compile("/(\\w+)/?(\\w*)/?(.*)/?(.*)");
    
    private static final Log LOG = LogFactory.getLog(SimpleGrailsControllerHelper.class);
    private static final String DISPATCH_ACTION_PARAMETER = "_action";
    private static final String ID_PARAMETER = "id";

    public SimpleGrailsControllerHelper(GrailsApplication application, ApplicationContext context, ServletContext servletContext) {
        super();
        this.application = application;
        this.applicationContext = context;
        this.servletContext = servletContext;
        this.grailsAttributes = new DefaultGrailsApplicationAttributes(this.servletContext);
    }

    public ServletContext getServletContext() {
        return this.servletContext;
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerHelper#getControllerClassByName(java.lang.String)
      */
    public GrailsControllerClass getControllerClassByName(String name) {
        return this.application.getController(name);
    }

    public GrailsScaffolder getScaffolderForController(String controllerName) {
        GrailsControllerClass controllerClass = getControllerClassByName(controllerName);
        return (GrailsScaffolder)applicationContext.getBean( controllerClass.getFullName() + SCAFFOLDER );
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerHelper#getControllerClassByURI(java.lang.String)
      */
    public GrailsControllerClass getControllerClassByURI(String uri) {
        return this.application.getControllerByURI(uri);
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerHelper#getControllerInstance(org.codehaus.groovy.grails.commons.GrailsControllerClass)
      */
    public GroovyObject getControllerInstance(GrailsControllerClass controllerClass) {
        return (GroovyObject)this.applicationContext.getBean(controllerClass.getFullName());
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerHelper#handleURI(java.lang.String, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
      */
    public ModelAndView handleURI(String uri, HttpServletRequest request, GrailsHttpServletResponse response) {
        return handleURI(uri,request,response,Collections.EMPTY_MAP);
    }



    /**
     * If in Proxy's are used in the Groovy context, unproxy (is that a word?) them by setting
     * the adaptee as the value in the map so that they can be used in non-groovy view technologies
     *
     * @param model The model as a map
     */
    private void removeProxiesFromModelObjects(Map model) {

        for (Iterator keyIter = model.keySet().iterator(); keyIter.hasNext();) {
            Object current = keyIter.next();
            Object modelObject = model.get(current);
            if(modelObject instanceof Proxy) {
                model.put( current, ((Proxy)modelObject).getAdaptee() );
            }
        }
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerHelper#handleURI(java.lang.String, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, java.util.Map)
      */
    public ModelAndView handleURI(String uri, HttpServletRequest request, GrailsHttpServletResponse response, Map params) {
        if(uri == null)
            throw new IllegalArgumentException("Controller URI [" + uri + "] cannot be null!");

        // step 1: process the uri
        if (uri.indexOf("?") > -1) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        if(uri.indexOf('\\') > -1) {
            uri = uri.replaceAll("\\\\", "/");
        }
        if(!uri.startsWith("/"))
            uri = '/' + uri;
        if(uri.endsWith("/"))
            uri = uri.substring(0,uri.length() - 1);

        String id = null;
        String controllerName = null;
        String actionName = null;
        Map extraParams = Collections.EMPTY_MAP;
        Matcher m = uriPattern.matcher(uri);
        if(m.find()) {
            controllerName = m.group(1);
            actionName =  m.group(2);
            uri = '/' + controllerName + '/' + actionName;
            id = m.group(3);
            String extraParamsString = m.group(4);
            if(extraParamsString != null && extraParamsString.indexOf('/') > - 1) {
                String[] tokens = extraParamsString.split("/");
                extraParams = new HashMap();
                for (int i = 0; i < tokens.length; i++) {
                    String token = tokens[i];
                    if(i == 0 || ((i % 2) == 0)) {
                        if((i + 1) < tokens.length) {
                            extraParams.put(token, tokens[i + 1]);
                        }
                    }
                }

            }
        }
        // if the action name is blank check its included as dispatch parameter
        if(StringUtils.isBlank(actionName) && request.getParameter(DISPATCH_ACTION_PARAMETER) != null) {
            actionName = GrailsClassUtils.getPropertyNameRepresentation(request.getParameter(DISPATCH_ACTION_PARAMETER));
            uri = '/' + controllerName + '/' + actionName;
        }
        if(uri.endsWith("/"))
            uri = uri.substring(0,uri.length() - 1);

        // if the id is blank check if its a request parameter
        if(StringUtils.isBlank(id) && request.getParameter(ID_PARAMETER) != null) {
            id = request.getParameter(ID_PARAMETER);
        }
        if(LOG.isDebugEnabled()) {
            LOG.debug("Processing request for controller ["+controllerName+"], action ["+actionName+"], and id ["+id+"]");
        }
        if(LOG.isTraceEnabled()) {
            LOG.trace("Extra params from uri ["+extraParams+"] ");
        }
        // Step 2: lookup the controller in the application.
        GrailsControllerClass controllerClass = getControllerClassByURI(uri);

        if (controllerClass == null) {
            throw new UnknownControllerException("No controller found for URI [" + uri + "]!");
        }

        // parse the uri in its individual tokens
        controllerName = WordUtils.uncapitalize(controllerClass.getName());

        // Step 3: load controller from application context.
        GroovyObject controller = getControllerInstance(controllerClass);

        if(!controllerClass.isHttpMethodAllowedForAction(controller, request.getMethod(), actionName)) {
        	try {
				response.sendError(HttpServletResponse.SC_FORBIDDEN);
				return null;
			} catch (IOException e) {
				throw new ControllerExecutionException("I/O error sending 403 error",e);
			}
        }
        
        request.setAttribute( GrailsApplicationAttributes.CONTROLLER, controller );

        // Step 3a: Configure a proxy interceptor for controller dynamic methods for this request
        if(this.interceptor == null) {
            try {
                interceptor = new ControllerDynamicMethods(controller,this,request,response);
            }
            catch(IntrospectionException ie) {
                throw new ControllerExecutionException("Error creating dynamic controller methods for controller ["+controller.getClass()+"]: " + ie.getMessage(), ie);
            }
        }
        // Step 3b: if scaffolding retrieve scaffolder
        if(controllerClass.isScaffolding())  {
            this.scaffolder = (GrailsScaffolder)applicationContext.getBean( controllerClass.getFullName() + SCAFFOLDER );
            if(this.scaffolder == null)
                throw new IllegalStateException("Scaffolding set to true for controller ["+controllerClass.getFullName()+"] but no scaffolder available!");
        }

        // Step 4: get closure property name for URI.
        if(StringUtils.isBlank(actionName))
            actionName = controllerClass.getClosurePropertyName(uri);

        if (StringUtils.isBlank(actionName)) {
            // Step 4a: Check if scaffolding
            if( controllerClass.isScaffolding() && !scaffolder.supportsAction(actionName))
                throw new NoClosurePropertyForURIException("Could not find closure property for URI [" + uri + "] for controller [" + controllerClass.getFullName() + "]!");
        }

        // Step 4a: Set dynamic properties on controller
        controller.setProperty(ControllerDynamicMethods.CONTROLLER_NAME_PROPERTY, controllerName);
        controller.setProperty(ControllerDynamicMethods.ACTION_NAME_PROPERTY, actionName);
        controller.setProperty(ControllerDynamicMethods.CONTROLLER_URI_PROPERTY, '/' + controllerName);
        controller.setProperty(ControllerDynamicMethods.ACTION_URI_PROPERTY, '/' + controllerName + '/' + actionName);

        // populate additional params from url
        Map controllerParams = (Map)controller.getProperty(GetParamsDynamicProperty.PROPERTY_NAME);
        if(!StringUtils.isBlank(id)) {
            controllerParams.put(GrailsApplicationAttributes.ID_PARAM, id);
        }
        if(!extraParams.isEmpty()) {
            for (Iterator i = extraParams.keySet().iterator(); i.hasNext();) {
                String name = (String) i.next();
                controllerParams.put(name,extraParams.get(name));
            }
        }

        // set the flash scope instance to its next state and set on controller
        FlashScope fs = this.grailsAttributes.getFlashScope(request);
        fs.next();

        controller.setProperty(ControllerDynamicMethods.FLASH_SCOPE_PROPERTY,fs);
        
        // Step 4b: Set grails attributes in request scope
        request.setAttribute(GrailsApplicationAttributes.REQUEST_SCOPE_ID,this.grailsAttributes);

        // Step 5: get the view name for this URI.
        String viewName = controllerClass.getViewByURI(uri);

        // Step 5a: Check if there is a before interceptor if there is execute it
        boolean executeAction = true;
        if(controllerClass.isInterceptedBefore(controller,actionName)) {
        	Closure beforeInterceptor = controllerClass.getBeforeInterceptor(controller);
        	if(beforeInterceptor!= null) {
        		Object interceptorResult = beforeInterceptor.call();
        		if(interceptorResult instanceof Boolean) {
        			executeAction = ((Boolean)interceptorResult).booleanValue();
        		}
        	}
        }
        // if the interceptor returned false don't execute the action
        if(!executeAction)
        	return null;
        
        // Step 6: get closure from closure property
        Closure action;
        try {
        	action = (Closure)controller.getProperty(actionName);
            // Step 7: process the action
            Object returnValue = handleAction( controller,action,request,response,params );


            // Step 8: determine return value type and handle accordingly
            initChainModel(controller);
            if(response.isRedirected()) {
            		return null;
            }
            
            ModelAndView mv = handleActionResponse(controller,returnValue,actionName,viewName);
            // Step 9: Check if there is after interceptor
            if(controllerClass.isInterceptedAfter(controller,actionName)) {
            	Closure afterInterceptor = controllerClass.getAfterInterceptor(controller);
            	afterInterceptor.call(new Object[]{ mv.getModel() });
            }
            return mv;
        }
        catch(MissingPropertyException mpe) {
            if(controllerClass.isScaffolding())
                throw new IllegalStateException("Scaffolder supports action ["+actionName +"] for controller ["+controllerClass.getFullName()+"] but getAction returned null!");
            else {
            	try {
					response.sendError(HttpServletResponse.SC_NOT_FOUND);
					return null;
				} catch (IOException e) {
						throw new ControllerExecutionException("I/O error sending 404 error",e);
				}
            }
        }

    }

    public GrailsApplicationAttributes getGrailsAttributes() {
        return this.grailsAttributes;
    }

    public Object handleAction(GroovyObject controller,Closure action, HttpServletRequest request, HttpServletResponse response) {
        return handleAction(controller,action,request,response,Collections.EMPTY_MAP);
    }

    public Object handleAction(GroovyObject controller,Closure action, HttpServletRequest request, HttpServletResponse response, Map params) {
            if(interceptor == null) {
                ProxyMetaClass pmc = (ProxyMetaClass)controller.getMetaClass();
                interceptor = (ControllerDynamicMethods)pmc.getInterceptor();
            }
            // if there are additional params add them to the params dynamic property
            if(params != null && !params.isEmpty()) {
                GetParamsDynamicProperty paramsProp = (GetParamsDynamicProperty)interceptor.getDynamicProperty( GetParamsDynamicProperty.PROPERTY_NAME );
                paramsProp.addParams( params );
            }
            // check the chain model is not empty and add it
            if(!this.chainModel.isEmpty()) {
                // get the "chainModel" property
                GenericDynamicProperty chainProperty = (GenericDynamicProperty)interceptor.getDynamicProperty(ChainDynamicMethod.PROPERTY_CHAIN_MODEL);
                // if it doesn't exist create it
                if(chainProperty == null) {
                    interceptor.addDynamicProperty( new GenericDynamicProperty( ChainDynamicMethod.PROPERTY_CHAIN_MODEL,Map.class,this.chainModel,false ) );
                }
                else {
                    // otherwise add to it
                    Map chainPropertyModel = (Map)chainProperty.get(controller);
                    chainPropertyModel.putAll( this.chainModel );
                    this.chainModel = chainPropertyModel;
                }
            }



        // Step 7: determine argument count and execute.
        Object returnValue = action.call();

        // Step 8: add any errors to the request
        request.setAttribute( GrailsApplicationAttributes.ERRORS, controller.getProperty(ControllerDynamicMethods.ERRORS_PROPERTY) );

        return returnValue;
    }

    /* (non-Javadoc)
      * @see org.codehaus.groovy.grails.web.servlet.mvc.GrailsControllerHelper#handleActionResponse(org.codehaus.groovy.grails.commons.GrailsControllerClass, java.lang.Object, java.lang.String, java.lang.String)
      */
    public ModelAndView handleActionResponse( GroovyObject controller,Object returnValue,String closurePropertyName, String viewName) {
        boolean viewNameBlank = (viewName == null || viewName.length() == 0);
        // reset the metaclass
        ModelAndView explicityModelAndView = (ModelAndView)controller.getProperty(ControllerDynamicMethods.MODEL_AND_VIEW_PROPERTY);
        Boolean renderView = (Boolean)controller.getProperty(ControllerDynamicMethods.RENDER_VIEW_PROPERTY);

        if(renderView == null) renderView = Boolean.TRUE;

        if(!renderView.booleanValue()) {
            return null;
        }
        else if(explicityModelAndView != null) {
            return explicityModelAndView;
        }
        else if (returnValue == null) {
            if (viewNameBlank) {
                return null;
            } else {
                Map model;
                if(!this.chainModel.isEmpty()) {
                    model = new CompositeMap(this.chainModel, new BeanMap(controller));
                }
                else {
                    model = new BeanMap(controller);
                }

                return new ModelAndView(viewName, model);
            }
        } else if (returnValue instanceof Map) {
            // remove any Proxy wrappers and set the adaptee as the value
            Map returnModel = (Map)returnValue;
            removeProxiesFromModelObjects(returnModel);
            if(!this.chainModel.isEmpty()) {
                returnModel.putAll(this.chainModel);
            }
            return new ModelAndView(viewName, returnModel);

        } else if (returnValue instanceof ModelAndView) {
            ModelAndView modelAndView = (ModelAndView)returnValue;

            // remove any Proxy wrappers and set the adaptee as the value
            Map modelMap = modelAndView.getModel();
            removeProxiesFromModelObjects(modelMap);

            if(!this.chainModel.isEmpty()) {
                modelAndView.addAllObjects(this.chainModel);
            }

            if (modelAndView.getView() == null && modelAndView.getViewName() == null) {
                if (viewNameBlank) {
                    throw new NoViewNameDefinedException("ModelAndView instance returned by and no view name defined by nor for closure on property [" + closurePropertyName + "] in controller [" + controller.getClass() + "]!");
                } else {
                    modelAndView.setViewName(viewName);
                }
            }
            return modelAndView;
        }
        else {
            Map model;
            if(!this.chainModel.isEmpty()) {
                model = new CompositeMap(this.chainModel, new BeanMap(controller));
            }
            else {
                model = new BeanMap(controller);
            }
            return new ModelAndView(viewName, model);
        }
    }

	private void initChainModel(GroovyObject controller) {
		FlashScope fs = this.grailsAttributes.getFlashScope((HttpServletRequest)controller.getProperty(ControllerDynamicMethods.REQUEST_PROPERTY));
        if(fs.containsKey(ChainDynamicMethod.PROPERTY_CHAIN_MODEL)) {
            this.chainModel = (Map)fs.get(ChainDynamicMethod.PROPERTY_CHAIN_MODEL);
            if(this.chainModel == null)
                this.chainModel = Collections.EMPTY_MAP;
        }
	}

}
