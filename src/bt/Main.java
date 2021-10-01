package bt;

import bt.console.input.ArgumentParser;
import bt.console.input.FlagArgument;
import bt.console.input.ValueArgument;
import bt.console.output.styled.Style;
import bt.log.Logger;
import bt.remote.socket.MulticastClient;
import bt.remote.socket.evnt.mcast.MulticastClientKilled;
import bt.remote.socket.evnt.mcast.MulticastClientStarted;
import bt.scheduler.Threads;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * @author &#8904
 */
public class Main
{
    public static void main(String[] args) throws IOException
    {
        Logger.global().hookSystemErr();
        Logger.global().hookSystemOut();
        Logger.global().setLogToFile(false);
        Logger.global().setPrintCaller(false);
        Logger.global().setPrintTimestamp(false);

        var parser = new ArgumentParser("-");

        var discoverCmd = new FlagArgument("discover", "d").usage("-discover")
                                                           .description("Attempts to discover possible servers to connect to.");

        var hostCmd = new ValueArgument("host", "h").usage("-host <hostname>")
                                                    .description("[Optional] Sets the hostname to connect to. Ommit to connect to localhost.");

        var portCmd = new ValueArgument("port", "p").usage("-port <portnumber>")
                                                    .description("Sets the port to connect to or the port to listen on if the server flag is used.");

        var rawCmd = new FlagArgument("raw", "r").usage("-raw")
                                                 .description("[Optional] Sets the type of client to raw to send and receive raw byte data.");

        var serverCmd = new FlagArgument("server", "s").usage("-server")
                                                       .description("[Optional] Indicates that a server should be created instead of a client.");

        parser.register(discoverCmd);
        parser.register(hostCmd);
        parser.register(portCmd);
        parser.register(rawCmd);
        parser.register(serverCmd);
        parser.registerDefaultHelpArgument();
        parser.registerDefaultStyleArgument();
        parser.parse(args);

        if (discoverCmd.isExecuted())
        {
            var client = new MulticastClient(MulticastClient.DEFAULT_PORT, MulticastClient.DEFAULT_GROUP_ADDRESS);
            client.getEventDispatcher().subscribeTo(MulticastClientStarted.class, e -> printMessage("Started multicast client %s:%s",
                                                                                                    formatHostPort()));

            client.getEventDispatcher().subscribeTo(MulticastClientKilled.class, e -> printMessage("Killed multicast client %s:%s",
                                                                                                   formatHostPort()));

            client.onMulticastReceive(packet ->
                                      {
                                          String message = new String(packet.getData(), 0, packet.getLength());

                                          if (!message.trim().equalsIgnoreCase("discover"))
                                          {
                                              System.out.println(Style.apply(message, "yellow"));
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
                                           System.err.println(Style.apply(e));
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

                if (serverCmd.getFlag())
                {
                    new BtServerConsole(port);
                }
                else
                {
                    String host = hostCmd.getValue() != null ? hostCmd.getValue() : "localhost";
                    new BtClientConsole(host, port, !rawCmd.isExecuted());
                }

                System.exit(0);
            }
            else
            {
                System.err.println("Usage: btc [-host <hostname>] -port <portnumber> [-raw] [-server]");
            }
        }
    }

    private static void printMessage(String message, String... formatStrings)
    {
        System.out.println(String.format(Style.apply(message, "default_text"), formatStrings));
    }

    private static String[] formatHostPort()
    {
        return new String[]
                {
                        Style.apply(MulticastClient.DEFAULT_GROUP_ADDRESS, "-red", "yellow"),
                        Style.apply(MulticastClient.DEFAULT_PORT + "", "-red", "yellow")
                };
    }
}