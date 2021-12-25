package bt;

import bt.async.AsyncException;
import bt.db.listener.evnt.DeleteEvent;
import bt.db.listener.evnt.InsertEvent;
import bt.db.listener.evnt.UpdateEvent;
import bt.db.statement.result.SqlResultSet;
import bt.log.Log;
import bt.remote.socket.Client;
import bt.remote.socket.ObjectClient;
import bt.remote.socket.RawClient;
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
            Log.error("Failed to initialize", e);
            System.exit(-1);
        }

        handleInput();
    }

    public void init() throws IOException
    {
        Log.info("\r\n====================================================="
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

                Log.info(Arrays.toString(intData));

                return null;
            });
        }

        this.client.setSingleThreadProcessing(true);
        this.client.autoReconnect(3);

        this.client.configureDefaultEventListeners();

        this.client.start();
        this.input = new Scanner(System.in);
        InstanceKiller.killOnShutdown(this);

        if (!this.client.isConnected())
        {
            System.exit(-1);
        }
    }

    protected void processObjectResponse(Object response)
    {
        if (response != null)
        {
            if (response instanceof Throwable)
            {
                Log.error("Received Exception from server", (Throwable)response);
            }
            else if (response instanceof SqlResultSet)
            {
                SqlResultSet set = (SqlResultSet)response;
                Log.info(set.toString(new String[] { "green", "bold" },
                                      new String[] { "white" }));
            }
            else if (response instanceof InsertEvent)
            {
                InsertEvent ev = (InsertEvent)response;

                try
                {
                    Log.info("Inserted row in table " + ev.getTable() + " with id " + ev.getID() + ".");
                    Object resultSet = ((ObjectClient)this.client).send("select * from " + ev.getTable() + " where " + ev.getIDFieldName() + " = " + ev.getID()).get();
                    processObjectResponse(resultSet);
                }
                catch (IOException e)
                {
                    Log.error("Failed to select inserted row", e);
                }
            }
            else if (response instanceof DeleteEvent)
            {
                DeleteEvent ev = (DeleteEvent)response;
                Log.info("Deleted row in table " + ev.getTable() + " with id " + ev.getID() + ".");
            }
            else if (response instanceof UpdateEvent)
            {
                UpdateEvent ev = (UpdateEvent)response;
                try
                {
                    Log.info("Updated row in table " + ev.getTable() + " with id " + ev.getID() + ".");
                    Object resultSet = ((ObjectClient)this.client).send("select * from " + ev.getTable() + " where " + ev.getIDFieldName() + " = " + ev.getID()).get();
                    processObjectResponse(resultSet);
                }
                catch (IOException e)
                {
                    Log.error("Failed to select updated row", e);
                }
            }
            else
            {
                Log.info(response.toString());
            }
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
                Log.error("Request timed out.");
            }
            catch (IOException e)
            {
                Log.error("Failed to send request", e);
            }
        }
    }

    @Override
    public void kill()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.input));
    }
}