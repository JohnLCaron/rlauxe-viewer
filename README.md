# rlauxe-viewer

_last update: 06/29/2026_

<!-- TOC -->
* [rlauxe-viewer](#rlauxe-viewer)
  * [Building Rlauxe-viewer](#building-rlauxe-viewer)
  * [Starting Rlauxe-viewer](#starting-rlauxe-viewer)
    * [From the command line](#from-the-command-line)
    * [From IntelliJ:](#from-intellij)
  * [Using rlauxe-viewer](#using-rlauxe-viewer)
    * [Setting UI choices](#setting-ui-choices)
    * [Showing the results of an audit](#showing-the-results-of-an-audit)
  * [Specialized Viewers](#specialized-viewers)
<!-- TOC -->

A GUI for the [Rlauxe Risk Limiting Audit library](https://github.com/JohnLCaron/rlauxe).

## Building Rlauxe-viewer

Download the repo:
````
cd <devhome>
git clone https://github.com/JohnLCaron/rlauxe-viewer.git
````

Build the viewer application:

````
cd <devhome>/rlauxe-viewer
./gradlew clean assemble uberJar
````

and check that `viewer/build/libs/viewer-uber.jar` was built.

## Starting Rlauxe-viewer

### From the command line

`
cd devhome/rlauxe-viewer
`

For Belgium elections:

`
java -jar viewer/build/libs/rlauxe-viewer-uber.jar -belgiumAudit
`

For Colorado elections:

`
java -jar viewer/build/libs/rlauxe-viewer-uber.jar -corlaAudit
`

For all audits:

`
java -jar viewer/build/libs/rlauxe-viewer-uber.jar
`

For any of these, you can optionally set the default directory where Audit Records are kept, eg:

`
java -jar viewer/build/libs/rlauxe-viewer-uber.jar -belgiumAudit -datadir /my/rlauxe/audits
`

which is where the "Audit Record Chooser" widget will start from.


### From IntelliJ:

In the Project Panel, navigate to the source file

_viewer/src/main/java/org/cryptobiotic/rlauxe/viewer/ViewerMain.java_

In the editor, there should be a clickable green button on class ViewerMain:

![image](docs/images/ViewerMain.png).

## Using rlauxe-viewer

After the application starts, use the "Audit Record Chooser" button
![image](uibase/src/main/resources/resources/ui/png/Open-File-Folder-icon.png)
to bring up the "Audit Record Chooser" widget,
then navigate to the directory where the Audit Record is stored.

Note that for Belgium or Corla audits, this is the top directory containing the component counties or province audits,
but for regular audits, its the subdirectory named "audit" under the test case name. (Sorry about that).

![image](docs/images/DirectoryChooser.png)

The **Contests** tab will be populated from the chosen audit record:

![image](docs/images/ContestsPanel.png)

Select a Contest in the top table, right click and choose **Show Contest**. Each visible field is shown with a description
and its value for that contest, along with a summary of the contest and the reported votes for each candidate:

![image](docs/images/ShowContest.png)

### Setting UI choices

Change the Font Size from the System Menu in the upper left:

![image](docs/images/SystemMenu.png)

Closing the viewer window will save your UI configuration (in _~/.rlauxe/_). 
Choose "Exit Viewer NO Save" to exit without saving.
Choose "Save Preferences to Disk" to save without exiting.

The upper right corner gear widget in each table

![image](docs/images/TableColumnChooser.png).

allows you to change which columns are visible:

![image](docs/images/TableColumnContests.png).

Change the order and size of the table headings, by clicking and dragging the table headers:

![image](docs/images/TableHeaders.png).

Change the table sort order by clicking the table header of the field you want to sort on. 


### Showing the results of an audit

If the audit has already run, clicking on the **AuditRounds** tab will show you the results by round:

![image](docs/images/AuditRounds.png)

In this case, the audit completed successfully in a single round. The **Assertion Rounds** table shows all the assertions needed by the
audit. By clicking on a header, you can sort the table by that column's value. In this example, we have sorted on the "estimate new Mvrs" colums, to see which assertions require the most samples.

The **Estimation Rounds** table shows the estimated distribution of samples needed for the selected assertion. 
The **Audit Results** table shows the results of the actual Audit (if its was done).

If you right click on the Audit Result line, and choose **Rerun audit with Details**, the audit is rerun and shows the
actual audit sample values, betting value, pvalue, mvr and cvr. 

![image](docs/images/AuditDetail.png)

In this example, the votes always match, so the assort value always equals noerror, and the estimate should be spot on.
The fields shown in the **Rerun audit with Details** report are:

* j  : sample number
* xj : assort value
* lamj : BettingMart bet
* tj : (1 - lamj*(xj - mj) )
* Tj : Product (tj, j=1..j)
* pvalue = 1 / Tj
* location: sampled CVR's location
* mvr votes: MVR's votes
* card: CVR's votes

## Specialized Viewers

[The Rlauxe getting started page](https://github.com/JohnLCaron/rlauxe/blob/main/docs/Developer.md#getting-started) has instructions
on how to build all of the case study datasets.

* [Belgium Viewer](docs/BelgiumViewer.md)
* [Colorado Viewer](docs/CorlaViewer.md)






