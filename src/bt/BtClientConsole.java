package bt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;

import bt.async.AsyncException;
import bt.console.output.styled.Style;
import bt.remote.socket.Client;
import bt.remote.socket.ObjectClient;
import bt.remote.socket.RawClient;
import bt.remote.socket.evnt.client.*;
import bt.runtime.InstanceKiller;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

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
            System.err.println(Style.apply(e));
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
        System.err.println(Style.apply(e.getException()));
    }

    private void printMessage(String message, String... formatStrings)
    {
        System.out.println(String.format(Style.apply(message, "default_text"), formatStrings));
    }

    private void printErrorMessage(String message, String... formatStrings)
    {
        System.err.println(String.format(Style.apply(message, "red"), formatStrings));
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
                        System.err.println(Style.apply((Throwable)response));
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
                printErrorMessage("Request timed out.");
            }
            catch (IOException e)
            {
                System.err.println(Style.apply(e));
            }
        }
    }

    @Override
    public void kill()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.input));
    }
}