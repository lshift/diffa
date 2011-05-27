Get the latest Standalone Diffa agent package from the Diffa [download page][download] on GitHub.

    $ wget https://github.com/downloads/lshift/diffa/diffa-0.9.3-SNAPSHOT.zip

Extract the contents of the archive, and boot the agent:

    $ unzip diffa-0.9.3-SNAPSHOT.zip -d diffa-agent
    $ cd diffa-agent/bin
    $ sh agent.sh

Once you see the log message `Diffa Agent successfully initialized`, open a browser window and go to [http://localhost:7654/](http://localhost:7654/). You should see the Diffa UI.