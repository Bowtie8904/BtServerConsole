package bt;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import bt.console.input.ArgumentParser;
import bt.console.input.FlagArgument;
import bt.console.input.ValueArgument;
import bt.console.output.styled.Style;
import bt.remote.socket.MulticastClient;
import bt.scheduler.Threads;

/**
 * @author &#8904
 *
 */
public class Main
{
    public static void main(String[] args)
    {
        try
        {
            var parser = new ArgumentParser("-");

            var discoverCmd = new FlagArgument("discover", "d").usage("-discover")
                                                               .description("Attempts to discover possible servers to connect to.");

            var hostCmd = new ValueArgument("host", "h").usage("-host <hostname>")
                                                        .description("[Optional] Sets the hostname to connect to. Ommit to connect to localhost.");

            var portCmd = new ValueArgument("port", "p").usage("-port <portnumber>")
                                                        .description("Sets the port to connect to.");

            var rawCmd = new FlagArgument("raw", "r").usage("-raw")
                                                     .description("[Optional] Sets the type of client to raw to send and receive raw byte data.");

            parser.register(discoverCmd);
            parser.register(hostCmd);
            parser.register(portCmd);
            parser.register(rawCmd);
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
                    String host = hostCmd.getValue() != null ? hostCmd.getValue() : "localhost";
                    new BtServerConsole(host, port, !rawCmd.isExecuted());
                    System.exit(0);
                }
                else
                {
                    System.out.println(Style.apply("Usage: btc [-host <hostname>] -port <portnumber> [-raw]", "red"));
                }
            }
        }
        catch (Exception e)
        {
            System.err.println(Style.apply(e));
            System.exit(-1);
        }
    }
}