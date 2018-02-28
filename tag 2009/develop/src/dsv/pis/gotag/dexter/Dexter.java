// Dexter.java
// Bailiff excerciser and demo.
// Fredrik Kilander, DSV
// 30-jan-2009/FK Replaced f.show() (deprecated) with f.setVisible();
// 07-feb-2008/FK Code smarted up a bit.
// 18-nov-2004/FK Adapted for PRIS course.
// 2000-12-18/FK Runs for the first time.
// 2000-12-13/FK

package dsv.pis.gotag.dexter;

import java.io.*;
import java.lang.*;
import java.util.Random;
import java.util.*;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;

import net.jini.core.lookup.*;
import net.jini.lookup.*;

import dsv.pis.gotag.util.*;
import dsv.pis.gotag.bailiff.BailiffInterface;

/**
 * Dexter jumps around randomly among the Bailiffs. He is can be used
 * test that the system is operating, or as a template for more
 * evolved agents.
 */
public class Dexter implements Serializable
{
  /**
   * The string name of the Bailiff service interface, used when
   * querying the Jini lookup server.
   */
  protected static final String bfi =
    "dsv.pis.gotag.bailiff.BailiffInterface";

  /**
   * The debug flag controls the amount of diagnostic info we put out.
   */
  protected boolean debug = false;

  /**
   * The noFace flag disables the graphical frame when true.
   */
  protected boolean noFace = false;

  /**
   * name of the dexter client which is actually a unique id
   */
  protected UUID  name;

  /**
   * The baseString is used to generate UUID of the dexter name
   *
   */

  protected static String baseString = "38400000-8cf0-11bd-b23e-10b96e4ef00d";

  /**
   * Dexter uses a ServiceDiscoveryManager to find Bailiffs.
   * The SDM is not serializable so it must recreated on each new Bailiff.
   * That is why it is marked as transient.
   */
  protected transient ServiceDiscoveryManager SDM;

  /**
   * This service template is created in Dexter's constructor and used
   * in the topLevel method to find Bailiffs. The service
   * template IS serializable so Dexter only needs to instantiate it once.
   */
  protected ServiceTemplate bailiffTemplate;

  /**
   * Outputs a diagnostic message on standard output. This will be on
   * the host of the launching JVM before Dexter moves. Once he has migrated
   * to another Bailiff, the text will appear on the console of that Bailiff.
   * @param msg The message to print.
   */
  protected void debugMsg (String msg) {
    if (debug) System.out.println (msg);
  }

  /**
   * This creates a new Dexter. All the constructor needs to do is to
   * instantiate the service template.
   * @param debug True if this instance is being debugged.
   * @throws ClassNotFoundException Thrown if the class for the Bailiff
   * service interface could not be found.
   */

  AtomicBoolean isIt = new AtomicBoolean(false);
  AtomicBoolean isMigrating = new AtomicBoolean(false);

  public Dexter (boolean debug, boolean noFace, UUID name)
  throws
  java.lang.ClassNotFoundException
  {
    if (this.debug == false) this.debug = debug;

    this.noFace = noFace;
    this.name = name;

    // This service template is used to query the Jini lookup server
    // for services which implement the BailiffInterface. The string
    // name of that interface is passed in the bfi argument. At this
    // point we only create and configure the service template, no
    // query has yet been issued.

    bailiffTemplate =
      new ServiceTemplate
	(null,
	 new Class [] {java.lang.Class.forName (bfi)},
	 null);
  }

  /**
   * Sleep snugly and safely not bothered by interrupts.
   * @param ms  The number of milliseconds to sleep.
   */
  protected void snooze (long ms) {
    try {
      Thread.currentThread ().sleep (ms);
    }
    catch (java.lang.InterruptedException e) {}
  }

  /**
   * This is Dexter's main program once he is on his way. In short, he
   * gets himself a service discovery manager and asks it about Bailiffs.
   * If the list is long enough, he then selects one randomly and pings it.
   * If the ping returned without a remote exception, Dexter then tries
   * to migrate to that Bailiff. If the ping or the migrates fails, Dexter
   * gives up on that Bailiff and tries another.
   */
  public void topLevel (Boolean isIt)
    throws
      java.io.IOException
  {
    Random rnd = new Random ();

    // Create a Jini service discovery manager to help us interact with
    // the Jini lookup service.
    SDM = new ServiceDiscoveryManager (null, null);

    DexterFace dexFace = null;
    JFrame f = null;
    this.isIt.set(isIt);

    if (!noFace) {
      // Create a small GUI for this Dexter instance.
      f = new JFrame ("Dexter");
      f.addWindowListener (new WindowAdapter () {
	  public void windowClosing (WindowEvent e) {System.exit (0);}
	});
      dexFace = new DexterFace ();
      f.getContentPane ().add ("Center", dexFace);
      dexFace.init ();
      f.pack ();
      f.setSize (new Dimension (356, 292));
      f.setVisible (true);
      dexFace.startAnimation ();
    }

    for (;;) {

      ServiceItem [] svcItems;

      long retryInterval = 0;

      // The restraint sleep is just there so we don't get hyperactive
      // and confuse the slow human beings.

      debugMsg ("Entering restraint sleep.");

      snooze (5000);

      debugMsg ("Leaving restraint sleep.");

      // Enter a loop in which Dexter tries to find some Bailiffs.

      do {

	if (0 < retryInterval) {
	  debugMsg ("No Bailiffs detected - sleeping.");
	  snooze (retryInterval);
	  debugMsg ("Waking up.");
	}

	// Put our query, expressed as a service template, to the Jini
	// service discovery manager. 

	svcItems = SDM.lookup (bailiffTemplate, 8, null);
	retryInterval = 20 * 1000;

	// If no lookup servers are found, go back up to the beginning
	// of the loop, sleep a bit and then try again.
      } while (svcItems.length == 0);
      
      // We have the Bailiffs.

      debugMsg ("Found " + svcItems.length + " Bailiffs.");

      //Entry[] serviceAtts = svcItem.attributeSets; // Get service attributes

      // Now enter a loop in which we try to ping and migrate to them.

      int nofItems = svcItems.length;

      // While we still have at least one Bailiff service to try...

      while (0 < nofItems) {

	// Select one Bailiff randomly.

	    int idx = 0;
	    if (1 < nofItems) {
	        idx = rnd.nextInt (nofItems);
	    }

	    boolean accepted = false;	    // Assume it will fail
	    Object obj = svcItems[idx].service; // Get the service object
	    BailiffInterface bfi = null;

	    // Try to ping the selected Bailiff.

	    debugMsg ("Trying to ping...");

	    try {
	        if (obj instanceof BailiffInterface) {
	            bfi = (BailiffInterface) obj;
	            String response = bfi.ping (); // Ping it
                //String current_bailiff = bfi.getProperty();
	            debugMsg (response);
	            accepted = true;	// Oh, it worked!
	        }
	    }
	catch (java.rmi.RemoteException e) { // Ping failed
	  if (debug) {
	    e.printStackTrace ();
	  }
	}

	debugMsg (accepted ? "Accepted." : "Not accepted.");

	// If the ping failed, delete that Bailiff from the array and
	// try another. The current (idx) entry in the list of service items
	// is replaced by the last item in the list, and the list length
	// is decremented by one.

	if (accepted == false) { 
	  svcItems[idx] = svcItems[nofItems - 1];
	  nofItems -= 1;
	  continue;		// Back to top of while-loop.
	}
	else {

	  debugMsg ("Trying to jump...");


      /*boolean isArrived = false;
      String bailiffRoom = bfi.getProperty("room");
      for (int i = 0; i < serviceAtts.length; i++) { // For each attribute
          if (atts[i] instanceof Location) { // If it is Location ..
            Location loc = (Location) atts[i];
            isArrived = loc.floor.equalsIgnoreCase(bailiffRoom); // This host?
            if (isArrived == true) {
                break;
            }
          }
      }
      */
      // This is the spot where Dexter tries to migrate.
	  /* try {
          //debugMsg ("name of client  : " + this.name);
          bfi.migrate (this, "topLevel", new Object [] {});
          String agents = bfi.getAgentNames();

          debugMsg ("available agents from client ---> : " + agents);
          //Arrays.asList(current_agents).contains(this.name);
          if (agents.size() > 0) {
              //String [] current_agents = agents.split(',');
              //debugMsg("name of clients===> : " + agents);
              boolean checkIt = false;
              Iterator iter = agents.iterator();
              //for (int i = 0; i<agents.length; i++) {
              //for (String s : set) {
              while(iter.hasNext()){
                  String s = (String)iter.next();
                  if (s == this.name) {
                      debugMsg("Agent name inside check it true  : "+ this.name);
                      checkIt = true;
                      break;
                  }
              }
              if (checkIt){
                  boolean foundIt = false;
                  //Object o = null;
                  //for (String agent : current_agents) {
                      //o = bfi.isIt(agent);
                      //if(o)
                  //}
              }
              debugMsg("check it value : "+ checkIt);
          }
          */
        try {
            // TODO : Remove debugging
            ArrayList<UUID> agentsList = bfi.getAgentsNames();
            debugMsg("List of agents | Size = " + agentsList.size());
            for (int i = 0; i < agentsList.size(); ++i) {
                try {
                    debugMsg("Agent " + i + " : " + agentsList.get(i)
                            + " | isIt = " + (bfi.isIt(agentsList.get(i)) ? "YES" : "NO"));
                } catch ( Exception ex) {
                    debugMsg("Agent " + i + " : " + agentsList.get(i));
                }
            }

            // If it => try to it a player agent in the local bailiff
            if (this.isIt.get()) {
                debugMsg("\n[IT Agent] In action");

                // 3) Try to it one agent in the local bailiff
                //ArrayList<UUID> agentsList = bfi.getAgentsNames();
                debugMsg("Nb agent in local bailiff = " + agentsList.size());

                int nbAgents = agentsList.size();
                int nbAttempts = 0;
                while (this.isIt.get() && (nbAttempts < nbAgents - 1)) { // -1 => do not count the it agent
                    nbAttempts++;

                    UUID agent = agentsList.get(rnd.nextInt(nbAgents));

                    // Avoid to it ourselves
                    if (agent.equals(this.name))
                        continue;

                    // Try to it the agent
                    try {
                        debugMsg("current agent that is Trying to it " + agent + " !");
                        if (bfi.agentHasIt(agent)) {
                            // It successfull
                            this.isIt.compareAndSet(true, false);
                            debugMsg("[IT SUCCEEDED] Agent succeeded to it agent " + agent + " !");
                            if (debug)
                                System.out.println();
                            continue;
                        }
                    } catch (Exception e) { }
                }
            }

            debugMsg(this + " trying to migrate...");

            this.isMigrating.set(true);
            bfi.migrate(this, "topLevel", new Object[]{isIt});
            this.isMigrating.compareAndSet(true, false);

            debugMsg(this + " migrated...");
            SDM.terminate();    // SUCCESS
            if (!noFace) {
                dexFace.stopAnimation();
                f.setVisible(false);
            }
            return;        // SUCCESS
        } catch (java.rmi.RemoteException | java.lang.NoSuchMethodException e ) { // FAILURE
            if (debug) {
                e.printStackTrace();
            }
            this.isMigrating.compareAndSet(true, false);
        }

	  debugMsg ("Didn't make the jump...");

	}
      }	// while there are candidates left

      debugMsg ("They were all bad.");

    } // for ever // go back up and try to find more Bailiffs
  }


  public boolean isIt() {
      return isIt.get();
  }


  public boolean agentHasIt() {
      // If is migrating, cannot be it
      if (isMigrating.get()) {
          debugMsg("dexter isMigrating.get() : " + isMigrating.get());
          return false;
      }

      // Otherwise, become the it agent
      return isIt.compareAndSet(false, true);
  }

    /**
   * The main program of Dexter. It is only used when a Dexter is launched.
   */
  public static void main (String [] argv)
    throws
      java.lang.ClassNotFoundException,
      java.io.IOException
  {
    CmdlnOption helpOption  = new CmdlnOption ("-help");
    CmdlnOption debugOption = new CmdlnOption ("-debug");
    CmdlnOption noFaceOption = new CmdlnOption ("-noface");

    CmdlnOption [] opts =
      new CmdlnOption [] {helpOption, debugOption, noFaceOption};

    String [] restArgs = Commandline.parseArgs (System.out, argv, opts);

    if (restArgs == null) {
      System.exit (1);
    }

    if (helpOption.getIsSet () == true) {
      System.out.println ("Usage: [-help]|[-debug][-noface]");
      System.out.println ("where -help shows this message");
      System.out.println ("      -debug turns on debugging.");
      System.out.println ("      -noface disables the GUI.");
      System.exit (0);
    }

    // creating UUID
    UUID uid = UUID.fromString(baseString);

    boolean debug = debugOption.getIsSet ();
    boolean noFace = noFaceOption.getIsSet ();
      UUID name = uid.randomUUID();
      UUID name1 = uid.randomUUID();
      UUID name2 = uid.randomUUID();

    // We will try without it first
    // System.setSecurityManager (new RMISecurityManager ());
    Dexter dx = new Dexter (debug, noFace, name);
    Dexter dx1 = new Dexter (debug, noFace, name1);
    Dexter dx2 = new Dexter (debug, noFace, name2);

    dx.topLevel (true);
    dx1.topLevel (false);
    dx2.topLevel (false);
    System.exit (0);
  }
}
