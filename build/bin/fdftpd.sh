#!/bin/sh

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# -----------------------------------------------------------------------------
# Control Script for the FDFTPD Server
#
# Environment Variable Prerequisites
#
#   Do not set the variables in this script. Instead put them into a script
#   setenv.sh in FDFTPD_BASE/bin to keep your customizations separate.
#
#   FDFTPD_HOME   May point at your Catalina "build" directory.
#
#   FDFTPD_BASE   (Optional) Base directory for resolving dynamic portions
#                   of a Catalina installation.  If not present, resolves to
#                   the same directory that FDFTPD_HOME points to.
#
#   FDFTPD_OUT    (Optional) Full path to a file where stdout and stderr
#                   will be redirected.
#                   Default is $FDFTPD_BASE/logs/fdftpd.out
#
#   FDFTPD_OPTS   (Optional) Java runtime options used when the "start",
#                   "run" or "debug" command is executed.
#                   Include here and not in JAVA_OPTS all options, that should
#                   only be used by Tomcat itself, not by the stop process,
#                   the version command etc.
#                   Examples are heap size, GC logging, JMX ports etc.
#
#   FDFTPD_TMPDIR (Optional) Directory path location of temporary directory
#                   the JVM should use (java.io.tmpdir).  Defaults to
#                   $FDFTPD_BASE/temp.
#
#   JAVA_HOME       Must point at your Java Development Kit installation.
#                   Required to run the with the "debug" argument.
#
#   JRE_HOME        Must point at your Java Runtime installation.
#                   Defaults to JAVA_HOME if empty. If JRE_HOME and JAVA_HOME
#                   are both set, JRE_HOME is used.
#
#   JAVA_OPTS       (Optional) Java runtime options used when any command
#                   is executed.
#                   Include here and not in FDFTPD_OPTS all options, that
#                   should be used by Tomcat and also by the stop process,
#                   the version command etc.
#                   Most options should go into FDFTPD_OPTS.
#
#   JAVA_ENDORSED_DIRS (Optional) Lists of of colon separated directories
#                   containing some jars in order to allow replacement of APIs
#                   created outside of the JCP (i.e. DOM and SAX from W3C).
#                   It can also be used to update the XML parser implementation.
#                   Defaults to $FDFTPD_HOME/endorsed.
#
#   JPDA_TRANSPORT  (Optional) JPDA transport used when the "jpda start"
#                   command is executed. The default is "dt_socket".
#
#   JPDA_ADDRESS    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. The default is 8000.
#
#   JPDA_SUSPEND    (Optional) Java runtime options used when the "jpda start"
#                   command is executed. Specifies whether JVM should suspend
#                   execution immediately after startup. Default is "n".
#
#   JPDA_OPTS       (Optional) Java runtime options used when the "jpda start"
#                   command is executed. If used, JPDA_TRANSPORT, JPDA_ADDRESS,
#                   and JPDA_SUSPEND are ignored. Thus, all required jpda
#                   options MUST be specified. The default is:
#
#                   -agentlib:jdwp=transport=$JPDA_TRANSPORT,
#                       address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND
#
#   FDFTPD_PID    (Optional) Path of the file which should contains the pid
#                   of the fdftpd startup java process, when start (fork) is
#                   used
#
#   LOGGING_CONFIG  (Optional) Override Tomcat's logging config file
#                   Example (all one line)
#                   LOGGING_CONFIG="-Djava.util.logging.config.file=$FDFTPD_BASE/conf/logging.properties"
#
#   LOGGING_MANAGER (Optional) Override Tomcat's logging manager
#                   Example (all one line)
#                   LOGGING_MANAGER="-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"
#
# $Id: fdftpd.sh 1202062 2011-11-15 06:50:02Z mturk $
# -----------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
darwin=false
os400=false
case "`uname`" in
CYGWIN*) cygwin=true;;
Darwin*) darwin=true;;
OS400*) os400=true;;
esac

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

# Only set FDFTPD_HOME if not already set
[ -z "$FDFTPD_HOME" ] && FDFTPD_HOME=`cd "$PRGDIR/.." >/dev/null; pwd`

# Copy FDFTPD_BASE from FDFTPD_HOME if not already set
[ -z "$FDFTPD_BASE" ] && FDFTPD_BASE="$FDFTPD_HOME"

# Ensure that any user defined CLASSPATH variables are not used on startup,
# but allow them to be specified in setenv.sh, in rare case when it is needed.
CLASSPATH=

if [ -r "$FDFTPD_BASE/bin/setenv.sh" ]; then
  . "$FDFTPD_BASE/bin/setenv.sh"
elif [ -r "$FDFTPD_HOME/bin/setenv.sh" ]; then
  . "$FDFTPD_HOME/bin/setenv.sh"
fi

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$JRE_HOME" ] && JRE_HOME=`cygpath --unix "$JRE_HOME"`
  [ -n "$FDFTPD_HOME" ] && FDFTPD_HOME=`cygpath --unix "$FDFTPD_HOME"`
  [ -n "$FDFTPD_BASE" ] && FDFTPD_BASE=`cygpath --unix "$FDFTPD_BASE"`
  [ -n "$CLASSPATH" ] && CLASSPATH=`cygpath --path --unix "$CLASSPATH"`
fi

# For OS400
if $os400; then
  # Set job priority to standard for interactive (interactive - 6) by using
  # the interactive priority - 6, the helper threads that respond to requests
  # will be running at the same priority as interactive jobs.
  COMMAND='chgjob job('$JOBNAME') runpty(6)'
  system $COMMAND

  # Enable multi threading
  export QIBM_MULTI_THREADED=Y
fi

# Get standard Java environment variables
if $os400; then
  # -r will Only work on the os400 if the files are:
  # 1. owned by the user
  # 2. owned by the PRIMARY group of the user
  # this will not work if the user belongs in secondary groups
  . "$FDFTPD_HOME"/bin/setclasspath.sh
else
  if [ -r "$FDFTPD_HOME"/bin/setclasspath.sh ]; then
    . "$FDFTPD_HOME"/bin/setclasspath.sh
  else
    echo "Cannot find $FDFTPD_HOME/bin/setclasspath.sh"
    echo "This file is needed to run this program"
    exit 1
  fi
fi

# Add on extra jar files to CLASSPATH
if [ ! -z "$CLASSPATH" ] ; then
  CLASSPATH="$CLASSPATH":
fi
CLASSPATH="$CLASSPATH""$FDFTPD_HOME"/bin/fdftpd.jar

if [ -z "$FDFTPD_OUT" ] ; then
  FDFTPD_OUT="$FDFTPD_BASE"/logs/fdftpd.out
fi

if [ -z "$FDFTPD_TMPDIR" ] ; then
  # Define the java.io.tmpdir to use for Catalina
  FDFTPD_TMPDIR="$FDFTPD_BASE"/temp
fi

# Bugzilla 37848: When no TTY is available, don't output to console
have_tty=0
if [ "`tty`" != "not a tty" ]; then
    have_tty=1
fi

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --absolute --windows "$JAVA_HOME"`
  JRE_HOME=`cygpath --absolute --windows "$JRE_HOME"`
  FDFTPD_HOME=`cygpath --absolute --windows "$FDFTPD_HOME"`
  FDFTPD_BASE=`cygpath --absolute --windows "$FDFTPD_BASE"`
  FDFTPD_TMPDIR=`cygpath --absolute --windows "$FDFTPD_TMPDIR"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
  JAVA_ENDORSED_DIRS=`cygpath --path --windows "$JAVA_ENDORSED_DIRS"`
fi

# Set juli LogManager config file if it is present and an override has not been issued
if [ -z "$LOGGING_CONFIG" ]; then
  if [ -r "$FDFTPD_BASE"/conf/logging.properties ]; then
    LOGGING_CONFIG="-Djava.util.logging.config.file=$FDFTPD_BASE/conf/logging.properties"
  else
    # Bugzilla 45585
    LOGGING_CONFIG="-Dnop"
  fi
fi

if [ -z "$LOGGING_MANAGER" ]; then
  JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager"
else
  JAVA_OPTS="$JAVA_OPTS $LOGGING_MANAGER"
fi

# Uncomment the following line to make the umask available when using the
# org.apache.fdftpd.security.SecurityListener
#JAVA_OPTS="$JAVA_OPTS -Dorg.apache.fdftpd.security.SecurityListener.UMASK=`umask`"

# ----- Execute The Requested Command -----------------------------------------

# Bugzilla 37848: only output this if we have a TTY
if [ $have_tty -eq 1 ]; then
  echo "Using FDFTPD_BASE:   $FDFTPD_BASE"
  echo "Using FDFTPD_HOME:   $FDFTPD_HOME"
  echo "Using FDFTPD_TMPDIR: $FDFTPD_TMPDIR"
  if [ "$1" = "debug" ] ; then
    echo "Using JAVA_HOME:       $JAVA_HOME"
  else
    echo "Using JRE_HOME:        $JRE_HOME"
  fi
  echo "Using CLASSPATH:       $CLASSPATH"
  if [ ! -z "$FDFTPD_PID" ]; then
    echo "Using FDFTPD_PID:    $FDFTPD_PID"
  fi
fi

if [ "$1" = "jpda" ] ; then
  if [ -z "$JPDA_TRANSPORT" ]; then
    JPDA_TRANSPORT="dt_socket"
  fi
  if [ -z "$JPDA_ADDRESS" ]; then
    JPDA_ADDRESS="8000"
  fi
  if [ -z "$JPDA_SUSPEND" ]; then
    JPDA_SUSPEND="n"
  fi
  if [ -z "$JPDA_OPTS" ]; then
    JPDA_OPTS="-agentlib:jdwp=transport=$JPDA_TRANSPORT,address=$JPDA_ADDRESS,server=y,suspend=$JPDA_SUSPEND"
  fi
  FDFTPD_OPTS="$FDFTPD_OPTS $JPDA_OPTS"
  shift
fi

if [ "$1" = "debug" ] ; then
  if $os400; then
    echo "Debug command not available on OS400"
    exit 1
  else
    shift
    if [ "$1" = "-security" ] ; then
      if [ $have_tty -eq 1 ]; then
        echo "Using Security Manager"
      fi
      shift
      exec "$_RUNJDB" "$LOGGING_CONFIG" $JAVA_OPTS $FDFTPD_OPTS \
        -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
        -sourcepath "$FDFTPD_HOME"/../../java \
        -Djava.security.manager \
        -Djava.security.policy=="$FDFTPD_BASE"/conf/fdftpd.policy \
        -Dfdftpd.base="$FDFTPD_BASE" \
        -Dfdftpd.home="$FDFTPD_HOME" \
        -Djava.io.tmpdir="$FDFTPD_TMPDIR" \
        org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" start
    else
      exec "$_RUNJDB" "$LOGGING_CONFIG" $JAVA_OPTS $FDFTPD_OPTS \
        -Djava.endorsed.dirs="$JAVA_ENDORSED_DIRS" -classpath "$CLASSPATH" \
        -sourcepath "$FDFTPD_HOME"/../../java \
        -Dfdftpd.base="$FDFTPD_BASE" \
        -Dfdftpd.home="$FDFTPD_HOME" \
        -Djava.io.tmpdir="$FDFTPD_TMPDIR" \
        org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" start
    fi
  fi

elif [ "$1" = "run" ]; then

  shift
  if [ "$1" = "-security" ] ; then
    if [ $have_tty -eq 1 ]; then
      echo "Using Security Manager"
    fi
    shift
    eval exec \"$_RUNJAVA\" \"$LOGGING_CONFIG\" $JAVA_OPTS $FDFTPD_OPTS \
      -Djava.endorsed.dirs=\"$JAVA_ENDORSED_DIRS\" -classpath \"$CLASSPATH\" \
      -Djava.security.manager \
      -Djava.security.policy==\"$FDFTPD_BASE/conf/fdftpd.policy\" \
      -Dfdftpd.base=\"$FDFTPD_BASE\" \
      -Dfdftpd.home=\"$FDFTPD_HOME\" \
      -Djava.io.tmpdir=\"$FDFTPD_TMPDIR\" \
      org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" start
  else
    eval exec \"$_RUNJAVA\" \"$LOGGING_CONFIG\" $JAVA_OPTS $FDFTPD_OPTS \
      -Djava.endorsed.dirs=\"$JAVA_ENDORSED_DIRS\" -classpath \"$CLASSPATH\" \
      -Dfdftpd.base=\"$FDFTPD_BASE\" \
      -Dfdftpd.home=\"$FDFTPD_HOME\" \
      -Djava.io.tmpdir=\"$FDFTPD_TMPDIR\" \
      org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" start
  fi

elif [ "$1" = "start" ] ; then

  if [ ! -z "$FDFTPD_PID" ]; then
    if [ -f "$FDFTPD_PID" ]; then
      if [ -s "$FDFTPD_PID" ]; then
        echo "Existing PID file found during start."
        if [ -r "$FDFTPD_PID" ]; then
          PID=`cat "$FDFTPD_PID"`
          ps -p $PID >/dev/null 2>&1
          if [ $? -eq 0 ] ; then
            echo "Tomcat appears to still be running with PID $PID. Start aborted."
            exit 1
          else
            echo "Removing/clearing stale PID file."
            rm -f "$FDFTPD_PID" >/dev/null 2>&1
            if [ $? != 0 ]; then
              if [ -w "$FDFTPD_PID" ]; then
                cat /dev/null > "$FDFTPD_PID"
              else
                echo "Unable to remove or clear stale PID file. Start aborted."
                exit 1
              fi
            fi
          fi
        else
          echo "Unable to read PID file. Start aborted."
          exit 1
        fi
      else
        rm -f "$FDFTPD_PID" >/dev/null 2>&1
        if [ $? != 0 ]; then
          if [ ! -w "$FDFTPD_PID" ]; then
            echo "Unable to remove or write to empty PID file. Start aborted."
            exit 1
          fi
        fi
      fi
    fi
  fi

  shift
  touch "$FDFTPD_OUT"
  if [ "$1" = "-security" ] ; then
    if [ $have_tty -eq 1 ]; then
      echo "Using Security Manager"
    fi
    shift
    eval \"$_RUNJAVA\" \"$LOGGING_CONFIG\" $JAVA_OPTS $FDFTPD_OPTS \
      -Djava.endorsed.dirs=\"$JAVA_ENDORSED_DIRS\" -classpath \"$CLASSPATH\" \
      -Djava.security.manager \
      -Djava.security.policy==\"$FDFTPD_BASE/conf/fdftpd.policy\" \
      -Dfdftpd.base=\"$FDFTPD_BASE\" \
      -Dfdftpd.home=\"$FDFTPD_HOME\" \
      -Djava.io.tmpdir=\"$FDFTPD_TMPDIR\" \
      org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" start \
      >> "$FDFTPD_OUT" 2>&1 "&"

  else
    eval \"$_RUNJAVA\" \"$LOGGING_CONFIG\" $JAVA_OPTS $FDFTPD_OPTS \
      -Djava.endorsed.dirs=\"$JAVA_ENDORSED_DIRS\" -classpath \"$CLASSPATH\" \
      -Dfdftpd.base=\"$FDFTPD_BASE\" \
      -Dfdftpd.home=\"$FDFTPD_HOME\" \
      -Djava.io.tmpdir=\"$FDFTPD_TMPDIR\" \
      org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" start \
      >> "$FDFTPD_OUT" 2>&1 "&"

  fi

  if [ ! -z "$FDFTPD_PID" ]; then
    echo $! > "$FDFTPD_PID"
  fi

elif [ "$1" = "stop" ] ; then

  shift

  SLEEP=5
  if [ ! -z "$1" ]; then
    echo $1 | grep "[^0-9]" >/dev/null 2>&1
    if [ $? -gt 0 ]; then
      SLEEP=$1
      shift
    fi
  fi

  FORCE=0
  if [ "$1" = "-force" ]; then
    shift
    FORCE=1
  fi

  if [ ! -z "$FDFTPD_PID" ]; then
    if [ -f "$FDFTPD_PID" ]; then
      if [ -s "$FDFTPD_PID" ]; then
        kill -0 `cat "$FDFTPD_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          echo "PID file found but no matching process was found. Stop aborted."
          exit 1
        fi
      else
        echo "PID file is empty and has been ignored."
      fi
    else
      echo "\$FDFTPD_PID was set but the specified file does not exist. Is Tomcat running? Stop aborted."
      exit 1
    fi
  fi

  eval \"$_RUNJAVA\" $JAVA_OPTS \
    -Djava.endorsed.dirs=\"$JAVA_ENDORSED_DIRS\" -classpath \"$CLASSPATH\" \
    -Dfdftpd.base=\"$FDFTPD_BASE\" \
    -Dfdftpd.home=\"$FDFTPD_HOME\" \
    -Djava.io.tmpdir=\"$FDFTPD_TMPDIR\" \
    org.foxteam.noisyfox.fdf.Startup.Bootstrap "$@" stop

  if [ ! -z "$FDFTPD_PID" ]; then
    if [ -f "$FDFTPD_PID" ]; then
      while [ $SLEEP -ge 0 ]; do
        kill -0 `cat "$FDFTPD_PID"` >/dev/null 2>&1
        if [ $? -gt 0 ]; then
          rm -f "$FDFTPD_PID" >/dev/null 2>&1
          if [ $? != 0 ]; then
            if [ -w "$FDFTPD_PID" ]; then
              cat /dev/null > "$FDFTPD_PID"
            else
              echo "Tomcat stopped but the PID file could not be removed or cleared."
            fi
          fi
          break
        fi
        if [ $SLEEP -gt 0 ]; then
          sleep 1
        fi
        if [ $SLEEP -eq 0 ]; then
          if [ $FORCE -eq 0 ]; then
            echo "Tomcat did not stop in time. PID file was not removed."
          fi
        fi
        SLEEP=`expr $SLEEP - 1 `
      done
    fi
  fi

  if [ $FORCE -eq 1 ]; then
    if [ -z "$FDFTPD_PID" ]; then
      echo "Kill failed: \$FDFTPD_PID not set"
    else
      if [ -f "$FDFTPD_PID" ]; then
        PID=`cat "$FDFTPD_PID"`
        echo "Killing Tomcat with the PID: $PID"
        kill -9 $PID
        rm -f "$FDFTPD_PID" >/dev/null 2>&1
        if [ $? != 0 ]; then
          echo "Tomcat was killed but the PID file could not be removed."
        fi
      fi
    fi
  fi

elif [ "$1" = "configtest" ] ; then

    eval \"$_RUNJAVA\" $JAVA_OPTS \
      -Djava.endorsed.dirs=\"$JAVA_ENDORSED_DIRS\" -classpath \"$CLASSPATH\" \
      -Dfdftpd.base=\"$FDFTPD_BASE\" \
      -Dfdftpd.home=\"$FDFTPD_HOME\" \
      -Djava.io.tmpdir=\"$FDFTPD_TMPDIR\" \
      org.apache.fdftpd.startup.Bootstrap configtest
    result=$?
    if [ $result -ne 0 ]; then
        echo "Configuration error detected!"
    fi
    exit $result

elif [ "$1" = "version" ] ; then

    "$_RUNJAVA"   \
      -classpath "$FDFTPD_HOME/lib/fdftpd.jar" \
      org.apache.fdftpd.util.ServerInfo

else

  echo "Usage: fdftpd.sh ( commands ... )"
  echo "commands:"
  if $os400; then
    echo "  debug             Start Catalina in a debugger (not available on OS400)"
    echo "  debug -security   Debug Catalina with a security manager (not available on OS400)"
  else
    echo "  debug             Start Catalina in a debugger"
    echo "  debug -security   Debug Catalina with a security manager"
  fi
  echo "  jpda start        Start Catalina under JPDA debugger"
  echo "  run               Start Catalina in the current window"
  echo "  run -security     Start in the current window with security manager"
  echo "  start             Start Catalina in a separate window"
  echo "  start -security   Start in a separate window with security manager"
  echo "  stop              Stop Catalina, waiting up to 5 seconds for the process to end"
  echo "  stop n            Stop Catalina, waiting up to n seconds for the process to end"
  echo "  stop -force       Stop Catalina, wait up to 5 seconds and then use kill -KILL if still running"
  echo "  stop n -force     Stop Catalina, wait up to n seconds and then use kill -KILL if still running"
  echo "  configtest        Run a basic syntax check on server.xml - check exit code for result"
  echo "  version           What version of tomcat are you running?"
  echo "Note: Waiting for the process to end and use of the -force option require that \$FDFTPD_PID is defined"
  exit 1

fi
