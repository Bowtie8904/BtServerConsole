package bt;

import bt.async.AsyncException;
import bt.async.Data;
import bt.console.output.styled.Style;
import bt.db.statement.result.SqlResultSet;
import bt.log.Log;
import bt.remote.socket.ObjectClient;
import bt.remote.socket.Server;
import bt.remote.socket.ServerClient;
import bt.remote.socket.data.DataProcessor;
import bt.remote.socket.evnt.server.ServerClientEvent;
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

        this.server.configureDefaultEventListeners();

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
                            Log.error("Received Exception from client", (Throwable)response);
                        }
                        else if (response instanceof SqlResultSet)
                        {
                            SqlResultSet set = (SqlResultSet)response;

                            Log.info(set.toString(new String[] { "green", "bold" },
                                                  new String[] { "white" }));
                        }
                        else
                        {
                            Log.info(response.toString());
                        }
                    }
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

    @Override
    public Object process(Data data)
    {
        Log.info(data.get().toString());
        return null;
    }
}