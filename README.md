# rlauxe-viewer

WORK IN PROGRESS
_last update: 12/13/2025_

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

In the editor, there should be a clickable green button on class ViewerMain:

![image](docs/images/ViewerMain.png).

After the application starts, use the Directory Chooser button ![image](docs/images/DirectoryChooserIcon.png)
to bring up the Directory Chooser,
then navigate to the directory where the Audit Record is stored (note that for the test cases, its always a subdirectory
named "audit" of the test case name).

![image](docs/images/DirectoryChooser.png)

The first time you start up, you can change the window size, the table sizes, the order and size of the table headings, etc.
When you exit, your choices will be saved for the next time you start the application (in ~/.rlauxe/RlauxeViewer.xml).
