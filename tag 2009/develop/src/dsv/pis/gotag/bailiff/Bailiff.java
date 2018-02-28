// Bailiff.java -
// Fredrik Kilander, DSV (fk@dsv.su.se)
// 18-nov-2004/FK Adapted for the PRIS course.
// 2000-12-12/FK Rewrite for Java 1.3 and Jini 1.1
// 19 May 1999/FK 

package dsv.pis.gotag.bailiff;
import dsv.pis.gotag.dexter.Dexter;

import java.lang.*;
import java.io.*;
import java.net.*;
import java.rmi.*;
import java.rmi.server.*;
import java.util.*;

import net.jini.core.entry.*;
import net.jini.core.lookup.*;
import net.jini.core.discovery.*;
import net.jini.lease.*;
import net.jini.lookup.*;
import net.jini.lookup.entry.*;

import dsv.pis.gotag.util.*;

/**
 * The Bailiff is a Jini service that provides an execution environment
 * for agents. The service it provides is this:
 * A serializable class may call the Bailiff's migrate() method to
 * transfer itself to the JVM of the Bailiff and there gain its own
 * thread of execution in a callback procedure of its own choice.
 * <p>
 * The Bailiff is not mobile. Once started, the Jini JoinManager keeps
 * it alive.
 * <p>
 * [bail-iff n 1 law officer who helps a sheriff in issuing writs and
 * making arrests. 2 (Brit.) landlord's agent or steward; manager of
 * an estate or farm. 3 (US) official in a lawcourt, esp one who takes
 * people to their seats and announces the arrival of the judge.]
 *
 * @author Fredrik Kilander, DSV
 */
public class Bailiff extends java.rmi.server.UnicastRemoteObject    // for RMI
        implements
        dsv.pis.gotag.bailiff.BailiffInterface // for clients
{
    protected boolean debug = false;
    protected Logger log;
    protected String user;
    protected String room;
    protected String host;
    protected Map propertyMap;
    //protected Map clientObjectMap;
    protected JoinManager bf_joinmanager;
    protected InetAddress myInetAddress;

    HashMap<UUID, agitator> clientObjectMap = new HashMap<UUID, agitator>();

    protected void debugMsg(String s) {
        if (debug) {
            System.out.println(s);
        }
    }

    /**
     * Returns the room name string of this Bailiff.
     *
     * @return The room name string.
     */
    public String getRoom() {
        return room;
    }

    /**
     * Returns the user name string of this Bailiff.
     *
     * @return The user name string.
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the host name string of this Bailiff
     *
     * @return The host name string.
     */
    public String getHost() {
        return host;
    }

    /**
     * A helper class to receive callbacks from the JoinManager.
     *
     * @author Fredrik Kilander, DSV
     * @see JoinManager
     * @see ServiceIDListener
     */
    private class IDListener
            implements
            ServiceIDListener    // for JoinManager
    {
        /**
         * The ServiceID returned by the JoinManager.
         */
        protected ServiceID myServiceID;

        /**
         * Creates a new IDListener.
         */
        public IDListener() {
        }

        /**
         * This method is called by the JoinManager once it has registered
         * the service with a Lookup server and obtained a service ID.
         *
         * @param sidIn The service's ServiceID.
         */
        public void serviceIDNotify(ServiceID sidIn) {
            myServiceID = sidIn;
            if (debug) {
                debugMsg("serviceIDNotify sid='" + myServiceID + "'");
                log.entry("<serviceIDNotify sidIn=\"" + sidIn + "\"/>");
            }
        }

        /**
         * Returns the ServiceID.
         *
         * @return The ServiceID or null if not set.
         */
        public ServiceID getServiceID() {
            return myServiceID;
        }
    } // IDListener

    /**
     * This class wraps and encapsulates the remote object to which the
     * Bailiff lends a thread of execution.
     */
    private class agitator extends Thread {

        protected Dexter myObj;    // The client object
        protected String myCb;    // The name of the entry point method
        protected Object[] myArgs;    // Arguments to the entry point method
        protected java.lang.reflect.Method myMethod; // Ref. to entry point method
        protected java.lang.reflect.Field myField; // Ref. to agent id
        protected Class[] myParms; // Class reflection of arguments
        protected UUID myName;

        /**
         * The baseString is used to generate UUID of the dexter name
         *
         */

        //protected static String baseString = "38400000-8cf0-11bd-b23e-10b96e4ef00d";
        /**
         * Creates a new agitator by copying th references to the client
         * object, the name of the entry method and the arguments to
         * the entry method.
         *
         * @param obj  The client object, holding the method to execute
         * @param cb   The name of the entry point method (callback)
         * @param args Arguments to the entry point method
         */
        public agitator(Dexter obj, String cb, Object[] args) {
            myObj = obj;
            myCb = cb;
            myArgs = args;

            try {
                myField = myObj.getClass().getDeclaredField("name");
                myField.setAccessible(true);
                myName= (UUID)myField.get(myObj);
                //myName = (String)value;
                //debugMsg("user id ==========> : " + value);
            } catch (NoSuchFieldException x) {
                x.printStackTrace();
            } catch (IllegalAccessException x) {
                x.printStackTrace();
            }

            //UUID uid = UUID.fromString(baseString);
            //myName = uid.randomUUID().toString();;

            //clientObjectMap.put(myName, myObj);
            //debugMsg("user id : " + myName);

            // If the array of arguments are non-zero we must create an array
            // of Class so that we can match the entry point method's name with
            // the parameter signature. So, the myParms[] array is loaded with
            // the class of each entry point parameter.

            if (0 < args.length) {
                myParms = new Class[args.length];
                for (int i = 0; i < args.length; i++) {
                    myParms[i] = args[i].getClass();
                }
            } else {
                myParms = null;
            }

        }

        /**
         * This method locates the method that is the client object's requested
         * entry point. It also sets the classloader of the current instance
         * to follow the client's classloader.
         *
         * @throws NoSuchMethodException Thrown if the entry point specified
         *                               in the constructor can not be found.
         */
        public void initialize() throws java.lang.NoSuchMethodException {
            myMethod = myObj.getClass().getMethod(myCb, myParms);
            setContextClassLoader(myObj.getClass().getClassLoader());
        }

        public boolean isIt() {
            return myObj.isIt();
        }

        public boolean agentHasIt() {
            return myObj.agentHasIt();
        }
        /**
         * Overrides the default run() method in class Thread (a superclass to
         * us). Then we invoke the requested entry point on the client object.

        public void run() {
            try {
                //Thread th = Thread.currentThread();
                //String tName = th.getName();
                //debugMsg("current thread name : "+ nameT);
                myMethod.invoke(myObj, myArgs);

                clientObjectMap.remove(myName);
                debugMsg("removing agent : "+ myName);
                //debugMsg("is "+ nameT + " alive : " + t.isAlive() + "in room : " + propertyMap.get("room"));
            } catch (Throwable t) {
                if (debug) {
                    log.entry(t);
                }
            }

            //debugMsg("available cients ====>: " + clientObjectMap.keySet() + " in bailif : " + propertyMap.get("room"));
            //debugMsg("Removing client  : " + myName);
            //if (!this.isAlive()) {

            //}
                debugMsg("cients list ====> : " + clientObjectMap.keySet() + " in bailif : " + propertyMap.get("room"));
        }
    } // class agitator
         */

        public void run() {

            synchronized (clientObjectMap) {
                while (clientObjectMap.containsKey(myName)) {
                    try {
                        // We need to wait in case an agitator this agitator will migrate in the same
                        // bailiff
                        clientObjectMap.wait();
                    } catch (InterruptedException e) {
                        log.entry("[InterruptedException] agitator " + myName);
                    }
                }

                // Add the agent in the list of current agents
                clientObjectMap.put(myName, this);
                //debugMsg("[" + id + "] Start running");
                //debugMsg("[" + id + "] " + localAgents.toString());
            }

            try {
                myMethod.invoke(myObj, myArgs);
            } catch (Throwable t) {
                if (debug) {
                    log.entry(t);
                }
            } finally {
                synchronized (clientObjectMap) {
                    // Remove the agent from list of current agents
                    clientObjectMap.remove(myName);
                    //debugMsg("[" + id + "] " + localAgents.toString());
                    //debugMsg("[" + id + "] End running");

                    // Notify a possible waiting thread
                    clientObjectMap.notify();
                }
            }
        }
    } // class agitator

    // In BailiffInterface:

    /**
     * Returns a string acknowledging the host, IP address, room and user
     * fields of this Bailiff instance. This method can be used to debug
     * the identity of the Bailiff from a client and to verify that the
     * connection is still operational.
     *
     * @throws RemoteException
     * @returns The ping response.
     */
    public String ping() throws java.rmi.RemoteException {
        if (debug) {
            log.entry("<ping/>");
        }

        return ("Ping echo from Bailiff on host=" + host
                + " [" + myInetAddress.getHostAddress() + "] "
                + " room=" + room
                + " user=" + user
                + ".");
    }

    // In BailiffInterface:

    /**
     * Returns the string property stored under key.
     *
     * @param key The key to look up.
     * @returns The property value.
     */
    public String getProperty(String key) {
        if (debug) {
            log.entry("<getProperty key=\"" + key + "\"/>");
        }
        return (String) propertyMap.get(key.toLowerCase());
    }

    // In BailiffInterface:

    /**
     * Sets the property value to be stored under key.
     *
     * @param key   The name of the property.
     * @param value The value of the property.
     */
    public void setProperty(String key, String value) {
        if (debug) {
            log.entry("<setProperty key=\""
                    + key.toLowerCase() + "\" value=\"" + value + "\"/>");
        }
        propertyMap.put(key.toLowerCase(), value);
    }

    /**
     Get the available agent names in an array
    */

    // In BailiffInterface:

    @Override
    public ArrayList<UUID> getAgentsNames() throws RemoteException {
        Set<UUID> keySet = clientObjectMap.keySet();
        return new ArrayList<>(keySet);
    }


    @Override
    public boolean isIt(UUID name) throws RemoteException {
        // Is the agent in the Bailiff ?
        //debugMsg("Try to find UUID: [" + name.toString() + "] in " + localAgents.toString());

        return clientObjectMap.get(name).isIt();
    }

    @Override
    public boolean agentHasIt(UUID name) throws RemoteException{
        // Is the agent in the Bailiff ?
        //debugMsg("Try to find UUID: [" + name.toString() + "] in " + localAgents.toString());

        boolean res = clientObjectMap.get(name).agentHasIt();
        if (res)
            log.entry("<it agent=\"" + name + "\"/>");

        return res;
    }


    /**
     * Entry point for remote clients who want to pass an object to be
     * executed by the Bailiff. The Bailiff starts a new thread for the
     * object and calls the specified entry (callback) method. When that
     * method returns, the thread exits and the object becomes inert.
     *
     * @param obj  The object to execute.
     * @param cb   The name of the entry (callback) method to call.
     * @param args Array of arguments to the entry method. The elements in
     *             the array must match the entry method's signature.
     * @throws NoSuchMethodException Thrown if the specified entry method
     *                               does not exist with the expected signature.
     */
    public void migrate(Dexter obj, String cb, Object[] args)
            throws
            java.rmi.RemoteException,
            java.lang.NoSuchMethodException {
        if (debug) {
            log.entry("<migrate obj=\"" + obj + "\" cb=\"" + cb
                    + "\" args=\"" + args + "\"/>");
        }
        agitator agt = new agitator(obj, cb, args);
        agt.initialize();
        //agt.setName(agt.myName);
        //debugMsg("before start is "+ name + " alive : " + agt.isAlive());
        agt.start();
        //debugMsg("is "+ name + " alive : " + agt.isAlive());
    }

    /**
     * Creates a new Bailiff service instance.
     *
     * @param room  Informational text field used to designate the 'room'
     *              (physical or virtual) the Bailiff is running in.
     * @param user  Information text field used to designate the 'user'
     *              who is associated with the Bailiff instance.
     * @param debug If true, diagnostic messages will be logged to the
     *              provided Logger instance. This parameter is overridden if the
     *              class local debug variable is set to true in the source code.
     * @param log   If debug is true, this parameter can be a Logger instance
     *              configured to accept entries. If log is null a default Logger instance
     *              is created.
     * @throws RemoteException
     * @throws UnknownHostException Thrown if the local host address can not
     *                              be determined.
     * @throws IOException          Thrown if there is an I/O problem.
     */
    public Bailiff(String room, String user, boolean debug, Logger log)
            throws
            java.rmi.RemoteException,
            java.net.UnknownHostException,
            java.io.IOException {
        this.log = (log == null) ? new Logger() : log;
        this.user = user;
        this.room = room;
        myInetAddress = java.net.InetAddress.getLocalHost();
        host = myInetAddress.getHostName().toLowerCase();
        this.debug = (this.debug == true) ? true : debug;

        propertyMap = Collections.synchronizedMap(new HashMap());
        propertyMap.put("hostname", host);
        propertyMap.put("hostaddress", myInetAddress.getHostAddress());
        propertyMap.put("room", room);

        clientObjectMap = new HashMap();

        log.entry("STARTING host=" + host + ", room=" + room + ", user="
                + user + ", debug=" + debug + ".");

        // Create Jini service attributes.

        Entry[] bf_attributes =
                new Entry[]{
                        new Name("Bailiff"),
                        new Location(host, room, user)
                        //      ,
                        //	new BailiffServiceType (host, room, user)
                };

        // Create a Jini JoinManager that will help us to register ourselves
        // with all discovered Jini lookup servers.
        bf_joinmanager = new JoinManager
                (
                        this,            // the service object
                        bf_attributes,        // the attribute sets
                        new IDListener(),    // Service ID callback
                        null,            // Default Service Discovery Manager
                        null            // Default Lease Renewal Manager
                );
        //setProperty(idListner.getServiceID(), room);
    }

    /**
     * Shuts down this Bailiff service.
     */
    public void shutdown() {
        bf_joinmanager.terminate();
    }

    /**
     * Returns a string representation of this service instance.
     *
     * @returns A string representing this Bailiff instance.
     */
    public String toString() {
        return
                "Bailiff for user " +
                        user + " in room " + room + " on host " + host + ".";
    }

    /**
     * This is the main program of the Bailiff launcher. It starts the
     * Bailiff and registers it with the Jini lookup server(s).
     * When the main routine exits the JVM will
     * keep on running because the JoinManager will be running and referring
     * to the Bailiff. There may also be agitator threads active. Some house-
     * holding counters and a shutdown method are attractive extensions.
     *
     * @param args The array of commandline strings, Java standard.
     * @throws java.net.UnknownHostException Thrown if the name of the
     *                                       local host cannot be obtained.
     * @throws java.rmi.RemoteException      Thrown if there is a RMI problem.
     * @throws java.io.IOException           Thrown if discovery/join could not start.
     * @see BailiffServiceID
     * @see Bailiff_svc
     */
    public static void main(String[] argv)
            throws
            java.net.UnknownHostException,
            java.rmi.RemoteException,
            java.io.IOException {
        String room = "anywhere";
        String user = System.getProperty("user.name");
        boolean debug = false;

        CmdlnOption helpOption = new CmdlnOption("-help");
        CmdlnOption noFrameOption = new CmdlnOption("-noframe");
        CmdlnOption debugOption = new CmdlnOption("-debug");
        CmdlnOption roomOption = new CmdlnOption("-room",
                CmdlnOption.OPTIONAL |
                        CmdlnOption.PAR_REQ);
        CmdlnOption userOption = new CmdlnOption("-user",
                CmdlnOption.OPTIONAL |
                        CmdlnOption.PAR_REQ);
        CmdlnOption logOption = new CmdlnOption("-log",
                CmdlnOption.OPTIONAL |
                        CmdlnOption.PAR_OPT);

        CmdlnOption[] opts =
                new CmdlnOption[]{helpOption,
                        debugOption,
                        roomOption,
                        userOption,
                        logOption};

        String[] restArgs = Commandline.parseArgs(System.out, argv, opts);

        if (restArgs == null) {
            System.exit(1);
        }

        if (helpOption.getIsSet() == true) {
            System.out.println
                    ("Usage: [-room room][-user user][-debug][-log [logfile]]");
            System.out.print("Where room is location of the service ");
            if (room == null) {
                System.out.println("(no default).");
            } else {
                System.out.println("(default = '" + room + "').");
            }

            System.out.print("      -user specifies the owning user ");
            if (user == null) {
                System.out.println("(no default available).");
            } else {
                System.out.println("(default = '" + user.toLowerCase() + "').");
            }

            System.out.println("      -debug turns on debugging mode.");
            System.out.println("      -log turns on logging to file.");

            System.exit(0);
        }

        debug = debugOption.getIsSet();

        if (roomOption.getIsSet() == true) {
            room = roomOption.getValue().toLowerCase();
        }

        if (userOption.getIsSet() == true) {
            user = userOption.getValue().toLowerCase();
        }

        Logger log;

        if (logOption.getIsSet() == true) {
            String lg = logOption.getValue();
            log =
                    (lg != null) ? new Logger(lg, true) : new Logger(".", "Bailiff");
        } else {
            log = new Logger();
        }

        // Set the RMI security manager.
        System.setSecurityManager(new RMISecurityManager());
        Bailiff bf = new Bailiff(room, user, debug, log);
        if (noFrameOption.getIsSet() == false) {
            BailiffFrame bff = new BailiffFrame(bf);
        }
    } // main

} // public class Bailiff

