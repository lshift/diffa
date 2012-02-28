# Overview

Diffa provides real time health checks between arbitrary system components.

For more information, please see the website: http://diffa.lshift.net

# License

Diffa uses the Apache license, Version 2.0.

# Proprietary Third Party Libraries

Some configurations of the software rely on third party libraries which are not
freely available.  The following libraries are available under license from
their respective vendors.  Please check with the vendor the conditions under
which you may use their software.

Library: Oracle database client library
Vendor: Oracle (www.oracle.com)
Referenced in: kernel/pom.xml (profile id = oracle)
      <dependency>
        <groupId>com.oracle</groupId>
        <artifactId>ojdbc6</artifactId>
        <version>11.2.0.1.0</version>
        <scope>test</scope>
      </dependency>
Required for:
- testing against an Oracle database
- installing/upgrading an Oracle database schema
In order to run maven tasks with the 'oracle' profile (mvn -Poracle <task>),
your maven repository will need to contain the Oracle JDBC driver specified in
the dependency.

