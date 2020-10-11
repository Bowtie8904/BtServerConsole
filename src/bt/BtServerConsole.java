package bt;

import java.io.IOException;
import java.util.Scanner;

import bt.async.AsyncException;
import bt.log.Logger;
import bt.remote.socket.Client;
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

    public BtServerConsole(String host, int port)
    {
        this.host = host;
        this.port = port;

        try
        {
            init();
        }
        catch (IOException e)
        {
            Logger.global().print(e);
            System.exit(-1);
        }

        handleInput();
    }

    public void init() throws IOException
    {
        Logger.global().print("\r\n====================================================="
                              + "\r\n______ _   _____                       _      "
                              + "\r\n| ___ \\ | /  __ \\                     | |     "
                              + "\r\n| |_/ / |_| /  \\/ ___  _ __  ___  ___ | | ___ "
                              + "\r\n| ___ \\ __| |    / _ \\| '_ \\/ __|/ _ \\| |/ _ \\"
                              + "\r\n| |_/ / |_| \\__/\\ (_) | | | \\__ \\ (_) | |  __/"
                              + "\r\n\\____/ \\__|\\____/\\___/|_| |_|___/\\___/|_|\\___|"
                              + "\r\n=====================================================");

        this.client = new Client(this.host, this.port);
        this.client.start();
        this.input = new Scanner(System.in);
        InstanceKiller.killOnShutdown(this);
        Logger.global().print("Connected to " + this.client.getHost() + ":" + this.client.getPort());
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
                Object response = this.client.send(cmd).get();

                if (response instanceof Throwable)
                {
                    Logger.global().print((Throwable)response);
                    continue;
                }

                Logger.global().print(response);
            }
            catch (AsyncException e)
            {
                Logger.global().print("Request timed out.");
            }
            catch (IOException e)
            {
                Logger.global().print(e);
            }
        }
    }

    @Override
    public void kill()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.input));
    }
}