#!/bin/bash
#
# Contains help for the 'scavenger'-script. Should not be used directly.

if [ -z 'SOURCED_HELP' ]
then
  export SOURCED_HELP='true'
else
  return
fi

function printHelp() {
  echo "Scavenger 2.x"
  echo "This script starts Scavenger 2.x nodes."
  echo ""
  echo "Usage:  "
  echo "    $ scavenger <cmd> <options>"
  echo ""
  echo "Commands:"
  echo "    startSeed"
  echo "    startWorker"
  echo "    startMaster"
  echo ""
  echo "Options:"
  echo "  --jvm-options '-DmyOpt1=val1 -DmyOpt2=val2 ...'"
  echo "  --scavenger-conf <pathToScavengerConfFile>"
  echo "  --host <hostNameOrIp>"
  echo "  --port <portNumber>"
  echo "  --jars <applicationSpecificJars>"
  echo "  --main <org.full.name.of.ClientApp> "
  echo ""
  echo "Examples:"
  echo "1) Assuming 'scavenger.conf' is in the current directory,"
  echo "   starting a seed node should not be as simple as this: "
  echo "    $ scavenger startSeed"
  echo "2) If 'scavenger.conf' is in '/my/path/scavenger.conf',"
  echo "   we have to specify it explicitly:"
  echo "    $ scavenger startSeed --scavenger-conf /my/path/scavenger.conf"
  echo "3) Starting worker nodes is similar, but one has to keep in mind "
  echo "   that every worker needs the jars with all classes that are"
  echo "   used for the actual computations:"
  echo "    $ scavenger startWorker --jars /myApp/target/myStuff.jar"
  echo "   Notice that the hostname should be determined automatically, "
  echo "   so that you should leave the option --host out most of the times."
  echo "4) A master node makes sense only as a part of the client application"
  echo "   Therefore, to start a master node, we have to specify the full"
  echo "   name of the class that contains the `main`-method and starts "
  echo "   the master node:"
  echo "    $ scavenger startMaster \\"
  echo "      --jars /myApp/myStuff.jar \\"
  echo "      --main org.myOrg.myApp.MyMain"
}
