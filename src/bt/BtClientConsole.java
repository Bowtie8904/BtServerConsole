package bt;

import bt.async.AsyncException;
import bt.console.output.styled.Style;
import bt.db.listener.evnt.DeleteEvent;
import bt.db.listener.evnt.InsertEvent;
import bt.db.listener.evnt.UpdateEvent;
import bt.db.statement.result.SqlResultSet;
import bt.remote.socket.Client;
import bt.remote.socket.ObjectClient;
import bt.remote.socket.RawClient;
import bt.remote.socket.evnt.client.*;
import bt.runtime.InstanceKiller;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

public class BtClientConsole implements Killable
{
    private Client client;
    private Scanner input;
    private int port;
    private String host;
    private boolean objectClient;

    public BtClientConsole(String host, int port, boolean objectClient)
    {
        this.host = host;
        this.port = port;
        this.objectClient = objectClient;

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

        if (this.objectClient)
        {
            this.client = new ObjectClient(this.host, this.port);
            ((ObjectClient)this.client).setDataProcessor(data -> {
                Threads.get().executeCached(() -> processObjectResponse(data.get()));
                return null;
            });
        }
        else
        {
            this.client = new RawClient(this.host, this.port);
            ((RawClient)this.client).setByteProcessor(data -> {
                int[] intData = new int[data.length];

                for (int i = 0; i < data.length; i++)
                {
                    intData[i] = Byte.toUnsignedInt(data[i]);
                }

                System.out.println(Arrays.toString(intData));

                return null;
            });
        }

        this.client.setSingleThreadProcessing(true);
        this.client.autoReconnect(3);

        client.getEventDispatcher().subscribeTo(ClientConnectionSuccessfull.class, e -> printMessage("Connected to %s:%s",
                                                                                                     formatHostPort(e)));

        client.getEventDispatcher().subscribeTo(ClientConnectionFailed.class, e -> printMessageAndStackTrace("Failed to connect to %s:%s",
                                                                                                             e,
                                                                                                             formatHostPort(e)));

        client.getEventDispatcher().subscribeTo(ClientConnectionLost.class, e -> printMessageAndStackTrace("Connection to %s:%s lost",
                                                                                                           e,
                                                                                                           formatHostPort(e)));

        client.getEventDispatcher().subscribeTo(ClientReconnectStarted.class, e -> printMessage("Attempting to reconnect to %s:%s",
                                                                                                formatHostPort(e)));

        client.getEventDispatcher().subscribeTo(ClientReconnectSuccessfull.class, e -> printMessage("Successfully reconnected to %s:%s",
                                                                                                    formatHostPort(e)));

        client.getEventDispatcher().subscribeTo(ClientReconnectFailed.class, e -> printMessageAndStackTrace("Failed to reconnect to %s:%s",
                                                                                                            e,
                                                                                                            formatHostPort(e)));

        client.getEventDispatcher().subscribeTo(ClientReconnectAttemptFailed.class, e -> printErrorMessage("Attempt %s/%s failed",
                                                                                                           Style.apply(e.getAttempt() + "", "-red", "yellow"),
                                                                                                           Style.apply((e.getMaxAttempts() == -1 ? "-" : e.getMaxAttempts() + ""), "-red", "yellow")));

        client.getEventDispatcher().subscribeTo(UnspecifiedClientException.class, e -> printMessageAndStackTrace("Error", e));

        this.client.start();
        this.input = new Scanner(System.in);
        InstanceKiller.killOnShutdown(this);

        if (!this.client.isConnected())
        {
            System.exit(-1);
        }
    }

    private String[] formatHostPort(ClientEvent e)
    {
        return new String[]
                {
                        Style.apply(e.getClient().getHost(), "-red", "yellow"),
                        Style.apply(e.getClient().getPort() + "", "-red", "yellow")
                };
    }

    private void printMessageAndStackTrace(String message, ClientExceptionEvent e, String... formatStrings)
    {
        System.err.println(String.format(Style.apply(message, "red", "bold"), formatStrings));
        e.getException().printStackTrace();
    }

    private void printMessage(String message, String... formatStrings)
    {
        System.out.println(String.format(Style.apply(message, "default_text"), formatStrings));
    }

    private void printErrorMessage(String message, String... formatStrings)
    {
        System.err.println(String.format(Style.apply(message, "red"), formatStrings));
    }

    protected void processObjectResponse(Object response)
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
        else if (response instanceof InsertEvent)
        {
            InsertEvent ev = (InsertEvent)response;

            try
            {
                System.out.println("Inserted row in table " + ev.getTable() + " with id " + ev.getID() + ".");
                Object resultSet = ((ObjectClient)this.client).send("select * from " + ev.getTable() + " where " + ev.getIDFieldName() + " = " + ev.getID()).get();
                processObjectResponse(resultSet);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else if (response instanceof DeleteEvent)
        {
            DeleteEvent ev = (DeleteEvent)response;
            System.out.println("Deleted row in table " + ev.getTable() + " with id " + ev.getID() + ".");
        }
        else if (response instanceof UpdateEvent)
        {
            UpdateEvent ev = (UpdateEvent)response;
            try
            {
                System.out.println("Updated row in table " + ev.getTable() + " with id " + ev.getID() + ".");
                Object resultSet = ((ObjectClient)this.client).send("select * from " + ev.getTable() + " where " + ev.getIDFieldName() + " = " + ev.getID()).get();
                processObjectResponse(resultSet);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println(response);
        }
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
                if (this.objectClient)
                {
                    Object response = ((ObjectClient)this.client).send(cmd).get();

                    processObjectResponse(response);
                }
                else
                {
                    ((RawClient)this.client).send(cmd.getBytes(StandardCharsets.UTF_8));
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

    @Override
    public void kill()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.input));
    }
}