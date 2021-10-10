package bt;

import bt.async.AsyncException;
import bt.async.Data;
import bt.console.output.styled.Style;
import bt.db.statement.result.SqlResultSet;
import bt.remote.socket.ObjectClient;
import bt.remote.socket.Server;
import bt.remote.socket.ServerClient;
import bt.remote.socket.data.DataProcessor;
import bt.remote.socket.evnt.mcast.MulticastClientKilled;
import bt.remote.socket.evnt.mcast.MulticastClientStarted;
import bt.remote.socket.evnt.mcast.UnspecifiedMulticastClientException;
import bt.remote.socket.evnt.server.*;
import bt.runtime.InstanceKiller;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class BtServerConsole implements Killable, DataProcessor
{
    private Server server;
    private Scanner input;
    private int port;

    public BtServerConsole(int port)
    {
        this.port = port;

        try
        {
            init();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        handleInput();
    }

    public void init() throws IOException
    {
        System.out.println("\r\n====================================================="
                                   + "\r\n______ _   _____                       _      "
                                   + "\r\n| ___ \\ | /  __ \\                     | |     "
                                   + "\r\n| |_/ / |_| /  \\/ ___  _ __  ___  ___ | | ___ "
                                   + "\r\n| ___ \\ __| |    / _ \\| '_ \\/ __|/ _ \\| |/ _ \\"
                                   + "\r\n| |_/ / |_| \\__/\\ (_) | | | \\__ \\ (_) | |  __/"
                                   + "\r\n\\____/ \\__|\\____/\\___/|_| |_|___/\\___/|_|\\___|"
                                   + "\r\n=====================================================");

        this.server = new Server(this.port)
        {
            @Override
            protected ServerClient createClient(Socket socket) throws IOException
            {
                ServerClient client = super.createClient(socket);
                client.setDataProcessor(BtServerConsole.this);
                client.setSingleThreadProcessing(true);

                return client;
            }
        };

        this.server.getEventDispatcher().subscribeTo(ServerKilled.class, e -> printMessage("Server stopped listening on port %s",
                                                                                           Style.apply(e.getServer().getPort() + "", "yellow")));
        this.server.getEventDispatcher().subscribeTo(ServerStarted.class, e -> printMessage("Server started listening on port %s",
                                                                                            Style.apply(e.getServer().getPort() + "", "yellow")));
        this.server.getEventDispatcher().subscribeTo(NewClientConnection.class, e -> printMessage("New connection to %s:%s",
                                                                                                  formatClientPort(e)));
        this.server.getEventDispatcher().subscribeTo(RemovedClientConnection.class, e -> printMessage("Connection to %s:%s ended",
                                                                                                      formatClientPort(e)));
        this.server.getEventDispatcher().subscribeTo(MulticastClientStarted.class, e -> printMessage("Multicast client started listening on %s:%s",
                                                                                                     Style.apply(e.getClient().getMulticastGroup().getHostAddress(), "yellow"),
                                                                                                     Style.apply(e.getClient().getPort() + "", "yellow")));
        this.server.getEventDispatcher().subscribeTo(MulticastClientKilled.class, e -> printMessage("Multicast client stopped listening on %s:%s",
                                                                                                    Style.apply(e.getClient().getMulticastGroup().getHostAddress(), "yellow"),
                                                                                                    Style.apply(e.getClient().getPort() + "", "yellow")));
        this.server.getEventDispatcher().subscribeTo(UnspecifiedServerException.class, e -> printMessageAndStackTrace("Error", e.getException()));
        this.server.getEventDispatcher().subscribeTo(UnspecifiedMulticastClientException.class, e -> printMessageAndStackTrace("Error", e.getException()));

        this.server.setupMultiCastDiscovering();
        this.server.setName("BtServerConsole:" + this.port);

        this.server.start();
        this.input = new Scanner(System.in);
        InstanceKiller.killOnShutdown(this);
    }

    private String[] formatClientPort(ServerClientEvent e)
    {
        return new String[]
                {
                        Style.apply(e.getClient().getHost(), "-red", "yellow"),
                        Style.apply(e.getClient().getPort() + "", "-red", "yellow")
                };
    }

    protected void handleInput()
    {
        boolean continueToRun = true;
        while (continueToRun)
        {
            String cmd = this.input.nextLine().trim();

            if (cmd.equalsIgnoreCase("exit"))
            {
                continueToRun = false;
                continue;
            }

            try
            {
                for (var client : this.server.getClients())
                {
                    Object response = ((ObjectClient)client).send(cmd).get();

                    if (response != null)
                    {
                        if (response instanceof Throwable)
                        {
                            ((Throwable)response).printStackTrace();
                        }
                        else if (response instanceof SqlResultSet)
                        {
                            SqlResultSet set = (SqlResultSet)response;

                            System.out.println(set.toString(new String[] { "green", "bold" },
                                                            new String[] { "white" }));
                        }
                        else
                        {
                            System.out.println(response);
                        }
                    }
                }
            }
            catch (AsyncException e)
            {
                printErrorMessage("Request timed out.");
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    private void printMessageAndStackTrace(String message, Exception e, String... formatStrings)
    {
        System.err.println(String.format(Style.apply(message, "red", "bold"), formatStrings));
        e.printStackTrace();
    }

    private void printErrorMessage(String message, String... formatStrings)
    {
        System.err.println(String.format(Style.apply(message, "red"), formatStrings));
    }

    private void printMessage(String message, String... formatStrings)
    {
        System.out.println(String.format(Style.apply(message, "default_text"), formatStrings));
    }

    @Override
    public void kill()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.input));
    }

    @Override
    public Object process(Data data)
    {
        System.out.println(data.get().toString());
        return null;
    }
}