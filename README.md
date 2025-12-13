# rlauxe-viewer

WORK IN PROGRESS
_last update: 12/13/2025_

## Building Rlauxe

See [Rlauxe Developer](https://github.com/JohnLCaron/rlauxe/blob/main/docs/Developer.md).

## Building Rlauxe-viewer

Download the repo:
````
cd <devhome>
git clone https://github.com/JohnLCaron/rlauxe-viewer.git
````

Build the application:
````
cd rlauxe-viewer
./gradlew clean assemble uberJar
````

## Running Rlauxe-viewer

From gradle:

````
cd devhome/rlauxe-viewer
java -jar viewer/build/libs/viewer-uber.jar
````

From IntelliJ:

In the Project Panel, navigate to the source file

_viewer/src/main/java/org/cryptobiotic/rlauxe/viewer/ViewerMain.java_

In the editor, there should be a clickable green button on class ViewerMain.

After the application starts, use the Directory Chooser button ![image](images/DirectoryChooserIcon.png)
tp bring up the Directory Chooser,
and navigate to the directory where the Audit Record is stored (for the test cases, its always named "audit")

![image](images/DirectoryChooser.png)


The first time you start up, you can change the window size, the table sizes, the order and size of the table headings, etc.
When you exit, your choices will be saved for the next time you start up.


## Using

Viewing [rlauxe](https://github.com/JohnLCaron/rlauxe) audit records in a simple desktop UI.

Create an Election Record, eg with **org.cryptobiotic.rlauxe.cli.TestRunCli.testCliRoundClca** (in the main repo), then
point rlauxe-viewer at the top directory and explore. 

````
java -jar /home/stormy/dev/github/frontend/rlaux-viewer/viewer/build/libs/viewer-uber.jar
````
