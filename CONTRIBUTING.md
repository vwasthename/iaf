

# How to contribute

Thanks for reading this we're glad you're taking an interest in contributing to our framework.
We want you working on things you're excited about, there are however plenty of [issues](https://github.com/ibissource/iaf/issues) that can be picked up.

**Please be advised that we use our own repository manager for snapshot builds.**
Either use our public `ibissource` or private `proprietary` profile when running Maven.


## Running the Frank!Framework

If you want to experiment with the Frank!Framework, you can use the [Frank!Runner](https://github.com/ibissource/frank-runner). If you want to stick with Maven, you can follow the instructions of this section.

Initial:

- git clone https://github.com/ibissource/iaf
- cd iaf
- mvn
- cd example
- mvn jetty:run
- [http://localhost:8080/iaf](http://localhost:8080/iaf)


After modifying a project file:

- ctrl-c
- cd .. ; mvn clean install ; cd example ; mvn jetty:run

The jetty-maven-plugin requires Maven 3 and Java 1.8. We tested these instructions with Maven 3.6.3.

## Submitting changes

Please send a [GitHub Pull Request](https://github.com/ibissource/iaf/pull/new/master) with a clear list of what you've done (read more about [pull requests](https://help.github.com/articles/about-pull-requests/)). When you send a pull request, make sure all of your commits are atomic (one feature per commit) and that the title is clear, obvious and informative!

Always write a clear log message for your commits. Add the current year, the name of the copyright owner and the copyright notice (when not already present) to adjusted and new files. See:

- https://www.gnu.org/licenses/gpl-howto.html
- https://www.gnu.org/prep/maintain/html_node/Copyright-Notices.html
- https://opensource.stackexchange.com/questions/2367/how-do-i-properly-specify-the-years-of-copyright
- http://www.apache.org/licenses/LICENSE-2.0#apply

When relevant to the end-user first add a line to [release notes](RELEASES.md)
at the end of [section 'Upcoming'](RELEASES.md#upcoming) with a functional
description targeted to end-user. There's no limit on the length of this line.
After that create the commit message targeted to developers and Git tools with a
short one-line description in the first line (in some cases the same as the
release notes line) and if necessary a more detailed explanatory text after a
blank line. See:

- http://chris.beams.io/posts/git-commit/#seven-rules
- http://stackoverflow.com/questions/2290016/git-commit-messages-50-72-formatting
- http://git-scm.com/book/ch5-2.html

One-line messages are fine for small changes, but bigger changes should look like this:

    $ git commit -m "A brief summary of the commit
    > 
    > A paragraph describing what changed and its impact."

When you have created a pull request, you can visit it on GitHub.
If your work needs improvement, WeAreFrank! will add comments that
are visible inside your pull request. For each comment, GitHub keeps
a property whether the comment has been resolved. This is useful
when a pull request goes through many iterations of improvements.
When you want to follow-up on the latest comments, it is confusing
to see comments that have been resolved already.

What is our policy to setting comments to resolved? First, please
always answer to the comment stating what you did with it. Even
if you simply granted it, please say so.

After answering, you can set the
comment to resolved if it was clear and if you have fully granted
it. If the comment was not clear to you or if you only partially
granted it, then do not resolve the comment yourself. WeAreFrank!
should resolve the comment when they are satisfied.

If a comment you answered to is very old and if no additional
answer has been added to the conversation for a long time, you
can also set it to resolved to ease the review process.

## Coding conventions

Start reading our code and you'll get the hang of it. We optimize for readability:

  * We indent using tabs, not spaces.
  * We ALWAYS put spaces after list items and method parameters (`[1, 2, 3]`, not `[1,2,3]`) and around operators (`x += 1`, not `x+=1`).
  * When you have long method calls or method definitions, please keep them on one line. When you have many similar lines of long method calls,
  they are easier to read without artificial line breaks. Lines are allowed to be as long as 150 characters.
  * This is open source software. Consider the people who will read your code, and make it look nice for them. It's sort of like driving a car: Perhaps you love doing donuts when you're alone, but with passengers the goal is to make the ride as smooth as possible.
  * Use Unix style newlines.
  * Each class that can be used in a configuration must contain the following documentation:
    - Class level IbisDoc, not larger then 5 to 10 lines
    - For each configurable attribute, IbisDoc must not be larger then 2 lines
    - Any examples and more detailed information, that has to be incorperated in to the IbisManual, should be provided as a separate file(s) attached to the pull request
  * In JavaDoc comments, do not use the `’` character. It breaks the Frank!Doc. You can use `'` instead.
  * Please do not modify files purely for the sake of formatting, or do so in a dedicated pull request. Formatting changes make a pull request harder to understand for reviewers.
  * You can experiment with Eclipse's formatting capabilities. In the preferences window, search for the string "tab". You will get an overview of all the options about formatting. The following options are interesting in particular:
    - There are many screens in which you can define that you use tabs instead of spaces for indentation. Please visit them all to configure that you use tabs.
    - In Java | Code Style, you can define your own named code style. When you open the dialog, you see many options on how to format Java code. You can set the maximum length of lines here, for example, to avoid artificial line breaks.

WeAreFrank! has introduced [Project Lombok](https://projectlombok.org/) in this source code. Please keep the following in mind when using it:

  * With Lombok, you do not have to code getters and setters anymore. You can generate them by by putting annotations `@Getter` and `@Setter` on the backing field. This is very useful. But please do NOT put the `@Getter` or `@Setter` on the class. This makes less lines of code, but there is a drawback. You cannot see the call hierarchy anymore of a getter or a setter. When you put the annotations on the method level, you can still see the call hierarchy: right-click the `@Getter` or `@Setter` and select "Open Call Hierarchy" in Eclipse.
  * For the sake of readability, please put the `@Getter` or `@Setter` annotations inside the variable declaration: "`private @Getter @Setter MyType myField`".

## Testing

Before creating a pull request with your changes, please run the iaf-test module's test scenarios. If all tests pass, the chance of Frank developers running into unexpected errors will be reduced to a minimum. Instructions on how to run the iaf-test scenarios can be found [here](TESTING_WITH_IAF-TEST.md).

We have yet to test the compatibility of the iaf-test module with Jetty. Until then, the only verified way to run the module is on a Tomcat server in Eclipse. However, feel free to try and run it on Jetty yourself! If it works for you, we'd love to hear about it. :)

### Checking differences within Larva

The iaf-test module runs Larva tests, see https://frank-manual.readthedocs.io/en/latest/gettingStarted/helloLarva.html. Larva tests
execute some system-under-test, for example a Frank configuration. The
output of the system-under-test is often compared to some expected value.
The Graphical User Interface of Larva shows these differences, but sometimes
it works better to use a third-party tool like WinMerge.

If you are developing under Windows, you can do the following to set this up:

- Follow the instructions of section "Developing with Eclipse", see below.
- Download the WinMerge installer from https://winmerge.org/. After accepting cookies, you may have to refresh your browser before the download starts.
- Run the installer you downloaded. Make sure that WinMerge is added to the system path.
- Lookup the path to the WinMerge executable. You may do this by viewing the system path.
- In the Eclipse Project Explorer, you have a folder "Servers". Your Tomcat installation appears as a subfolder. Under that subfolder, open file `catalina.properties`.
- In `catalina.properties`, add: `larva.windiff.command=c:/Program Files (x86)/WinMerge/winmergeu`, but replace the value after the `=` by the path to WinMerge on your laptop.
- Start module iaf-test.
- If you see a "Differences:" panel, you have a button "windiff" above it. Please press it to see the differences in WinMerge. NOTE: You only see a "Differences:" panel if you select a low log level. Mind the pull-down menu labeled "Log level".
- If all your tests succeed, you do not have "Differences:" panels and you have no "windiff" buttons. To test your WinMerge integration, you may have to temporarily edit a test scenario to make it fail. 

## Developing with Eclipse

You can download Eclipse and load the Frank!Framework sources into it using the [Frank!Runner](https://github.com/ibissource/frank-runner). It will also take care of project Lombok. If you want to understand what you are doing, you can do it manually using the instructions of this section. If you use the Frank!Runner, you still need to do the Eclipse configurations that are explained here.

### Install Eclipse with Lombok

- Download Eclipse from [here](https://www.eclipse.org/downloads/packages/), choosing "Eclipse IDE for Enterprise Java Developers". We tested our instructions with version 2021-09, but older versions should also work. Note that 64-bit Eclipse doesn't work with 32-bit JRE/JDK (doesn't start without any message). There is no installer. To install Eclipse, just unzip your download to a directory of your choice.
- Download the Lombok library. This is easier than letting Maven do the download and then finding the .jar file in Eclipse. Browse to https://projectlombok.org/. On the top menu, choose "Download".
- Download version 1.18.12. You may need the link "older versions".
- Run the .jar you downloaded. Under Windows you can double-click it.
- You see a GUI. The GUI may automatically find your Eclipse installation. If this does not work, use the button "Specify location". You should point to the eclipse.exe file.
- Press Install / Update.
- If you have trouble with these instructions, then you can get help on the https://projectlombok.org/ site. On the top menu, choose "install" | "Eclipse".
- The Frank!Framework can only run on Java 8. Please install a Java 8 JDK in addition to the JRE that is included in your Eclipse installation. You can find it [here](https://www.oracle.com/nl/java/technologies/javase/javase8u211-later-archive-downloads.html).

**NOTE:** In earlier versions of this file, you were asked to start Eclipse using a Java 8 JRE. That does not work anymore 
with Eclipse 2021-09. You must start Eclipse from a Java 11 or later JRE, typically the JRE delivered with Eclipse, and then configure a Java 8 **JDK** in the workspace or project settings. A Java 8 JRE is not sufficient because some unit tests require a JDK. Configuring Eclipse to use the Java 8 JDK will be explained later.

### Configure Eclipse

- If you want to change -vm options in `eclipse.ini`, please be aware that that option is present already. Update the existing option and do not introduce a duplicate -vm.
- You need to adjust `eclipse.ini` to avoid problems with Lombok, see https://stackoverflow.com/questions/69218106/eclipse-not-able-to-open-java-files-unable-to-make-protected-final-java-lang. Please append the following lines:

      --illegal-access=warn
      --add-opens java.base/java.lang=ALL-UNNAMED

- Start Eclipse and close Welcome.
- Make sure that the default text file line delimiter is set to Unix and default encoding is set to UTF-8: Window, Preferences, General, Workspace, New text file line delimiter: Unix, Text file encoding: UTF-8.
- There are a few unit tests that only run when a JDK is available; a JRE is not sufficient for them. Let Eclipse run unit tests using a JDK, as follows. Go to Window | Preferences. Go to Java | Installed JREs. Press Add... and browse to an installation directory of a JDK. Finally, make it the default by clicking the checkbox.

### Import the source code

- Make sure Maven is able to access the internet. E.g. when behind a proxy: Window, Preferences, Maven, User Settings, settings.xml should exist and contain proxy configuration.
- Window, Open Perspective, Other..., Git, OK, Clone a Git repository, URI: https://github.com/ibissource/iaf.git, Next, Next, Finish.
- Optionally (when you have access to the proprietary jars some modules depend on) add your Nexus credentials and enable the proprietary profile in your maven settings.xml
- Return to the Java EE perspective.
- In the main menu, choose File | Import...
- The Import wizard appears which allows you to import many different kinds of projects. Open "Maven" and select "Existing Maven Projects". Click "Next".
- Browse to the directory in which you cloned the iaf Git repository. A list of `pom.xml` files appears, one for each subproject.
- **deselect**: iaf-coolgen, iaf-ibm, iaf-ifsa, iaf-sap, iaf-tibco and iaf-idin (unless you have access to the proprietary repository), Finish.
- Window, Open Perspective, Other..., Java EE.
- Rightclick iaf, Maven, Update Project..., OK. Now Eclipse will update the classpath settings according to the module pom file. (Updating the project may take a while!)

### Set up a Tomcat server in Eclipse

- Servers, No servers are available. Click this link to create a new server..., Apache, Tomcat v7.0 Server, Next, Browse..., select the root folder of a Tomcat installation (when not available download the latest version of [Tomcat](http://tomcat.apache.org/) (version 7.0.47+ is known to work)), OK, Finish.
- Double click Tomcat v7.0 Server at localhost, Open launch configuration, Arguments, VM arguments, add -Ddtap.stage=LOC, OK, Modules, Add Web Module..., iaf-example, OK, File, Save
- Right click Tomcat v7.0 Server at localhost, Start.
- Browse the IAF console at [http://localhost:8080/iaf-example/](http://localhost:8080/iaf-example/).

### In some cases you might want/need to:

- Rightclick iaf, Maven, Update Project..., OK.
- Delete .setting folder(s) in broken iaf module(s), followed by rightclick iaf, Maven, Update Project..., OK.
- Enable Project, Build Automatically
- Right click Tomcat Server at localhost, Clean...
- Change newlines in .classpath and org.eclipse.wst.common.component files back to Unix newlines.
- Rightclick pom.xml (in iaf), Run As, Maven build..., JRE, make sure a JDK (not a JRE) is used, Refresh, Refresh resources upon completion, Specific resources, Specify Resources..., iaf (Using "The project containing the selected resource" doesn't seem to work), Finish, Run.
- The local Maven repository might contain corrupt jar files which for example will result in java.lang.NoClassDefFoundError: org/aspectj/lang/ProceedingJoinPoint when starting Tomcat. Remove the jar file from the repository to make Maven download the file again.
- When changing IAF versions Eclipse doesn't always automatically clean the  tomcat deploy folder (wtpwebapps). Rightclick project, Run As, Maven Clean, followed by  Right click Tomcat v7.0 Server at localhost, Clean...
- Check the deployment assemblies:
  - Right-click iaf-webapp and choose Properties. In the left-hand menu select "Deployment Assembly". To the right, you see what Eclipse directories are mapped to what directories within Apache Tomcat. You should have:
    - `src/main/webapp` to `/`
    - `target/m2e-wtp/web-resources` to `/`
    - `iaf-akami` to `WEB-INF/lib/ibis-adapterframework-akami-X.Y-SNAPSHOT.jar`
    - ...
    - `iaf-larva `to `WEB-INF/lib/ibis-adapterframework-larva-X.Y-SNAPSHOT.jar`
    - `Maven Dependencies` to `WEB-INF/lib`
  - Sometimes, an additional mapping `/` to `/` is present. This is wrong; if you see it, delete it!
  - Right-click iaf-example and choose Properties. In the left-hand menu select "Deployment Assembly". To the right, you see what Eclipse directories are mapped to what directories within Apache Tomcat. You should have:
    - `/src/main/java` to `WEB-INF/classes`
    - `/src/main/resources` to `WEB-INF/classes`
    - `/src/main/webapp` to `/`
    - `/target/m2e-wtp/web-resources` to `/`
    - `iaf-core` to `WEB-INF/lib/ibis-adapterframework-core-X.Y-SNAPSHOT.jar`
    - `iaf-example` to -
    - ...
    - `iaf-webapp` to -
    - `Maven Dependencies` to `WEB-INF/lib`
  - Sometimes, an additional mapping `/` to `/` is present. This is wrong; if you see it, delete it!
- When running Tomcat v8.5, you can disable its pluggability scans to prevent unnecessarily long startup times. To do this, add the following element within the Context element of your Tomcat server's _context.xml_ file:
######
    <JarScanner>
        <JarScanFilter defaultPluggabilityScan="false" />
    </JarScanner>
- In case Eclipse is continuously downloading javadoc and source files, you might need to upgrade your M2E installation.

### Let Eclipse check Javadoc comments

Please ensure that your Javadoc comments are correct. Eclipse can check this for you. Please do the following:

- In the main menu, open Windows | Preferences.
- Go to Java | Compiler | Javadoc.
- Check checkbox "Process Javadoc comments".
- In pull-down menu "Malformed Javadoc comments", select "Error".
- In pull-down menu "Only consider members as visible as", choose "Private".
- Check checkbox "Validate tag arguments".
- Uncheck "Report non visible references" and "Report deprecated references".
- In pull-down menu "Missing tag descriptions", select "Ignore".
- In pull-down menu "Missing Javadoc tags", select "Ignore".

## Developing with IntelliJ

- Clone this any way you like. E.g. at the commandline: git clone git@github.com:ibissource/iaf.git
- From File -> Open... Select iaf folder and import it as a Maven project.
- Make sure to select Java 7 or Java 8 as a default JDK.
- Download Tomcat 8.5 from https://tomcat.apache.org/download-80.cgi and export it anywhere you like. (On windows make sure to extract it on a folder which can be edited by non-admin users.)
- On top right click "Add Configurations..." then click + button. Click "More items" on the bottom of the list and select Tomcat Server -> Local from the new list.
- Click Configure next to the Application Server and Select your Tomcat Home directory.
- Add -Ddtap.stage=LOC to the VM Options
- On deployment tab click + -> artifacts... and then select ibis-adapterframework-example:war
- Name your configuration and save it
- Open Maven window by clicking Maven button on your right and open execution window by clicking "m" button. Then run command "mvn clean install -Dmaven.javadoc.skip=true verify"
- Run your configuration and you are ready to go.

# Frank!Doc - Documentation for Frank developers

The Frank!Framework is used by Frank developers. They write XML files (Frank configurations) to solve software integration problems. These XML files are translated to Java objects that will collaborate to do the intended job. The Java objects have the types that are available in this repository. For example, when a Frank configuration contains a tag `<XsltPipe>`, an object of type `XsltPipe` is instantiated.

The syntax and the meaning of Frank configurations are documented in the following files (the Frank!Doc):
* `./target/frankDoc/js/frankDoc.json`. This file is read by a web application implemented in sub-project `webapp`. This web application will render the information in the JSON file. Frank developers use the website as a reference manual. See https://ibis4example.ibissource.org/iaf/frankdoc.
* `./target/frankDoc/xml/xsd/FrankConfig-strict.xsd`. This file is given to Frank developers. They reference this XSD in their Frank config XML files. When they open an XML file, their text editor will use `FrankConfig-strict.xsd` to support autocomplete and to provide tooltip information.
* `./target/frankDoc/xml/xsd/FrankConfig-compatibility.xsd`. This file is added to the Frank!Framework .jar file during the Maven build. The file is then used at runtime to parse Frank configurations.

The Frank!Doc is created by a doclet (see https://docs.oracle.com/javase/8/docs/technotes/guides/javadoc/doclet/overview.html) that is implemented in our project https://github.com/ibissource/frank-doc. The doclet is executed during the Maven build of this project. The information in the Frank!Doc is based on the Java source files in this repository. As a developer of the F!F, please take care that the Frank!Doc remains correct and helpful for Frank developers. For further instructions, see [FRANKDOC.md](./FRANKDOC.md).

Thanks,
The Frank!Framework Team
