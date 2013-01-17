package net.lshift.diffa.auth;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static java.util.Arrays.asList;

public class AuthProxy {

  static Logger log = LoggerFactory.getLogger(AuthProxy.class);

  private final int localPort;
  private final String remoteHost;
  private final int remotePort;

  public AuthProxy(int localPort, String remoteHost, int remotePort) {
    this.localPort = localPort;
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
  }

  public void run() {

    log.info("Proxying *:" + localPort + " to " + remoteHost + ':' + remotePort);

    // Configure the bootstrap.
    Executor executor = Executors.newCachedThreadPool();
    ServerBootstrap sb = new ServerBootstrap(
        new NioServerSocketChannelFactory(executor, executor));

    // Set up the event pipeline factory.
    ClientSocketChannelFactory cf = new NioClientSocketChannelFactory(executor, executor);

    sb.setPipelineFactory(new AuthProxyPipelineFactory(cf, remoteHost, remotePort));

    // Start up the server.
    sb.bind(new InetSocketAddress(localPort));
  }

  public static void main(String[] args) throws Exception {
    AuthProxy proxy = configureProxySettings(args);
    proxy.run();
  }

  private static AuthProxy configureProxySettings(String[] args) throws IOException {

    OptionParser parser = new OptionParser();
    parser.acceptsAll(asList("help", "h", "?"), "show help").forHelp();
    OptionSpec<Integer> localPortSpec = parser.accepts("l", "Local port to listen on").withRequiredArg().ofType(Integer.class).required();
    OptionSpec<String> remoteHostSpec = parser.accepts("h", "Remote host to forward to").withRequiredArg().required();
    OptionSpec<Integer> remotePortSpec = parser.accepts("p", "Remote host to forward to").withRequiredArg().ofType(Integer.class).required();

    OptionSet options = null;

    try {
      options = parser.parse(args);
    }
    catch (OptionException e) {
      System.err.println(e.getMessage());
      parser.printHelpOn(System.err);
      System.exit(1);
    }

    int localPort = options.valueOf(localPortSpec);
    int remotePort = options.valueOf(remotePortSpec);
    String remoteHost = options.valueOf(remoteHostSpec);

    return new AuthProxy(localPort, remoteHost, remotePort);
  }
}
