package bt;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import bt.console.input.ArgumentParser;
import bt.console.input.FlagArgument;
import bt.console.input.ValueArgument;
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
        var parser = new ArgumentParser("-");

        var discoverCmd = new FlagArgument("discover", "d").usage("-discover")
                                                           .description("Attempts to discover possible servers to connect to.");

        var hostCmd = new ValueArgument("host").usage("-host <hostname>")
                                               .description("[Optional] Sets the hostname to connect to. Ommit to connect to localhost.");

        var portCmd = new ValueArgument("port").usage("-port <portnumber>")
                                              .description("Sets the port to connect to.");

        parser.register(discoverCmd);
        parser.register(hostCmd);
        parser.register(portCmd);
        parser.registerDefaultHelpArgument("help", "h");
        parser.parse(args);

        if (discoverCmd.isExecuted())
        {
            var client = new MulticastClient(MulticastClient.DEFAULT_PORT, MulticastClient.DEFAULT_GROUP_ADDRESS);

            client.onMulticastReceive(packet ->
            {
                String message = new String(packet.getData(), 0, packet.getLength());

                if (!message.trim().equalsIgnoreCase("discover"))
                {
                    System.out.println(message);
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
                    e.printStackTrace();
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
                System.out.println("Usage: btc [-host <hostname>] -port <portnumber>");
            }
        }
    }
}