package fr.networkguard.audit;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ThreatModelTest {
    @Test public void neverClaimAttackFromSuspectedAccess(){
        assertTrue(ThreatModel.status(false,true).contains("AUCUNE INTRUSION PROUVÉE"));
        assertTrue(ThreatModel.status(false,false).startsWith("NON VÉRIFIÉ"));
        assertTrue(ThreatModel.status(true,false).startsWith("FAIT TECHNIQUE"));
    }
    @Test public void localAuditIsNotSpywareVerdict(){
        String s=ThreatModel.phoneSummary(2,1,true);
        assertTrue(s.contains("ne prouvent PAS"));
        assertTrue(s.contains("2 visible(s)"));
        assertTrue(s.contains("1 visible(s)"));
    }
    @Test public void strangersCannotBeAssignedToAnArbitraryCamera(){
        assertTrue(ThreatModel.profileDescription(0).contains("ne peut pas les relier"));
    }
}
