package bt;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import bt.console.input.CommandParser;
import bt.console.input.FlagCommand;
import bt.console.input.ValueCommand;
import bt.log.Logger;
import bt.remote.socket.MulticastClient;
import bt.scheduler.Threads;

/**
 * @author &#8904
 *
 */
public class Main
{
    public static void main(String[] args) throws IOException
    {
        Logger.global().setPrintCaller(false);
        Logger.global().setLogToFile(false);
        Logger.global().hookSystemOut();
        Logger.global().hookSystemErr();

        var parser = new CommandParser("-");

        var discoverCmd = new FlagCommand("discover", "d").usage("-discover")
                                                          .description("Attempts to discover possible servers to connect to.");

        var hostCmd = new ValueCommand("host").usage("-host <hostname>")
                                              .description("[Optional] Sets the hostname to connect to. Ommit to connect to localhost.");

        var portCmd = new ValueCommand("port").usage("-port <portnumber>")
                                              .description("Sets the port to connect to.");

        parser.register(discoverCmd);
        parser.register(hostCmd);
        parser.register(portCmd);
        parser.registerDefaultHelpCommand("help", "h");
        parser.parse(args);

        if (discoverCmd.isExecuted())
        {
            var client = new MulticastClient(MulticastClient.DEFAULT_PORT, MulticastClient.DEFAULT_GROUP_ADDRESS);

            client.onMulticastReceive(packet ->
            {
                String message = new String(packet.getData(), 0, packet.getLength());

                if (!message.trim().equalsIgnoreCase("discover"))
                {
                    Logger.global().print(message);
                }
            });

            client.start();

            Threads.get().schedule(() ->
            {
                try
                {
                    client.send("Discover");
                }
                catch (IOException e)
                {
                    Logger.global().print(e);
                }

            }, 1, TimeUnit.SECONDS);

            Scanner sc = new Scanner(System.in);

            sc.nextLine();
            sc.close();

            System.exit(0);
        }
        else
        {
            int port = 0;

            if (parser.wasExecuted("port"))
            {
                port = Integer.parseInt(portCmd.getValue());
                String host = hostCmd.getValue() != null ? hostCmd.getValue() : "localhost";
                new BtServerConsole(host, port);
                System.exit(0);
            }
            else
            {
                Logger.global().print("Usage: btc [-host <hostname>] -port <portnumber>");
            }
        }
    }
}