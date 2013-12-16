/**
 * 
 */
package org.cohorte.ecf.provider.jabsorb;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.cohorte.remote.utilities.BundlesClassLoader;
import org.eclipse.ecf.core.util.ECFException;
import org.eclipse.ecf.remoteservice.IRemoteCall;
import org.eclipse.ecf.remoteservice.client.AbstractClientContainer;
import org.eclipse.ecf.remoteservice.client.AbstractClientService;
import org.eclipse.ecf.remoteservice.client.IRemoteCallable;
import org.eclipse.ecf.remoteservice.client.RemoteServiceClientRegistration;
import org.jabsorb.ng.client.Client;
import org.jabsorb.ng.client.ISession;
import org.jabsorb.ng.client.TransportRegistry;

/**
 * Jabsorb remote service client
 * 
 * @author Thomas Calmant
 */
public class JabsorbClientService extends AbstractClientService {

    /** The Jabsorb client */
    private final Client pClient;

    /** Service interfaces */
    private final List<Class<?>> pInterfaces = new LinkedList<>();

    /** A classloader that walks through bundles */
    private final ClassLoader pLoader;

    /** The service proxy */
    private final Object pProxy;

    /**
     * Sets up the client
     * 
     * @param aContainer
     * @param aRegistration
     * @throws Exception
     *             Error preparing the proxy
     */
    public JabsorbClientService(final AbstractClientContainer aContainer,
            final RemoteServiceClientRegistration aRegistration)
            throws ECFException {

        super(aContainer, aRegistration);

        // Setup the class loader
        pLoader = new BundlesClassLoader(Activator.getContext());

        // Setup the client
        pClient = setupClient(aRegistration);

        // Setup the proxy
        pProxy = createProxy();
    }

    /**
     * Creates a proxy for this service
     * 
     * @return A proxy object
     * @throws ECFException
     *             Error generating the endpoint name, or no service interface
     *             found
     */
    private Object createProxy() throws ECFException {

        // Load service classes
        pInterfaces.clear();
        for (String className : registration.getClazzes()) {
            try {
                pInterfaces.add(pLoader.loadClass(className));

            } catch (ClassNotFoundException ex) {
                // Ignore unknown class
                System.err.println("Class not loaded: " + className);
            }
        }

        // If not class has been loaded, raise an error
        if (pInterfaces.isEmpty()) {
            throw new ECFException("No class found in: "
                    + Arrays.toString(registration.getClazzes()));
        }

        // Create the proxy
        return pClient.openProxy(getEndpointName(), pLoader,
                pInterfaces.toArray(new Class<?>[0]));
    }

    /**
     * Clean up
     * 
     * @see org.eclipse.ecf.remoteservice.AbstractRemoteService#dispose()
     */
    @Override
    public void dispose() {

        // Close the proxy
        pClient.closeProxy(pProxy);

        // Clean up the list of classes
        pInterfaces.clear();

        super.dispose();
    }

    /**
     * Extracts the endpoint name from properties
     * 
     * @return The endpoint name
     * @throws ECFException
     *             Can't find/generate an endpoint name
     */
    private String getEndpointName() throws ECFException {

        // Custom name
        String endpointName = (String) registration
                .getProperty(JabsorbConstants.ENDPOINT_NAME);
        if (endpointName == null || endpointName.isEmpty()) {
            // Get the remote service ID
            Long svcId = (Long) registration
                    .getProperty(JabsorbConstants.ENDPOINT_SERVICE_ID);
            if (svcId == null || svcId == 0) {
                // No service ID given
                throw new ECFException("No endpoint name given");
            }

            // Generated name
            endpointName = "service_" + svcId;
        }

        return endpointName;
    }

    /**
     * Looks for the first method in the service interfaces that has the same
     * name and number of arguments.
     * 
     * @param aMethodName
     *            A method name
     * @return A method object or null
     */
    private Method getMethod(final String aMethodName, final int aNbArgs) {

        for (Class<?> clazz : pInterfaces) {
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                // Test method name and number of arguments
                // Interface methods are public, so no need to check for them
                if (method.getName().equals(aMethodName)
                        && method.getParameterTypes().length == aNbArgs) {
                    // Found a match
                    return method;
                }
            }
        }

        // Method not found
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.eclipse.ecf.remoteservice.client.AbstractClientService#invokeRemoteCall
     * (org.eclipse.ecf.remoteservice.IRemoteCall,
     * org.eclipse.ecf.remoteservice.client.IRemoteCallable)
     */
    @Override
    protected Object invokeRemoteCall(final IRemoteCall aCall,
            final IRemoteCallable aCallable) throws ECFException {

        // Look for the method
        Method method = getMethod(aCall.getMethod(),
                aCall.getParameters().length);
        if (method == null) {
            throw new ECFException("Can't find a method called "
                    + aCall.getMethod() + " with "
                    + aCall.getParameters().length + " arguments");
        }

        try {
            // Call the method
            return pClient.invoke(pProxy, method, aCall.getParameters());

        } catch (Throwable ex) {
            // Encapsulate the exception
            throw new ECFException("Error calling remote method: "
                    + ex.getMessage(), ex);
        }
    }

    /**
     * Sets up the client according to the registration
     * 
     * @param aRegistration
     */
    private Client setupClient(
            final RemoteServiceClientRegistration aRegistration) {

        // Get the URI
        String uri = (String) aRegistration
                .getProperty(JabsorbConstants.JABSORB_URI);

        // Prepare the session
        final ISession session = TransportRegistry.i().createSession(uri);

        // Set up the client
        return new Client(session, pLoader);
    }
}
