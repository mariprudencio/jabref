package net.sf.jabref.remote;

import net.sf.jabref.JabRef;
import net.sf.jabref.BasePanel;
import net.sf.jabref.Globals;
import net.sf.jabref.gui.ImportInspectionDialog;
import net.sf.jabref.imports.ParserResult;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Vector;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: alver
 * Date: Aug 14, 2005
 * Time: 8:11:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class RemoteListener extends Thread implements ImportInspectionDialog.CallBack {

    private JabRef jabref;
    private ServerSocket socket;
    private boolean active = true, toStop = false;
    private static final String IDENTIFIER = "jabref";

    public RemoteListener(JabRef jabref, ServerSocket socket) {
        this.jabref = jabref;
        this.socket = socket;
    }

    public void disable() {
        toStop = true;
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        while (active) {
            try {
                Socket newSocket = socket.accept();

                if (toStop) {
                    active = false;
                    return;
                }
                //System.out.println("Connection...");

                OutputStream out = newSocket.getOutputStream();
                InputStream in = newSocket.getInputStream();
                out.write(IDENTIFIER.getBytes());
                out.write('\0');
                out.flush();

                int c;
                StringBuffer sb = new StringBuffer();
                while ((c = in.read()) != '\0') {
                    sb.append((char)c);
                }

                String[] args = sb.toString().split("\n");
                Vector loaded = jabref.processArguments(args, false);

                for (int i=0; i<loaded.size(); i++) {
                    ParserResult pr = (ParserResult) loaded.elementAt(i);
                    if (!pr.toOpenTab()) {
                        jabref.jrf.addTab(pr.getDatabase(), pr.getFile(), pr.getMetaData(), (i == 0));
                    } else {
                        // Add the entries to the open tab.
                        BasePanel panel = jabref.jrf.basePanel();
                        if (panel == null) {
                            // There is no open tab to add to, so we create a new tab:
                            jabref.jrf.addTab(pr.getDatabase(), pr.getFile(), pr.getMetaData(), (i == 0));
                        } else {
                            List entries = new ArrayList(pr.getDatabase().getEntries());
                            jabref.jrf.addImportedEntries(panel, entries, "", false, this);
                        }
                    }
                }
                //System.out.println("Loaded: "+loaded.size());
                in.close();
                out.close();
                newSocket.close();
                //socket.close();
                //socket = new ServerSocket(Globals.prefs.getInt("remoteServerPort"));
            } catch (SocketException ex) {
                active = false;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static RemoteListener openRemoteListener(JabRef jabref) {
        try {
            ServerSocket socket = new ServerSocket(Globals.prefs.getInt("remoteServerPort"));
            RemoteListener listener = new RemoteListener(jabref, socket);
            return listener;
        } catch (IOException e) {
            return null;
        }

    }

    /**
     * Attempt to send command line arguments to already running JabRef instance.
     * @param args Command line arguments.
     * @return true if successful, false otherwise.
     */
    public static boolean sendToActiveJabRefInstance(String[] args) {
        try {
            InetAddress local = InetAddress.getByName("localhost");
            Socket socket = new Socket(local, Globals.prefs.getInt("remoteServerPort"));

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            int c;
            StringBuffer sb = new StringBuffer();
            while ((c = in.read()) != '\0') {
                sb.append((char)c);
            }

            if (!IDENTIFIER.equals(sb.toString())) {
                String error = Globals.lang("Cannot use port %0 for remote operation; another"
                    +"application may be using it. Try specifying another port.",
                        new String[] {String.valueOf(Globals.prefs.getInt("remoteServerPort"))});
                System.out.println(error);
                return false;
            }

            for (int i=0; i<args.length; i++) {
                byte[] bytes = args[i].getBytes();
                out.write(bytes);
                out.write('\n');
            }
            out.write('\0');
            out.flush();
            in.close();
            out.close();
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // This method is called by the dialog when the user has selected the
// wanted entries, and clicked Ok. The callback object can update status
// line etc.
    public void done(int entriesImported) {
        jabref.jrf.output(Globals.lang("Imported entries"));
    }

    // This method is called by the dialog when the user has cancelled the import.
    public void cancelled() {

    }

    // This method is called by the dialog when the user has cancelled or
// signalled a stop. It is expected that any long-running fetch operations
// will stop after this method is called.
    public void stopFetching() {

    }
}