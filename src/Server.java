import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

  public static final String QUIT_CMD = "BYE";

  private static long numOfOkResps = 0;
  private static long avgRespTimeInMillis = 0;
  private static long maxRespTimeInMillis = 0;

  protected static final ExecutorService STAT_REQS_EXECUTOR = Executors.newCachedThreadPool();
  protected static final ExecutorService COMP_REQS_EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


  public static void main(String... args) {

    logMessage("Starting server", System.out);

    if (args.length == 1) {

      try {
        Integer.parseInt(args[0]);
      } catch (NumberFormatException e) {
        System.err.println("Invalid port number");
        System.out.println("Usage: java Server <port>");
        System.exit(1);
      }

      while (true) {
        try (ServerSocket serverSocket = new ServerSocket(Integer.parseInt(args[0]))) {
          new ClientHandler(serverSocket.accept()).start();
        } catch (IOException e) {
          logMessage(e.getMessage(), System.err);
        }
      }

    }

    logMessage("Server shutting down", System.out);

  }

  public static void logMessage(String msg, PrintStream out) {
    out.println(msg);
  }

  protected static void updateRespsStats(final long respTime) {

    numOfOkResps++;

    if (respTime > maxRespTimeInMillis) {
      maxRespTimeInMillis = respTime;
    }

    avgRespTimeInMillis = ((avgRespTimeInMillis * (numOfOkResps - 1)) + respTime) / numOfOkResps;

  }

  public static long numOfOkResps() {
    return numOfOkResps;
  }

  public static long avgRespTimeInMillis() {
    return avgRespTimeInMillis;
  }

  public static long maxRespTimeInMillis() {
    return maxRespTimeInMillis;
  }

}



