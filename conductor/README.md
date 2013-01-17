# Conductor Run Book

This is a complicated module to hack on, since it has a lot of integration touch points which include:

* A running instance of HSQL that is accessible by both the integration test code and the Conductor itself;
* Cassandra;

If you want to run HSQL in the foreground using Maven:

  $ mvn exec:java -Dhsqldb.daemon=false

  .....

  [Server@31fa4f2a]: [Thread[org.hsqldb.Server.main(),5,org.hsqldb.Server]]: checkRunning(false) entered
  [Server@31fa4f2a]: [Thread[org.hsqldb.Server.main(),5,org.hsqldb.Server]]: checkRunning(false) exited
  [Server@31fa4f2a]: Startup sequence initiated from main() method
  [Server@31fa4f2a]: Could not load properties from file
  [Server@31fa4f2a]: Using cli/default properties only
  [Server@31fa4f2a]: Initiating startup sequence...
  [Server@31fa4f2a]: Server socket opened successfully in 3 ms.
  [Server@31fa4f2a]: Database [index=0, id=0, db=file:target/things, alias=things] opened sucessfully in 478 ms.
  [Server@31fa4f2a]: Startup sequence completed in 483 ms.
  [Server@31fa4f2a]: 2012-12-17 13:57:17.825 HSQLDB server 2.2.8 is online on port 11091
  [Server@31fa4f2a]: To close normally, connect and execute SHUTDOWN SQL
  [Server@31fa4f2a]: From command line, use [Ctrl]+[C] to abort abruptly


$ mvn cassandra:start cassandra:load
$ mvn cassandra:run