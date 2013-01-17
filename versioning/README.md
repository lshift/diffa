# Dealing With The Maven Cassandra Plugin

Cassandra will be booted as part of the integration test phase.
However, if you prefer, you can get more control over what is going on.

To reset the local Cassandra node:

$ mvn cassandra:delete cassandra:start cassandra:load

To run the local Cassandra node (e.g. when running standalone unit tests):

$ mvn cassandra:run