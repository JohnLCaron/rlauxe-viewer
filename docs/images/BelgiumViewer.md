# Belgium (D'Hondt) Viewer
_06/29/2026_

To bring up the specialized Belgium Audit viewer, add -belgiumAudit to the command line:

`java -jar viewer/build/libs/rlauxe-viewer-uber.jar -belgiumAudit`

Use the Audit Record chooser to navigate to the top of the Belgium Audit Record, and select it.

![image](images/belgium/BelgiumViewer.png)

The first table shows all the contests, ie the electoral districts. Choosing a contest shows its assertions in the second table.
The total seats for each party (aka candidate) across all contests are shown in the third table.

### Limiting the number mvrs sampled

In the assertion table, the _estMvrs_ column shows how many mvrs will be needed to satisfy the risk limit for that assertion,
when no discrepancies between the mvrs and the cvrs are found.

The same field in the contest table shows the maximum needed over all assertions for that contest. The contest _mvrLimit_ allows you
to limit the number of mvrs for that electoral district. Click in the cell, and enter the mvr limit for that contest.
The value of -1 means no limit, so the audit will use the value of estMvrs.

If mvrLimit is less than estMvrs, some of the assertions will fail, meaning that the risk will be above the risk limit (in this example 5%).
You can see this in the _estRisk_ field of the assertion table.

The game is to try to minimize the number of mvrs and minimize the number of failing assertions.

The status button in the upper right displays the total mvrs and the number of failed assertions over all contests.


#### Setting mvrLimit in the samplesLimit.txt file

In the top directory, there may be a samplesLimit.txt file:

![image](images/belgium/SampleLimits.png)

In this example, that file has the following content:


````
contest name, contest id, mvrLimit
Anvers, 1, 1884
Bruxelles, 2, 1760
FlandreEast, 4, 800
Hainut, 5, 550
Liege, 6, 640
````

And you can see that these values are used as the defaults for _mvrLimit_ in the Contests table of the BelgianViewr.
If a contest is not listed, then mvrLimit is not set.

This file is read in and used if present upon startup. You may edit this file by hand, and then use the **reread sample limits** button
in the upper right to reread it. After reading it in, you may modify the mvrLimits by hand. These changes are not
written back to the samplesLimit file.

### Choose Party coalitions

In the Party Coalition table, each party is shown with the reported number of seats won over all contests, along with
the number of failed assertions that affect that party (if all contests draw _estMvrs_ samples there will be no failures).

When there are failures, the _minSeats_ / _maxSeats_ columns show the minimum/maximum number of seats the party won if
all _disputed assertions_ are flipped against it / in favor of it.

You can choose party coalitions by clicking on the _include_ column of the party. The number of min/reported/max seats is
shown for that coalition of parties. This is not the sum over parties of those fields, because if a disputed assertion has both its
winner and loser in the coalition, than the number of seats for the coalition is not affected by the dispute.

### Party min/max seat algorithm

The algorithm for computing the _minSeats_ / _maxSeats_ for a party and a coalition is still being investigated. See [D'Hondt coalition min/max calculation](https://github.com/JohnLCaron/rlauxe/blob/main/docs/Dhondt.md) for the current algorithm used in rlauxe. The following examples may
vary in some details from the latest versions.

### Show Contest with disputed assertions

Select a Contest in the top table, right click and choose **Show Contest**. In addition to the first two sections shown for Contests of all types,
in section three DHondt contests show the scoring rounds and winning parties, as well as the parties who didnt clear the threshold (with an asterisk).
In section four the reported winners (plus first 3 losers) are shown sorted by their score = total votes / round
(the scoreDiff column is the difference of each row's score with the row above):

![image](images/belgium/ShowDHondtContest.png)

When there are disputed (aka failed assertions) there is a fifth section showing all disputed assertions for that contest:

![image](images/belgium/ShowDisputedContest.png)

This will be explained below.

### Show disputed Assertions

In this example we have selected FlandreEast, and its assertions are shown in the Assertions table, which we reverse sort on the
_estMvrs_ field (estimated number of mvrs needed):

![image](images/belgium/DisputedAssertions.png)

The corresponding _estRisk_ (estimated risk) field shows that the first assertion is estimated to miss the 5% risk limit.
(We chose the mvrLimit to satisfy as many assertions as possible, but this assertion was deemed too costly).

Select that assertion, right click and choose **Show Assertion**. The detail window shows:

![image](images/belgium/ShowDisputedAssertion.png)

The selected assertion is "winner=Vooruit-3 loser=CD&V-3". Since this is disputed, the alternate (or flipped) assertion is "winner=CD&V-3 loser=Vooruit-3".

The section "Alternate Contest from FlandreEast" shows what the scoring looks like using the flipped assertion. Compare this to the reported scoring in the "Show Contest" for FlandreEast, where Vooruit-3  wins the last seat and CDV-3 is the first loser. Now CDV-3 wins the last seat and Vooruit-3 is the first loser.

The alternate contest generates all the assertions needed under this alternate scoring. We show only the ones that will fail (because of mvrLimit), namely
the flipped assertion (winner=CD&V-3 loser=Vooruit-3), and also the "winner=CD&V-3 loser=open vld-3" assertion. This second assertion is
not present in the original contests since both CD&V-3 and open vld-3 are losers.

Not shown in the output, but we compute an alternate contest for this second new assertion, and see if it has failures that we dont already have in the contest.
A new failure will keep recursing if it generates a different new failure. If it does generate a new failure, we show the alternate contest that generated it.
(I think we dont have any examples of that?).

In this case, no other failures are generated, and these two assertions are the only ones shown in FlandreEast "Show Contest".

When there are multiple failed assertions, say for Anvers which has 3 failed assertions, then for each failed assertion we compute the Alternate Contest
and all the failed assertions it creates. We keep track of failed assertions for the entire contest; we stop recursing if we alredy have seen a particular failed assertion. Each of the 3 failed assertions for Anvers generates the same list of 4 failed assertions. So Anvers has 4 failed assertions altogether.

(TODO: we have not tried combinations of failed assertions to see if new failures are found).

Hainut is special in that its the only one in this example that has a Threshold assertion failure. However, the algorithm is the same: create an alternate contest
and find the failed assertions. Recurse on those to see if new failures are found.

(TODO: also not tested are combinations of multiple failed threshold assertions (in the same contest) to see if new failures are found).
