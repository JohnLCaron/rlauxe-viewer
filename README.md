# rlauxe-viewer

WORK IN PROGRESS
_last update: 05/26/2026_

<!-- TOC -->
* [rlauxe-viewer](#rlauxe-viewer)
  * [Building Rlauxe-viewer](#building-rlauxe-viewer)
  * [Audit Case Study Data](#audit-case-study-data)
  * [Starting Rlauxe-viewer](#starting-rlauxe-viewer)
    * [From the command line](#from-the-command-line)
    * [From IntelliJ:](#from-intellij)
    * [Setting UI choices](#setting-ui-choices)
  * [Showing the results of an audit](#showing-the-results-of-an-audit)
  * [Special Features for Belgium Audits](#special-features-for-belgium-audits)
    * [Choose number of mvrs](#choose-number-of-mvrs)
      * [Setting mvrLimit in the samplesLimit.txt file](#setting-mvrlimit-in-the-sampleslimittxt-file)
    * [Choose candidate coalitions](#choose-candidate-coalitions)
  * [Special Features for Corla Audits](#special-features-for-corla-audits)
<!-- TOC -->

## Building Rlauxe-viewer

Download the repo:
````
cd <devhome>
git clone https://github.com/JohnLCaron/rlauxe-viewer.git
````

Build the viewer application:

````
cd rlauxe-viewer
./gradlew clean assemble uberJar
````

and check that `viewer/build/libs/viewer-uber.jar` was built.

## Audit Case Study Data

[The Rlauxe getting started page](https://github.com/JohnLCaron/rlauxe/blob/main/docs/Developer.md#getting-started) has instructions
on how to build all of the case study datasets.

(Or contact me for the prebuilt Belgium 2024 dataset).


## Starting Rlauxe-viewer

### From the command line

`cd devhome/rlauxe-viewer
`

For Belgium elections:

`java -jar viewer/build/libs/viewer-uber.jar -belgiumAudit
`

For Colorado elections:

`java -jar viewer/build/libs/viewer-uber.jar -corlaAudit
`

For all audits:

`java -jar viewer/build/libs/viewer-uber.jar
`

For any of these, you can optionally set the default directory where Audit Records are kept, eg:

`java -jar viewer/build/libs/viewer-uber.jar -belgiumAudit -datadir /my/rlauxe/audits
`

which is where the "Audit Record Chooser" widget will start from.

### From IntelliJ:

In the Project Panel, navigate to the source file

_viewer/src/main/java/org/cryptobiotic/rlauxe/viewer/ViewerMain.java_

In the editor, there should be a clickable green button on class ViewerMain:

![image](docs/images/ViewerMain.png).

After the application starts, use the "Audit Record Chooser" button
![image](uibase/src/main/resources/resources/ui/png/Open-File-Folder-icon.png)
to bring up the "Audit Record Chooser" widget,
then navigate to the directory where the Audit Record is stored.

Note that for Belgium or Corla audits, this is the top directory containing the component counties or province audits,
but for regular audits, its the subdirectory named "audit" under the test case name. (Sorry about that).

![image](docs/images/DirectoryChooser.png)

The **Contests** tab will be populated from the chosen audit record:

![image](docs/images/ContestsPanel.png)

Select the Contest in the top table, right click and choose **Show Contest**. The Detail window shows more information about that Contest.
For Dhondt, it summarizes the scoring rounds and winning parties, as well as the parties who didnt clear the threshold (with an asterisk):

![image](docs/images/ShowContest.png)

### Setting UI choices

Change the Font Size from the System Menu in the upper left:

![image](docs/images/SystemMenu.png) 

Change the window size, the table sizes, the order and size of the table headings, etc. For each table, choose which 
columns are displayed from the upper right corner gear widget:

![image](docs/images/TableColumnChooser.png).

When you exit, your choices will be saved  (in ~/.rlauxe/RlauxeViewer.xml) for the next time you start the application.


## Showing the results of an audit

If the audit has already run, clicking on the **AuditRounds** tab will show you the results by round:

![image](docs/images/AuditRounds.png)

In this case, the audit completed successfully in a single round. The **Assertion Rounds** table shows all the assertions needed by the
audit. By clicking on a header, you can sort the table by that column's value. In this example, we have sorted on the "estimate new Mvrs" colums, to see which assertions require the most samples.

The **Estimation Rounds** table shows the estimated distribution of samples needed for the selected assertion. 
The **Audit Results** table shows the results of the actual Audit (if its was done).

If you right click on the Audit Result line, and choose **Rerun audit with Details**, the audit is rerun and shows the
actual audit sample values, betting value, pvalue, mvr and cvr. The fields are:

  * j  : sample number
  * xj : assort value
  * lamj : BettingMart bet
  * tj : (1 - lamj*(xj - mj) )
  * Tj : Product (tj, j=1..j)
  * pvalue = 1 / Tj
  * location: sampled CVR's location
  * mvr votes: MVR's votes
  * card: CVR's votes

![image](docs/images/AuditDetail.png)

In this example, the votes always match, so the assort value always equals noerror, and the estimate should be spot on.


## Special Features for Belgium Audits

To bring up the specialized Belgium Audit viewer, add -belgiumAudit to the command line, for example:

`java -jar viewer/build/libs/viewer-uber.jar -belgiumAudit`

Use the Audit Record chooser to navigate to the top of the Belgium Audit Record, and select it.
Now there is a single tab:  

![image](docs/images/belgium/BelgiumViewer.png)

The first table shows all the contests, ie the electoral districts. Choosing a contest shows its assertions in the second table. 
The total seats for each party (aka candidate) across all contests are shown in the third table.

### Choose number of mvrs

In the assertion table, the _estMvrs_ column shows how many mvrs will be needed to satisfy the risk limit for that assertion,
when no discrepancies between the mvrs and the cvrs are found.

The same field in the contest table shows the maximum needed over all assertions for the contest. The contest _mvrLimit_ allows you
to limit the number of mvrs for that electoral district. Click in the cell, and enter the mvr limit for that contest.
The value of -1 means no limit, so the audit will use the value of estMvrs.

If mvrLimit is less than estMvrs, some of the assertions will fail, meaning that the risk will be above the risk limit (in this example 5%).
You can see this in the _estRisk_ field of the assertion table.

The game is to try to minimize the number of mvrs and minimize the number of failing assertions.

The status button in the upper right displays the total mvrs and the number of failed assertions over all contests.


#### Setting mvrLimit in the samplesLimit.txt file

In the top directory, there may be a samplesLimit.txt file with content like:

````
contest name, contest id, mvrLimit
Anvers, 1, 1884
Bruxelles, 2, 1760
FlandreEast, 4, 800
Hainut, 5, 550
Liege, 6, 640
````

If a contest is not listed, then mvrLimit is not set.
This file is read in and used if present. You may edit this file by hand, and then use the **reread sample limits** button
in the upper right to use it. After reading it in, you  may still modify the mvrLimits by hand. These changes are not
written back to the samplesLimit file.

### Choose candidate coalitions

In the Party Coalition table, each party is shown with the reported number of seats won over all contests, along with 
the number of failed assertions that affect that party (if all contests draw _estMvrs_ samples there will be no failures). 

When there are failures, the _minSeats_ / _maxSeats_ columns show the minimum/maximum number of seats the party won if 
all _disputed assertions_ are flipped against it / in favor of it.

You can choose party coalitions by clicking on the _include_ column of the party. The number of min/reported/max seats is 
shown for that coalition of parties. This is not the sum over parties of those fields. If a disputed assertion has both its
winner and loser in the coalition, than the number of seats for the coalition is not affected by the dispute.


## Special Features for Corla Audits

