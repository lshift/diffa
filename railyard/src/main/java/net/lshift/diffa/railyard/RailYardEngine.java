package net.lshift.diffa.railyard;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import joptsimple.OptionSet;
import net.lshift.diffa.conductor.SimpleDaemon;
import net.lshift.diffa.railyard.plumbing.FailureHandler;
import net.lshift.diffa.railyard.plumbing.JsonProcessingExceptionMapper;
import net.lshift.diffa.railyard.plumbing.MethodNotAllowedExceptionMapper;
import net.lshift.diffa.railyard.plumbing.VersionStoreExceptionMapper;
import net.lshift.diffa.railyard.wiring.RailYardModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RailYardEngine extends SimpleDaemon {

  static Logger log = LoggerFactory.getLogger(RailYardEngine.class);

  public static final int DEFAULT_PORT = 7655;

  public RailYardEngine(String[] args) {
    super(args);
  }

  public static void main(String[] args) {
    new RailYardEngine(args);
  }

  @Override
  protected List<Object> getResources() {
    List<Object> resources = new ArrayList<Object>();

    Injector injector = Guice.createInjector(new RailYardModule());
    resources.add(injector.getInstance(ChangesResource.class));
    resources.add(injector.getInstance(InterviewResource.class));

    return resources;
  }

  @Override
  protected List<String> getProviderClasses() {

    return ImmutableList.of(
        JsonProcessingExceptionMapper.class.getName(),
        VersionStoreExceptionMapper.class.getName(),
        MethodNotAllowedExceptionMapper.class.getName(),
        FailureHandler.class.getName()
    );
  }

  @Override
  protected String getName(OptionSet options) {
    return "Railyard engine";
  }

  @Override
  protected int getPort(OptionSet options) {
    return DEFAULT_PORT;
  }

  /*
  @Override
  public void run() {

    // Start off by wiring all of the components together

    Injector injector = Guice.createInjector(new RailYardModule());

    List<Object> resources = new ArrayList<Object>();
    resources.add(injector.getInstance(ChangesResource.class));

    // Now that we have all of the bits and pieces wired, let's
    // put them together into a REST deployment and tell
    // RESTeasy what to do with exceptions

    ResteasyDeployment deployment = new ResteasyDeployment();
    deployment.setResources(resources);

    deployment.setProviderClasses(
        ImmutableList.of(
            JsonProcessingExceptionMapper.class.getName(),
            VersionStoreExceptionMapper.class.getName(),
            ApplicationExceptionHandler.class.getName()
        )
    );

    // Now boot Netty with the configured RESTeasy deployment

    NettyJaxrsServer server = new NettyJaxrsServer();
    server.setDeployment(deployment);
    server.setPort(DEFAULT_PORT);
    server.start();

    log.info("Started rail yard engine on port " + DEFAULT_PORT);
  }


  public static void main(String[] args) throws Exception {

    OptionSet options = getOptions(args);

    boolean daemon = options.has("f") ? false : true;

    RailYardEngine railYardEngine = new RailYardEngine();
    Thread railYardThread = new Thread(railYardEngine);

    if (!daemon) {
      log.info("Rail yard engine running in foreground");
    }

    railYardThread.setDaemon(daemon);
    railYardThread.start();

  }

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
  */

}
