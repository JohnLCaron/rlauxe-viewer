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

## Starting Rlauxe-viewer

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

After the application starts, use the Directory Chooser button
![image](uibase/src/main/resources/resources/ui/icons/Open-File-Folder-icon.png)
to bring up the Directory Chooser,
then navigate to the directory where the Audit Record is stored (note that for the test cases, its always a subdirectory
named "audit" of the test case name).

![image](docs/images/DirectoryChooser.png)

The **Audit** tab will be populated from the chosen audit record:

![image](docs/images/AuditPanel.png)

Select the Contest in the top table, right click and choose **Show Contest**. The Detail window shows more information about that Contest.
For Dhondt, it summarizes the scoring rounds and winning parties, as well as the parties who didnt clear the threshold (with an asterisk):

![image](docs/images/ShowContest.png)

### Setting UI choices

The first time you start up, you can change the window size, the table sizes, the order and size of the table headings, etc.
When you exit, your choices will be saved  (in ~/.rlauxe/RlauxeViewer.xml) for the next time you start the application.


## Showing the results of an audit

If the audit has already run, clicking on the **AuditRounds** tab will show you the results by round:

![image](docs/images/AuditRounds.png)

In this case, the audit completed successfully in a single round. The **Assertion** table shows all the assertions needed by the
audit. By clicking on the **noError** header, the assertions are sorted by the assertion's noerror field.

The **EstimationRounds** table shows the estimated distribution of samples needed for the selected assertion. 
If one looks at the AuditConfig fields by clicking on the info button 
![image](uibase/src/main/resources/resources/ui/icons/Info-icon.png):

![image](docs/images/AuditConfig.png)

We see that simFuzzPct = .001, meaning that in 1 in 1000 ballots, the candidate voted for was randomly changed, to simulate errors in the CVRs.
Also, nsimEst=10, so 10 simulation were done to create the distribution.
The distribution is given in deciles, so that 10*(idx+1) percent of the distribution is less than decile[idx] (idx is zero based).

The **Audit Results** table shows what happened in the "real" audit for the selected assertion. 
In this example, our estimate was 667 samples, but only 556 were needed.

If you right click on the Audit Result line, and choose **Show Audit Details**, the detail window reruns the audit and shows the
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

In this example, the votes always match, so the assort value always equals noerror. mj is slow varying, so tj is approximately constant, and

````
1/risk = Prod (tj) 
1/.05 = tj ^ nsamples
ln(20) = nsamples*ln(1.0054)
nsamples = ln(20)/ln(1.0054)
nsamples = 556
````

