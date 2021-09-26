package bt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

import bt.async.AsyncException;
import bt.remote.socket.Client;
import bt.remote.socket.ObjectClient;
import bt.remote.socket.RawClient;
import bt.remote.socket.evnt.*;
import bt.runtime.InstanceKiller;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

public class BtServerConsole implements Killable
{
    private Client client;
    private Scanner input;
    private int port;
    private String host;
    private boolean objectClient;

    public BtServerConsole(String host, int port, boolean objectClient)
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
                System.out.println(data.get());
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

        client.getEventDispatcher().subscribeTo(ConnectionSuccessfull.class, e -> System.out.println("Connected to " + e.getClient().getHost() + ":" + e.getClient().getPort()));
        client.getEventDispatcher().subscribeTo(ConnectionFailed.class, e -> printMessageAndStackTrace("Failed to connect to " + e.getClient().getHost() + ":" + e.getClient().getPort(), e));
        client.getEventDispatcher().subscribeTo(ConnectionLost.class, e -> printMessageAndStackTrace("Connection to " + e.getClient().getHost() + ":" + e.getClient().getPort() + " lost", e));
        client.getEventDispatcher().subscribeTo(ReconnectStarted.class, e -> System.out.println("Attempting to reconnect to " + e.getClient().getHost() + ":" + e.getClient().getPort()));
        client.getEventDispatcher().subscribeTo(ReconnectSuccessfull.class, e -> System.out.println("Successfully reconnected to " + e.getClient().getHost() + ":" + e.getClient().getPort()));
        client.getEventDispatcher().subscribeTo(ReconnectFailed.class, e -> printMessageAndStackTrace("Failed to reconnect to " + e.getClient().getHost() + ":" + e.getClient().getPort(), e));
        client.getEventDispatcher().subscribeTo(ReconnectAttemptFailed.class, e -> System.err.println("Attempt " + e.getAttempt() + " / " + (e.getMaxAttempts() == -1 ? "-" : e.getMaxAttempts()) + " failed"));
        client.getEventDispatcher().subscribeTo(UnspecifiedException.class, e -> printMessageAndStackTrace("Error", e));

        this.client.start();
        this.input = new Scanner(System.in);
        InstanceKiller.killOnShutdown(this);

        if (!this.client.isConnected())
        {
            System.exit(-1);
        }
    }

    private void printMessageAndStackTrace(String message, ClientExceptionEvent e)
    {
        System.err.println(message);
        e.getException().printStackTrace();
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

                    if (response instanceof Throwable)
                    {
                        ((Throwable)response).printStackTrace();
                        continue;
                    }

                    System.out.println(response);
                }
                else
                {
                    ((RawClient)this.client).send(cmd.getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (AsyncException e)
            {
                System.err.println("Request timed out.");
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