package org.cryptobiotic.rlauxe.bridge;

public class Naming {
    // enum class TestH0Status(val rank: Int, val complete: Boolean, val success: Boolean) {
    //    InProgress(0,false, false),
    //
    //    // contest status
    //    ContestMisformed(1,true, false), // Contest incorrectly formed
    //    MinMargin(2,true, false), // margin too small for RLA to efficiently work
    //    TooManyPhantoms(3,true, false), // too many phantoms, makes margin < 0
    //    FailMaxSamplesAllowed(4,true, false),  // estimated samples greater than maximum samples allowed
    //    AuditorRemoved(5,true, false),  // auditor decide to remove it
    //
    //    // possible returns from RiskTestingFn
    //    LimitReached(10,false, false),  // cant tell from the number of samples available
    //    StatRejectNull(11,true, true), // statistical rejection of H0
    //    //// only when sampling without replacement all the way close to Nc
    //    SampleSumRejectNull(12,true, true), // SampleSum > Nc / 2, so we know H0 is false
    //    AcceptNull(13,true, false), // SampleSum + (all remaining ballots == 1) < Nc / 2, so we know that H0 is true.
    //}
    static public String status(org.cryptobiotic.rlauxe.betting.TestH0Status status) {
        switch (status) {
            case StatRejectNull:
                return "Success";
            case SampleSumRejectNull:
                return "SuccessFullCount";
            case AcceptNull:
                return "FailedFullCount";

        }
        return status.toString();
    }
}
