package bt;

import bt.console.input.ArgumentParser;
import bt.console.input.FlagArgument;
import bt.console.input.ValueArgument;
import bt.console.output.styled.Style;
import bt.console.output.styled.log.DestyledLogFormatter;
import bt.console.output.styled.log.StyledLogFormatter;
import bt.log.ConsoleLoggerHandler;
import bt.log.FileLoggerHandler;
import bt.log.Log;
import bt.log.LoggerConfiguration;
import bt.remote.socket.MulticastClient;
import bt.scheduler.Threads;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * @author &#8904
 */
public class Main
{
    public static void main(String[] args) throws IOException
    {
        Log.createDefaultLogFolder();

        var consoleLogConfig = new LoggerConfiguration().level(Level.INFO)
                                                        .printCaller(false)
                                                        .printLogLevel(false)
                                                        .printThreadName(false)
                                                        .printTimestamp(false);
        var consoleHandler = new ConsoleLoggerHandler(consoleLogConfig);
        consoleHandler.setFormatter(new StyledLogFormatter(consoleLogConfig));

        var fileLogConfig = new LoggerConfiguration().level(Level.FINE);
        var fileHandler = new FileLoggerHandler(fileLogConfig);
        fileHandler.setFormatter(new DestyledLogFormatter(fileLogConfig));

        Log.configureDefaultJDKLogger(consoleHandler, fileHandler);

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
            client.configureDefaultEventListeners();

            client.onMulticastReceive(packet ->
                                      {
                                          String message = new String(packet.getData(), 0, packet.getLength());

                                          if (!message.trim().equalsIgnoreCase("discover"))
                                          {
                                              Log.info(Style.apply(message, "yellow"));
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
                                           Log.error("Failed to send discover request", e);
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
                Log.error("Usage: btc [-host <hostname>] -port <portnumber> [-raw] [-server]");
            }
        }
    }
}