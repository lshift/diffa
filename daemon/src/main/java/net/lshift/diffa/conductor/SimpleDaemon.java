package net.lshift.diffa.conductor;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jboss.resteasy.plugins.server.netty.NettyJaxrsServer;
import org.jboss.resteasy.spi.ResteasyDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Arrays.*;

public abstract class SimpleDaemon implements Runnable {

  private static Logger log = LoggerFactory.getLogger(SimpleDaemon.class);
  protected OptionSet options;
  
  public SimpleDaemon(String[] args)  {

    try {
      options = getOptions(args);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    boolean daemon = options.has("f") ? false : true;

    Thread serverThread = new Thread(this);

    serverThread.setDaemon(daemon);
    serverThread.start();

    if (!daemon) {
      log.info("Daemon running in foreground");
    }

  }


  public void run() {

    try {

      ResteasyDeployment deployment = new ResteasyDeployment();
      deployment.setResources(getResources());
      deployment.setProviderClasses(getProviderClasses());
      deployment.setResources(getResources());

      final int port = getPort(options);

      NettyJaxrsServer server = new NettyJaxrsServer();
      server.setDeployment(deployment);
      server.setPort(port);
      server.start();

      final String name = getName(options);

      log.info(String.format("Started %s on port %s", name, port));

    }
    catch (Throwable t) {
      log.error("Could not boot daemon", t);
    }

  }

  protected abstract List<Object> getResources();
  protected abstract List<String> getProviderClasses();
  protected abstract String getName(OptionSet options);
  protected abstract int getPort(OptionSet options);

  private static OptionSet getOptions(String[] args) throws IOException {

    OptionParser parser = new OptionParser();
    parser.acceptsAll(asList("help", "h", "?"), "show help").forHelp();
    parser.accepts("f", "Run rail yard engine in the foreground");

    OptionSet options = null;

    try {
      options = parser.parse(args);
    }
    catch (OptionException e) {
      System.err.println(e.getMessage());
      parser.printHelpOn(System.err);
      System.exit(1);
    }
    return options;
  }

}
