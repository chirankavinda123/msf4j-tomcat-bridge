package com.wso2.carbon.identity.msf4j.bridge.servlet.internal;

import com.wso2.carbon.identity.msf4j.bridge.servlet.Msf4jBridgeServlet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.wso2.msf4j.Microservice;
import org.wso2.msf4j.internal.MSF4JConstants;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;

@Component(name = "msf4j.bridge.component", immediate = true)
public class RegisterServiceComponents {

    private static final Log log = LogFactory.getLog(RegisterServiceComponents.class);
    private Msf4jBridgeServlet bridgeServlet;
    private Set<Microservice> inactiveMicroservices = new HashSet<>();
    private HttpService httpService;

    @Activate
    protected void activate(ComponentContext componentContext) {

        System.out.println("Activation RegisterServiceComponents ");
        bridgeServlet = new Msf4jBridgeServlet();
        HttpContext defaultHttpContext = httpService.createDefaultHttpContext();

        try {
            bridgeServlet.init();
            httpService.registerServlet("/IS", bridgeServlet, null, defaultHttpContext);
            addPendingServices();
            log.info("MSF4J - Servlet bridge activated Successfully.");
        } catch (ServletException e) {
            log.error("Error in registering the MSF4J servlet", e);
            e.printStackTrace();
        } catch (NamespaceException e) {
            log.error("Error in registering the MSF4J servlet", e);
            e.printStackTrace();
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {

        System.out.println("Deactivate RegisterServiceComponents ");
    }

    /**
     * Adds any pending services which has been advices to current registry.
     */
    private void addPendingServices() {

        if (bridgeServlet == null) {
            return;
        }
        Set<Microservice> inactiveServicesCopy = new HashSet<>(inactiveMicroservices);
        //TODO: do some interlocking on THIS-123
        inactiveServicesCopy.stream().forEach(ms -> bridgeServlet.addMicroserviceToRegistry(ms));
        inactiveMicroservices.clear();
    }

    @Reference(name = "microservice",
               service = Microservice.class,
               cardinality = ReferenceCardinality.MULTIPLE,
               policy = ReferencePolicy.STATIC,
               unbind = "removeService")
    protected void addService(Microservice service, Map properties) {

        if (isAllRequiredCapabilitiesAvailable()) {
            Object channelId = properties.get(MSF4JConstants.CHANNEL_ID);
            Object contextPath = properties.get(MSF4JConstants.CONTEXT_PATH);
            addMicroserviceToRegistry(service, channelId, contextPath);
        } else {
            //TODO: do some interlocking on THIS-123
            inactiveMicroservices.add(service);
            log.info("Required services are not ready. Not deployng micro-service : " + service);
        }
    }

    protected void removeService(Microservice service, Map properties) {
        //TODO: Implement
    }

    private void addMicroserviceToRegistry(Microservice service, Object channelId, Object contextPath) {

        bridgeServlet.addMicroserviceToRegistry(service);
    }

    private boolean isAllRequiredCapabilitiesAvailable() {

        return bridgeServlet != null;
    }

    @Reference(name = "http.service",
               service = HttpService.class,
               cardinality = ReferenceCardinality.MANDATORY,
               policy = ReferencePolicy.DYNAMIC,
               unbind = "unsetHttpService")
    public void setHttpService(HttpService httpService) {

        System.out.println("Setting HTTP SErvice : " + httpService);
        this.httpService = httpService;
    }

    public void unsetHttpService(HttpService httpService) {

        tearDownMsf4j();
        this.httpService = null;
    }

    /**
     * Closes all MSF4j stuff
     */
    private void tearDownMsf4j() {

        if (bridgeServlet != null) {
            bridgeServlet.destroy();
        }
        bridgeServlet = null;
    }
}
